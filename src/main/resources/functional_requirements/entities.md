# Entity Requirements

## Overview

This document defines the detailed requirements for all entities in the Cyoda OMS Backend system. Each entity represents a core business object that will be persisted in Cyoda and managed through workflows.

**Important Note on Entity State:**
- Entity state is an internal attribute managed automatically by the workflow system
- Use `entity.meta.state` to access the current state in processor code
- Do NOT include state/status fields in the entity schema - they are managed by the system
- If user requirements mention status/state fields, they should be mapped to `entity.meta.state`

## Entities

### 1. Product

**Purpose:** Represents a product in the catalog with complete product information including variants, inventory, and compliance data.

**Business ID:** `sku` (unique identifier for business operations)

**Attributes:**
- `sku` (string, required, unique) - Stock Keeping Unit, primary business identifier
- `name` (string, required) - Product display name
- `description` (string, required) - Product description
- `price` (number, required) - Base price in system currency
- `quantityAvailable` (number, required) - Available quantity for immediate sale
- `category` (string, required) - Product category for filtering and organization
- `warehouseId` (string, optional) - Default primary warehouse node ID

**Complex Attributes (from attached schema):**
- `attributes` (object) - Physical and descriptive attributes including brand, model, dimensions, weight, hazards, custom fields
- `localizations` (object) - Multi-language content with regulatory information per locale
- `media` (array) - Images, documents, and other media assets with metadata
- `options` (object) - Product option axes (color, size, etc.) with constraints
- `variants` (array) - Product variants with specific option combinations and overrides
- `bundles` (array) - Kit and bundle definitions with components and substitutions
- `inventory` (object) - Detailed inventory information across multiple nodes with lots, reservations, and policies
- `compliance` (object) - Regulatory documents and regional restrictions
- `relationships` (object) - Supplier contracts and related product references
- `events` (array) - Audit trail of product lifecycle events

**Relationships:**
- Referenced by Cart.lines (via sku)
- Referenced by Order.lines (via sku)
- Referenced by Shipment.lines (via sku)
- Self-referential through relationships.relatedProducts

**State Management:**
- Product entities do not have workflow states in this system
- Inventory and availability are managed through the quantityAvailable field and inventory object

### 2. Cart

**Purpose:** Represents a shopping cart for anonymous users during the shopping session.

**Business ID:** `cartId` (unique identifier for cart operations)

**Attributes:**
- `cartId` (string, required, unique) - Cart identifier
- `lines` (array, required) - Cart line items with structure: `{ sku, name, price, qty }`
- `totalItems` (number, required) - Total quantity of items in cart
- `grandTotal` (number, required) - Total cart value including all calculations
- `guestContact` (object, optional) - Guest contact information for checkout
  - `name` (string, optional) - Guest name
  - `email` (string, optional) - Guest email address
  - `phone` (string, optional) - Guest phone number
  - `address` (object, optional) - Guest shipping address
    - `line1` (string, optional) - Address line 1
    - `city` (string, optional) - City
    - `postcode` (string, optional) - Postal code
    - `country` (string, optional) - Country
- `createdAt` (datetime, auto-generated) - Cart creation timestamp
- `updatedAt` (datetime, auto-generated) - Last update timestamp

**Relationships:**
- References Product entities through lines[].sku
- Referenced by Payment.cartId
- Referenced by Order creation process

**State Management:**
- Uses entity.meta.state with workflow states: NEW → ACTIVE → CHECKING_OUT → CONVERTED
- State transitions managed by CartFlow workflow

### 3. Payment

**Purpose:** Represents a dummy payment transaction that auto-approves after 3 seconds.

**Business ID:** `paymentId` (unique identifier for payment operations)

**Attributes:**
- `paymentId` (string, required, unique) - Payment transaction identifier
- `cartId` (string, required) - Reference to associated cart
- `amount` (number, required) - Payment amount
- `provider` (string, required) - Always "DUMMY" for this system
- `createdAt` (datetime, auto-generated) - Payment creation timestamp
- `updatedAt` (datetime, auto-generated) - Last update timestamp

