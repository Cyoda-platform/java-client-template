# Entity Requirements

## Overview

This document defines the detailed requirements for all entities in the Cyoda OMS Backend system. Each entity represents a core business object that will be persisted in Cyoda and managed through workflows.

**Important Note on Entity State**: 
- Entity state is an internal attribute managed automatically by the workflow system
- Access entity state via `entity.meta.state` in processor code
- Status fields (like Cart.status, Payment.status, Order.status, Shipment.status) are NOT included in entity schemas
- The system automatically manages state transitions based on workflow definitions

## Entities

### 1. Product

**Business ID**: `sku` (unique identifier)

**Description**: Represents a product in the catalog with complete schema for persistence and round-trip operations.

**Attributes**:
- `sku` (string, required, unique): Product SKU identifier
- `name` (string, required): Product name
- `description` (string, required): Product description
- `price` (number, required): Default/base price (fallback)
- `quantityAvailable` (number, required): Quick projection field for available stock
- `category` (string, required): Product category for filtering
- `warehouseId` (string, optional): Default primary warehouse node

**Complex Attributes** (as per attached schema):
- `attributes`: Brand, model, dimensions, weight, hazards, custom fields
- `localizations`: Multi-locale content with regulatory information
- `media`: Images, documents with metadata
- `options`: Product option axes and constraints
- `variants`: Product variants with option values and overrides
- `bundles`: Kit and bundle configurations
- `inventory`: Warehouse nodes, lots, reservations, in-transit items
- `compliance`: Documentation and regional restrictions
- `relationships`: Suppliers and related products
- `events`: Product lifecycle events

**Relationships**:
- Referenced by Cart.lines (via sku)
- Referenced by Order.lines (via sku)
- Referenced by Shipment.lines (via sku)

**State Management**: Product entities do not have workflow states (stateless entity)

---

### 2. Cart

**Business ID**: `cartId` (unique identifier)

**Description**: Represents a shopping cart for anonymous checkout process.

**Attributes**:
- `cartId` (string, required, unique): Cart identifier
- `lines` (array, required): Cart line items
  - `sku` (string): Product SKU
  - `name` (string): Product name (snapshot)
  - `price` (number): Product price (snapshot)
  - `qty` (number): Quantity
- `totalItems` (number, required): Total number of items in cart
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
- `createdAt` (timestamp): Creation timestamp
- `updatedAt` (timestamp): Last update timestamp

**Relationships**:
- Referenced by Payment.cartId
- Referenced by Order (via snapshot during order creation)

**State Management**: Managed via CartFlow workflow
- States: NEW → ACTIVE → CHECKING_OUT → CONVERTED
- Access current state via `entity.meta.state`

---

### 3. Payment

**Business ID**: `paymentId` (unique identifier)

**Description**: Represents a dummy payment transaction for demo purposes.

**Attributes**:
- `paymentId` (string, required, unique): Payment identifier
- `cartId` (string, required): Associated cart ID
- `amount` (number, required): Payment amount
- `provider` (string, required): Payment provider (always "DUMMY")
- `createdAt` (timestamp): Creation timestamp
- `updatedAt` (timestamp): Last update timestamp

**Relationships**:
- References Cart via cartId
- Referenced by Order creation process

**State Management**: Managed via PaymentFlow workflow
- States: INITIATED → PAID | FAILED | CANCELED
- Access current state via `entity.meta.state`

---

### 4. Order

**Business ID**: `orderId` (unique identifier)

**Description**: Represents a customer order created from a paid cart.

**Attributes**:
- `orderId` (string, required, unique): Order identifier
- `orderNumber` (string, required): Short ULID for customer reference
- `lines` (array, required): Order line items (snapshot from cart)
  - `sku` (string): Product SKU
  - `name` (string): Product name
  - `unitPrice` (number): Unit price at time of order
  - `qty` (number): Quantity ordered
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
- `createdAt` (timestamp): Creation timestamp
- `updatedAt` (timestamp): Last update timestamp

**Relationships**:
- Created from Cart and Payment
- Has one Shipment (single shipment per order)

**State Management**: Managed via OrderLifecycle workflow
- States: WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED
- Access current state via `entity.meta.state`

---

### 5. Shipment

**Business ID**: `shipmentId` (unique identifier)

**Description**: Represents a single shipment for an order (demo uses single shipment per order).

**Attributes**:
- `shipmentId` (string, required, unique): Shipment identifier
- `orderId` (string, required): Associated order ID
- `lines` (array, required): Shipment line items
  - `sku` (string): Product SKU
  - `qtyOrdered` (number): Quantity originally ordered
  - `qtyPicked` (number): Quantity picked for shipment
  - `qtyShipped` (number): Quantity actually shipped
- `createdAt` (timestamp): Creation timestamp
- `updatedAt` (timestamp): Last update timestamp

**Relationships**:
- References Order via orderId
- Created automatically when Order is created

**State Management**: Managed via ShipmentFlow workflow
- States: PICKING → WAITING_TO_SEND → SENT → DELIVERED
- Access current state via `entity.meta.state`
- Shipment state changes drive Order state changes

## Entity Relationships Summary

```
Product (stateless)
    ↑ referenced by
Cart (NEW → ACTIVE → CHECKING_OUT → CONVERTED)
    ↓ creates
Payment (INITIATED → PAID | FAILED | CANCELED)
    ↓ creates (when PAID)
Order (WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED)
    ↓ creates
Shipment (PICKING → WAITING_TO_SEND → SENT → DELIVERED)
```

## Business Rules

1. **Product Stock**: `quantityAvailable` is decremented when Order is created (no reservations)
2. **Cart Lifecycle**: First item addition creates cart and moves to ACTIVE state
3. **Payment**: Dummy payment auto-approves after ~3 seconds
4. **Order Creation**: Only possible when Payment is in PAID state
5. **Shipment**: Single shipment created per order, shipment state drives order state
6. **Anonymous Checkout**: No user accounts, only guest contact information
