# Entity Requirements

## Overview

This document defines the detailed requirements for all entities in the Cyoda OMS Backend system. Each entity represents a core business object that will be persisted in Cyoda and managed through workflows.

**Important Note on Entity State**: 
- Entity state is managed internally by Cyoda workflows and accessed via `entity.meta.state`
- Status/state fields should NOT be included in the entity schema
- The system automatically manages state transitions based on workflow definitions

## Entities

### 1. Product

**Purpose**: Represents catalog items available for purchase with comprehensive product information.

**Entity Name**: Product

**Attributes**:
- `sku` (string, required, unique): Stock Keeping Unit identifier
- `name` (string, required): Product display name
- `description` (string, required): Product description
- `price` (number, required): Base price in system currency
- `quantityAvailable` (number, required): Available inventory count
- `category` (string, required): Product category for filtering
- `warehouseId` (string, optional): Primary warehouse identifier
- `attributes` (object, optional): Extended product attributes including:
  - `brand` (string): Product brand
  - `model` (string): Product model
  - `dimensions` (object): Physical dimensions with length, width, height, unit
  - `weight` (object): Weight value and unit
  - `hazards` (array): Hazardous material classifications
  - `custom` (object): Open extension for custom attributes
- `localizations` (object, optional): Multi-language content and regulatory info
- `media` (array, optional): Product images, documents, and media assets
- `options` (object, optional): Product option axes and constraints
- `variants` (array, optional): Product variants with option values and overrides
- `bundles` (array, optional): Kit and bundle configurations
- `inventory` (object, optional): Detailed inventory management data
- `compliance` (object, optional): Regulatory compliance documentation
- `relationships` (object, optional): Supplier and related product information
- `events` (array, optional): Product lifecycle events
- `createdAt` (timestamp): Entity creation timestamp
- `updatedAt` (timestamp): Last modification timestamp

**Relationships**:
- Referenced by Cart lines via `sku`
- Referenced by Order lines via `sku`
- Referenced by Shipment lines via `sku`

**State Management**: Product entities do not use workflow states in this implementation.

### 2. Cart

**Purpose**: Represents a shopping cart for anonymous users during the shopping session.

**Entity Name**: Cart

**Attributes**:
- `cartId` (string, required, unique): Cart identifier
- `lines` (array, required): Cart line items, each containing:
  - `sku` (string): Product SKU
  - `name` (string): Product name (snapshot)
  - `price` (number): Product price (snapshot)
  - `qty` (number): Quantity in cart
- `totalItems` (number, required): Total quantity of all items
- `grandTotal` (number, required): Total cart value
- `guestContact` (object, optional): Guest contact information:
  - `name` (string, optional): Guest name
  - `email` (string, optional): Guest email
  - `phone` (string, optional): Guest phone
  - `address` (object, optional): Guest address:
    - `line1` (string, optional): Address line 1
    - `city` (string, optional): City
    - `postcode` (string, optional): Postal code
    - `country` (string, optional): Country
- `createdAt` (timestamp): Cart creation timestamp
- `updatedAt` (timestamp): Last modification timestamp

**Relationships**:
- References Product entities via line item `sku`
- Referenced by Payment via `cartId`
- Referenced by Order creation process

**State Management**: Uses workflow states via `entity.meta.state`:
- `NEW`: Initial state when cart is created
- `ACTIVE`: Cart has items and can be modified
- `CHECKING_OUT`: Cart is in checkout process
- `CONVERTED`: Cart has been converted to an order

### 3. Payment

**Purpose**: Represents payment processing for cart checkout (dummy implementation).

**Entity Name**: Payment

**Attributes**:
- `paymentId` (string, required, unique): Payment identifier
- `cartId` (string, required): Associated cart identifier
- `amount` (number, required): Payment amount
- `provider` (string, required): Payment provider (always "DUMMY")
- `createdAt` (timestamp): Payment creation timestamp
- `updatedAt` (timestamp): Last modification timestamp

**Relationships**:
- References Cart via `cartId`
- Referenced by Order creation process

**State Management**: Uses workflow states via `entity.meta.state`:
- `INITIATED`: Payment has been started
- `PAID`: Payment completed successfully (auto after ~3s)
- `FAILED`: Payment failed
- `CANCELED`: Payment was canceled

### 4. Order

**Purpose**: Represents a confirmed order after successful payment.

**Entity Name**: Order

**Attributes**:
- `orderId` (string, required, unique): Order identifier
- `orderNumber` (string, required): Short ULID for customer reference
- `lines` (array, required): Order line items, each containing:
  - `sku` (string): Product SKU
  - `name` (string): Product name (snapshot)
  - `unitPrice` (number): Unit price at time of order
  - `qty` (number): Quantity ordered
  - `lineTotal` (number): Line total (unitPrice * qty)
- `totals` (object, required): Order totals:
  - `items` (number): Total items count
  - `grand` (number): Grand total amount
- `guestContact` (object, required): Customer contact information:
  - `name` (string, required): Customer name
  - `email` (string, optional): Customer email
  - `phone` (string, optional): Customer phone
  - `address` (object, required): Shipping address:
    - `line1` (string, required): Address line 1
    - `city` (string, required): City
    - `postcode` (string, required): Postal code
    - `country` (string, required): Country
- `createdAt` (timestamp): Order creation timestamp
- `updatedAt` (timestamp): Last modification timestamp

**Relationships**:
- Created from Cart and Payment entities
- Has one Shipment entity
- References Product entities via line item `sku`

**State Management**: Uses workflow states via `entity.meta.state`:
- `WAITING_TO_FULFILL`: Order created, waiting to start fulfillment
- `PICKING`: Order is being picked in warehouse
- `WAITING_TO_SEND`: Order picked, waiting to ship
- `SENT`: Order has been shipped
- `DELIVERED`: Order has been delivered

### 5. Shipment

**Purpose**: Represents the physical shipment of an order (single shipment per order).

**Entity Name**: Shipment

**Attributes**:
- `shipmentId` (string, required, unique): Shipment identifier
- `orderId` (string, required): Associated order identifier
- `lines` (array, required): Shipment line items, each containing:
  - `sku` (string): Product SKU
  - `qtyOrdered` (number): Quantity originally ordered
  - `qtyPicked` (number): Quantity picked for shipment
  - `qtyShipped` (number): Quantity actually shipped
- `createdAt` (timestamp): Shipment creation timestamp
- `updatedAt` (timestamp): Last modification timestamp

**Relationships**:
- References Order via `orderId`
- References Product entities via line item `sku`

**State Management**: Uses workflow states via `entity.meta.state`:
- `PICKING`: Items are being picked for shipment
- `WAITING_TO_SEND`: Items picked, waiting to ship
- `SENT`: Shipment has been sent
- `DELIVERED`: Shipment has been delivered

## Entity Relationships Summary

```
Product (1) ←→ (N) Cart.lines
Product (1) ←→ (N) Order.lines  
Product (1) ←→ (N) Shipment.lines
Cart (1) ←→ (1) Payment
Cart (1) → (1) Order (conversion)
Order (1) ←→ (1) Shipment
```

## Data Validation Rules

1. **Product**: SKU must be unique across all products
2. **Cart**: Lines must reference valid Product SKUs
3. **Payment**: Must reference valid Cart ID
4. **Order**: Must be created from valid Cart and Payment
5. **Shipment**: Must reference valid Order ID
6. **Quantities**: All quantity fields must be non-negative
7. **Prices**: All price fields must be positive
8. **Required Fields**: All required fields must be present and non-null
