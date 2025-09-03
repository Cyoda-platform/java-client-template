# Entity Requirements

## Overview
This document defines the entities for the Cyoda OMS Backend system. Each entity represents a business object that will be managed through Cyoda workflows.

**Important Note on Entity State:**
- Entity state is an internal attribute managed automatically by the workflow system
- State is accessed via `entity.meta.state` in processor code but cannot be directly modified
- Fields like "status" in the requirements are treated as the entity state and should NOT be included in the entity schema
- The system will manage state transitions automatically based on workflow definitions

## Entities

### 1. Product
**Description:** Represents a product in the catalog with full schema compliance for persistence and round-trip operations.

**Attributes:**
- `sku` (string, required, unique): Product SKU identifier
- `name` (string, required): Product name
- `description` (string, required): Product description
- `price` (number, required): Base price
- `quantityAvailable` (number, required): Available quantity for sale
- `category` (string, required): Product category
- `warehouseId` (string, optional): Primary warehouse identifier
- `attributes` (object, optional): Product attributes including brand, model, dimensions, weight, hazards, custom fields
- `localizations` (object, optional): Multi-language content and regulatory information
- `media` (array, optional): Product images, documents, and media files
- `options` (object, optional): Product option axes, values, and constraints
- `variants` (array, optional): Product variants with option values and overrides
- `bundles` (array, optional): Product bundles and kits
- `inventory` (object, optional): Inventory nodes, lots, reservations, and policies
- `compliance` (object, optional): Compliance documents and restrictions
- `relationships` (object, optional): Supplier and related product information
- `events` (array, optional): Product lifecycle events

**State Management:**
- Product entities do not have explicit workflow states in this implementation
- Products are managed through direct CRUD operations

**Relationships:**
- Referenced by Cart lines (via SKU)
- Referenced by Order lines (via SKU)
- Referenced by Shipment lines (via SKU)

### 2. Cart
**Description:** Represents a shopping cart for anonymous checkout process.

**Attributes:**
- `cartId` (string, required, unique): Cart identifier
- `lines` (array, required): Cart line items with SKU, name, price, quantity
- `totalItems` (number, required): Total number of items in cart
- `grandTotal` (number, required): Total cart value
- `guestContact` (object, optional): Guest contact information including name, email, phone, address
- `createdAt` (timestamp, auto-generated): Cart creation timestamp
- `updatedAt` (timestamp, auto-generated): Last update timestamp

**State Management:**
- Cart state is managed by CartFlow workflow
- States: NEW → ACTIVE → CHECKING_OUT → CONVERTED
- State field is NOT included in schema (managed by entity.meta.state)

**Relationships:**
- Contains multiple Product references (via SKU in lines)
- Referenced by Payment (via cartId)
- Referenced by Order (snapshot relationship)

### 3. Payment
**Description:** Represents a dummy payment transaction that auto-approves after 3 seconds.

**Attributes:**
- `paymentId` (string, required, unique): Payment identifier
- `cartId` (string, required): Associated cart identifier
- `amount` (number, required): Payment amount
- `provider` (string, required): Payment provider (always "DUMMY")
- `createdAt` (timestamp, auto-generated): Payment creation timestamp
- `updatedAt` (timestamp, auto-generated): Last update timestamp

**State Management:**
- Payment state is managed by PaymentFlow workflow
- States: INITIATED → PAID | FAILED | CANCELED
- State field is NOT included in schema (managed by entity.meta.state)

**Relationships:**
- References Cart (via cartId)
- Referenced by Order creation process

### 4. Order
**Description:** Represents a customer order created from a paid cart.

**Attributes:**
- `orderId` (string, required, unique): Order identifier
- `orderNumber` (string, required): Short ULID for customer reference
- `lines` (array, required): Order line items with SKU, name, unit price, quantity, line total
- `totals` (object, required): Order totals including items and grand total
- `guestContact` (object, required): Guest contact information (snapshot from cart)
- `createdAt` (timestamp, auto-generated): Order creation timestamp
- `updatedAt` (timestamp, auto-generated): Last update timestamp

**State Management:**
- Order state is managed by OrderLifecycle workflow
- States: WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED
- State field is NOT included in schema (managed by entity.meta.state)

**Relationships:**
- Created from Cart (snapshot relationship)
- Created from Payment (reference relationship)
- Has one Shipment (one-to-one relationship)

### 5. Shipment
**Description:** Represents a single shipment for an order (one shipment per order for demo).

**Attributes:**
- `shipmentId` (string, required, unique): Shipment identifier
- `orderId` (string, required): Associated order identifier
- `lines` (array, required): Shipment lines with SKU, quantities ordered, picked, and shipped
- `createdAt` (timestamp, auto-generated): Shipment creation timestamp
- `updatedAt` (timestamp, auto-generated): Last update timestamp

**State Management:**
- Shipment state is managed by OrderLifecycle workflow (shared with Order)
- States: PICKING → WAITING_TO_SEND → SENT → DELIVERED
- State field is NOT included in schema (managed by entity.meta.state)

**Relationships:**
- Belongs to Order (via orderId)
- References Products (via SKU in lines)

## Entity Relationships Summary

```
Product (1) ←→ (N) Cart.lines
Product (1) ←→ (N) Order.lines  
Product (1) ←→ (N) Shipment.lines

Cart (1) ←→ (1) Payment
Cart (1) → (1) Order (snapshot)

Payment (1) → (1) Order

Order (1) ←→ (1) Shipment
```

## Data Validation Rules

### Product
- SKU must be unique across all products
- Price must be positive
- QuantityAvailable must be non-negative
- Category is required for filtering

### Cart
- Lines array cannot be empty for ACTIVE carts
- TotalItems must equal sum of line quantities
- GrandTotal must equal sum of line totals

### Payment
- Amount must be positive
- Provider must be "DUMMY"
- CartId must reference existing cart

### Order
- OrderNumber must be unique short ULID
- Lines must be snapshot from cart
- GuestContact is required (no anonymous orders without contact)

### Shipment
- Must have corresponding Order
- Line quantities must be consistent with order
