# Entity Requirements

## Overview
This document defines the detailed requirements for all entities in the Cyoda OMS Backend system. Each entity represents a core business object that will be persisted in Cyoda and managed through workflows.

**Important Note on Entity State**: 
- Entity state is an internal attribute managed automatically by the workflow system
- State fields (like `status`) should NOT be included in the entity schema
- Use `entity.meta.state` to access the current state in processor code
- The system will manage state transitions automatically based on workflow definitions

## Entities

### 1. Product Entity

**Purpose**: Represents catalog products with full schema for persistence and round-trip compatibility.

**Business ID**: `sku` (string, unique)

**Attributes**:
- `sku` (string, required, unique): Stock Keeping Unit identifier
- `name` (string, required): Product display name
- `description` (string, required): Product description
- `price` (number, required): Base price in system currency
- `quantityAvailable` (number, required): Available stock quantity
- `category` (string, required): Product category for filtering
- `warehouseId` (string, optional): Default warehouse identifier

**Complex Attributes** (as per attached schema):
- `attributes`: Brand, model, dimensions, weight, hazards, custom fields
- `localizations`: Multi-language content and regulatory information
- `media`: Images, documents, and other media assets
- `options`: Product option axes and constraints
- `variants`: Product variants with option values and overrides
- `bundles`: Kit and bundle configurations
- `inventory`: Warehouse nodes, lots, reservations, and policies
- `compliance`: Regulatory documents and restrictions
- `relationships`: Suppliers and related products
- `events`: Product lifecycle events

**Timestamps**:
- `createdAt` (auto-generated)
- `updatedAt` (auto-generated)

**Relationships**:
- Referenced by Cart lines (via sku)
- Referenced by Order lines (via sku)
- Referenced by Shipment lines (via sku)

**State Management**: 
- Product entities do not have explicit workflow states
- Inventory changes are managed through direct updates

### 2. Cart Entity

**Purpose**: Represents shopping cart for anonymous checkout process.

**Business ID**: `cartId` (string, unique)

**Attributes**:
- `cartId` (string, required, unique): Cart identifier
- `lines` (array, required): Cart line items
  - `sku` (string): Product SKU
  - `name` (string): Product name (snapshot)
  - `price` (number): Unit price (snapshot)
  - `qty` (number): Quantity
- `totalItems` (number, required): Total quantity of all items
- `grandTotal` (number, required): Total cart value
- `guestContact` (object, optional): Guest contact information
  - `name` (string, optional): Guest name
  - `email` (string, optional): Guest email
  - `phone` (string, optional): Guest phone
  - `address` (object, optional): Guest address
    - `line1` (string, optional): Address line 1
    - `city` (string, optional): City
    - `postcode` (string, optional): Postal code
    - `country` (string, optional): Country

**Timestamps**:
- `createdAt` (auto-generated)
- `updatedAt` (auto-generated)

**Relationships**:
- Referenced by Payment (via cartId)
- Referenced by Order (via cartId during creation)

**State Management**: 
- Uses `entity.meta.state` with values: NEW, ACTIVE, CHECKING_OUT, CONVERTED
- State transitions managed by CartFlow workflow

### 3. Payment Entity

**Purpose**: Represents dummy payment processing for demo purposes.

**Business ID**: `paymentId` (string, unique)

**Attributes**:
- `paymentId` (string, required, unique): Payment identifier
- `cartId` (string, required): Associated cart identifier
- `amount` (number, required): Payment amount
- `provider` (string, required): Payment provider (always "DUMMY")

**Timestamps**:
- `createdAt` (auto-generated)
- `updatedAt` (auto-generated)

**Relationships**:
- References Cart (via cartId)
- Referenced by Order (via paymentId during creation)

**State Management**: 
- Uses `entity.meta.state` with values: INITIATED, PAID, FAILED, CANCELED
- State transitions managed by PaymentFlow workflow

### 4. Order Entity

**Purpose**: Represents confirmed orders created from paid carts.

**Business ID**: `orderId` (string, unique)

**Attributes**:
- `orderId` (string, required, unique): Order identifier
- `orderNumber` (string, required): Short ULID for customer reference
- `lines` (array, required): Order line items (snapshot from cart)
  - `sku` (string): Product SKU
  - `name` (string): Product name
  - `unitPrice` (number): Unit price at time of order
  - `qty` (number): Ordered quantity
  - `lineTotal` (number): Line total (unitPrice * qty)
- `totals` (object, required): Order totals
  - `items` (number): Total items count
  - `grand` (number): Grand total amount
- `guestContact` (object, required): Guest contact information (snapshot from cart)
  - `name` (string, required): Guest name
  - `email` (string, optional): Guest email
  - `phone` (string, optional): Guest phone
  - `address` (object, required): Guest address
    - `line1` (string, required): Address line 1
    - `city` (string, required): City
    - `postcode` (string, required): Postal code
    - `country` (string, required): Country

**Timestamps**:
- `createdAt` (auto-generated)
- `updatedAt` (auto-generated)

**Relationships**:
- References Products (via line SKUs)
- Referenced by Shipment (via orderId)

**State Management**: 
- Uses `entity.meta.state` with values: WAITING_TO_FULFILL, PICKING, WAITING_TO_SEND, SENT, DELIVERED
- State transitions managed by OrderLifecycle workflow

### 5. Shipment Entity

**Purpose**: Represents shipment for order fulfillment (single shipment per order for demo).

**Business ID**: `shipmentId` (string, unique)

**Attributes**:
- `shipmentId` (string, required, unique): Shipment identifier
- `orderId` (string, required): Associated order identifier
- `lines` (array, required): Shipment line items
  - `sku` (string): Product SKU
  - `qtyOrdered` (number): Originally ordered quantity
  - `qtyPicked` (number): Quantity picked for shipment
  - `qtyShipped` (number): Quantity actually shipped

**Timestamps**:
- `createdAt` (auto-generated)
- `updatedAt` (auto-generated)

**Relationships**:
- References Order (via orderId)
- References Products (via line SKUs)

**State Management**: 
- Uses `entity.meta.state` with values: PICKING, WAITING_TO_SEND, SENT, DELIVERED
- State transitions managed by ShipmentFlow workflow
- Shipment state changes drive Order state updates

## Entity Relationships Summary

```
Product (1) ←→ (N) Cart.lines
Product (1) ←→ (N) Order.lines  
Product (1) ←→ (N) Shipment.lines

Cart (1) ←→ (1) Payment
Cart (1) → (1) Order (during creation)

Order (1) ←→ (1) Shipment
```

## Data Consistency Rules

1. **Product Stock**: Decrement `quantityAvailable` when Order is created
2. **Cart Totals**: Recalculate `totalItems` and `grandTotal` on line changes
3. **Order Snapshot**: Order lines and contact info are snapshots from Cart at creation time
4. **Shipment Lines**: Initialize with ordered quantities, update picked/shipped as needed
5. **State Synchronization**: Order state derives from Shipment state in single-shipment scenario