**Relationships:**
- References Cart via cartId
- Referenced by Order creation process

**State Management:**
- Uses entity.meta.state with workflow states: INITIATED → PAID | FAILED | CANCELED
- State transitions managed by PaymentFlow workflow
- Auto-transition to PAID after ~3 seconds via processor

### 4. Order

**Purpose:** Represents a confirmed order created from a paid cart with customer details and fulfillment information.

**Business ID:** `orderId` (unique identifier for order operations)

**Attributes:**
- `orderId` (string, required, unique) - Internal order identifier
- `orderNumber` (string, required) - Short ULID for customer-facing order number
- `lines` (array, required) - Order line items with structure: `{ sku, name, unitPrice, qty, lineTotal }`
- `totals` (object, required) - Order totals with structure: `{ items, grand }`
- `guestContact` (object, required) - Customer contact information
  - `name` (string, required) - Customer name
  - `email` (string, optional) - Customer email address
  - `phone` (string, optional) - Customer phone number
  - `address` (object, required) - Shipping address
    - `line1` (string, required) - Address line 1
    - `city` (string, required) - City
    - `postcode` (string, required) - Postal code
    - `country` (string, required) - Country
- `createdAt` (datetime, auto-generated) - Order creation timestamp
- `updatedAt` (datetime, auto-generated) - Last update timestamp

**Relationships:**
- Created from Cart and Payment entities
- Has one Shipment entity
- References Product entities through lines[].sku

**State Management:**
- Uses entity.meta.state with workflow states: WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED
- State transitions managed by OrderLifecycle workflow
- State derived from associated Shipment state

### 5. Shipment

**Purpose:** Represents the physical shipment of an order (single shipment per order for demo).

**Business ID:** `shipmentId` (unique identifier for shipment operations)

**Attributes:**
- `shipmentId` (string, required, unique) - Shipment identifier
- `orderId` (string, required) - Reference to associated order
- `lines` (array, required) - Shipment line items with structure: `{ sku, qtyOrdered, qtyPicked, qtyShipped }`
- `createdAt` (datetime, auto-generated) - Shipment creation timestamp
- `updatedAt` (datetime, auto-generated) - Last update timestamp

**Relationships:**
- Belongs to one Order via orderId
- References Product entities through lines[].sku

**State Management:**
- Uses entity.meta.state with workflow states: PICKING → WAITING_TO_SEND → SENT → DELIVERED
- State transitions managed by ShipmentFlow workflow
- State changes trigger Order state updates

## Entity Relationships Summary

```
Product (1) ←→ (N) Cart.lines
Product (1) ←→ (N) Order.lines  
Product (1) ←→ (N) Shipment.lines
Cart (1) ←→ (1) Payment
Cart (1) → (1) Order (via creation process)
Order (1) ←→ (1) Shipment
```

## Data Validation Rules

1. **Product.sku** must be unique across all products
2. **Cart.lines[].sku** must reference existing Product.sku
3. **Payment.cartId** must reference existing Cart.cartId
4. **Order.lines** must be snapshot of Cart.lines at order creation
5. **Shipment.orderId** must reference existing Order.orderId
6. **Shipment.lines[].sku** must match Order.lines[].sku
7. **Product.quantityAvailable** must be decremented when Order is created
8. **Order.orderNumber** must be unique short ULID
9. **Guest contact address** is optional in Cart but required in Order

## Business Rules

1. Anonymous checkout only - no user accounts
2. Stock is decremented immediately on order creation (no reservations)
3. Single shipment per order
4. Payment auto-approves after ~3 seconds
5. Order number uses short ULID format
6. Product schema must be persisted verbatim for round-trip compatibility
7. Cart totals must be recalculated on every line change
8. Order creation requires PAID payment status
