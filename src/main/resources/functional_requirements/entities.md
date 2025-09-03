# Entity Requirements

This document defines the detailed requirements for all entities in the Cyoda OMS Backend system.

## Important Notes

- **Entity State Management**: All entities use `entity.meta.state` for workflow state management. Do NOT include `status` or `state` fields in the entity schema as these are managed automatically by the workflow system.
- **Business IDs**: Each entity should have a unique business identifier (sku, cartId, paymentId, orderId, shipmentId) for external references.
- **Timestamps**: All entities include `createdAt` and `updatedAt` timestamps managed by the system.

## Entity Definitions

### 1. Product Entity

**Purpose**: Represents products in the catalog with full schema for persistence and round-trip compatibility.

**Business ID**: `sku` (string, unique)

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
- Referenced by Cart lines (via sku)
- Referenced by Order lines (via sku)
- Referenced by Shipment lines (via sku)

**State Management**: Products do not have workflow states in this system.

### 2. Cart Entity

**Purpose**: Represents shopping carts for anonymous checkout process.

**Business ID**: `cartId` (string, unique)

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

**Relationships**:
- References Products (via line items sku)
- Referenced by Payment (via cartId)
- Referenced by Order (via cartId for creation)

**State Management**: Uses `entity.meta.state` with workflow states: NEW → ACTIVE → CHECKING_OUT → CONVERTED

### 3. Payment Entity

**Purpose**: Represents dummy payment processing for orders.

**Business ID**: `paymentId` (string, unique)

**Attributes**:
- `paymentId` (string, required, unique): Payment identifier
- `cartId` (string, required): Associated cart ID
- `amount` (number, required): Payment amount
- `provider` (string, required): Always "DUMMY" for demo

**Relationships**:
- References Cart (via cartId)
- Referenced by Order creation process

**State Management**: Uses `entity.meta.state` with workflow states: INITIATED → PAID | FAILED | CANCELED

### 4. Order Entity

**Purpose**: Represents customer orders created from paid carts.

**Business ID**: `orderId` (string, unique)

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

**Relationships**:
- Created from Cart and Payment
- Has one Shipment (single shipment per order)
- References Products (via line items sku)

**State Management**: Uses `entity.meta.state` with workflow states: WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED

### 5. Shipment Entity

**Purpose**: Represents shipments for orders (single shipment per order for demo).

**Business ID**: `shipmentId` (string, unique)

**Attributes**:
- `shipmentId` (string, required, unique): Shipment identifier
- `orderId` (string, required): Associated order ID
- `lines` (array, required): Shipment line items
  - `sku` (string): Product SKU
  - `qtyOrdered` (number): Quantity originally ordered
  - `qtyPicked` (number): Quantity picked for shipment
  - `qtyShipped` (number): Quantity actually shipped

**Relationships**:
- References Order (via orderId)
- References Products (via line items sku)

**State Management**: Uses `entity.meta.state` with workflow states: PICKING → WAITING_TO_SEND → SENT → DELIVERED

## Entity Relationships Summary

```
Product (sku) ←── Cart.lines[].sku
                ←── Order.lines[].sku  
                ←── Shipment.lines[].sku

Cart (cartId) ←── Payment.cartId
              ←── Order creation reference

Payment (paymentId) ←── Order creation reference

Order (orderId) ←── Shipment.orderId
```

## Data Validation Rules

1. **Product**: SKU must be unique across all products
2. **Cart**: Lines must reference valid product SKUs
3. **Payment**: Must reference existing cart, amount must match cart grandTotal
4. **Order**: Created only from PAID payments, lines snapshot from cart
5. **Shipment**: Created automatically with order, qtyOrdered matches order quantities

## Business Rules

1. **Stock Management**: Product.quantityAvailable decremented on order creation
2. **Anonymous Checkout**: No user accounts, only guest contact information
3. **Single Shipment**: One shipment per order for demo simplicity
4. **Payment Auto-approval**: Dummy payments auto-approve after ~3 seconds
5. **Order Numbers**: Use short ULID format for customer-friendly references
