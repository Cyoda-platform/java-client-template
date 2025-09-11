# Entity Requirements

## Overview
This document defines the detailed requirements for all entities in the Cyoda OMS Backend system. Each entity represents a domain object that implements the CyodaEntity interface and is managed by Cyoda workflows.

**Important Note on Entity State**: 
- Entity state is an internal attribute managed automatically by the workflow system
- It is NOT part of the entity schema and should NOT be included as a field
- Access entity state via `entity.meta.state` in processor code
- The system automatically manages state transitions based on workflow definitions
- If user requirements mention "status" fields, these are treated as the entity state

## Entity Definitions

### 1. Product Entity

**Entity Name**: Product
**Model Key**: "Product"
**Description**: Represents a product in the catalog with complete schema for persistence and round-trip operations.

**Attributes**:
- `sku` (String, required, unique): Product stock keeping unit identifier
- `name` (String, required): Product display name
- `description` (String, required): Product description
- `price` (Double, required): Default/base price (fallback)
- `quantityAvailable` (Integer, required): Quick projection field for available inventory
- `category` (String, required): Product category for filtering
- `warehouseId` (String, optional): Default primary warehouse node identifier
- `attributes` (Object, optional): Product attributes including brand, model, dimensions, weight, hazards, custom fields
- `localizations` (Object, optional): Multi-language content with locale-specific names, descriptions, and regulatory info
- `media` (Array, optional): Product media files (images, documents) with metadata
- `options` (Object, optional): Product option axes and constraints for variants
- `variants` (Array, optional): Product variants with option values, attributes, barcodes, price overrides
- `bundles` (Array, optional): Product bundles and kits with components
- `inventory` (Object, optional): Detailed inventory information across nodes, lots, reservations
- `compliance` (Object, optional): Compliance documents and regional restrictions
- `relationships` (Object, optional): Supplier relationships and related products
- `events` (Array, optional): Product lifecycle events
- `createdAt` (LocalDateTime, auto-generated): Entity creation timestamp
- `updatedAt` (LocalDateTime, auto-generated): Entity last update timestamp

**Relationships**: 
- Referenced by Cart lines (via sku)
- Referenced by Order lines (via sku)
- Referenced by Shipment lines (via sku)

**Validation Rules**:
- SKU must be unique across all products
- Price must be greater than 0
- QuantityAvailable must be >= 0
- Category must not be empty
- Full schema validation as per attached Product schema

---

### 2. Cart Entity

**Entity Name**: Cart
**Model Key**: "Cart"
**Description**: Represents a shopping cart for anonymous checkout process.

**Attributes**:
- `cartId` (String, required, unique): Cart identifier
- `lines` (Array, required): Cart line items with sku, name, price, qty
- `totalItems` (Integer, required): Total number of items in cart
- `grandTotal` (Double, required): Total cart value
- `guestContact` (Object, optional): Guest contact information including name, email, phone, address
- `createdAt` (LocalDateTime, auto-generated): Entity creation timestamp
- `updatedAt` (LocalDateTime, auto-generated): Entity last update timestamp

**Entity State Values** (managed by workflow):
- `NEW`: Initial state when cart is created
- `ACTIVE`: Cart has items and can be modified
- `CHECKING_OUT`: Cart is in checkout process
- `CONVERTED`: Cart has been converted to order

**Relationships**:
- References Product entities via line item SKUs
- Referenced by Payment entity (via cartId)
- Referenced by Order entity during conversion

**Validation Rules**:
- CartId must be unique
- Lines array must contain valid SKUs that exist in Product catalog
- TotalItems must equal sum of all line quantities
- GrandTotal must equal sum of all line totals
- Guest contact address.line1 is required when guestContact is provided

---

### 3. Payment Entity

**Entity Name**: Payment
**Model Key**: "Payment"
**Description**: Represents a dummy payment transaction for demo purposes.

**Attributes**:
- `paymentId` (String, required, unique): Payment identifier
- `cartId` (String, required): Associated cart identifier
- `amount` (Double, required): Payment amount
- `provider` (String, required): Payment provider (always "DUMMY")
- `createdAt` (LocalDateTime, auto-generated): Entity creation timestamp
- `updatedAt` (LocalDateTime, auto-generated): Entity last update timestamp

**Entity State Values** (managed by workflow):
- `INITIATED`: Payment has been started
- `PAID`: Payment completed successfully
- `FAILED`: Payment failed
- `CANCELED`: Payment was canceled

**Relationships**:
- References Cart entity (via cartId)
- Referenced by Order entity during order creation

**Validation Rules**:
- PaymentId must be unique
- CartId must reference an existing Cart
- Amount must be greater than 0
- Provider must be "DUMMY"

---

### 4. Order Entity

**Entity Name**: Order
**Model Key**: "Order"
**Description**: Represents a customer order created from a paid cart.

**Attributes**:
- `orderId` (String, required, unique): Order identifier
- `orderNumber` (String, required, unique): Short ULID for customer reference
- `lines` (Array, required): Order line items with sku, name, unitPrice, qty, lineTotal
- `totals` (Object, required): Order totals with items and grand total
- `guestContact` (Object, required): Guest contact information (name required, email/phone optional, address.line1 required)
- `createdAt` (LocalDateTime, auto-generated): Entity creation timestamp
- `updatedAt` (LocalDateTime, auto-generated): Entity last update timestamp

**Entity State Values** (managed by workflow):
- `WAITING_TO_FULFILL`: Order created, waiting to start fulfillment
- `PICKING`: Order is being picked in warehouse
- `WAITING_TO_SEND`: Order picked, waiting to ship
- `SENT`: Order has been shipped
- `DELIVERED`: Order has been delivered

**Relationships**:
- Created from Cart entity (snapshot of cart data)
- References Product entities via line item SKUs
- Has one Shipment entity

**Validation Rules**:
- OrderId must be unique
- OrderNumber must be unique short ULID
- Lines must contain valid product SKUs
- Totals.grand must equal sum of all line totals
- GuestContact.name and guestContact.address.line1 are required

---

### 5. Shipment Entity

**Entity Name**: Shipment
**Model Key**: "Shipment"
**Description**: Represents a single shipment for an order (demo uses single shipment per order).

**Attributes**:
- `shipmentId` (String, required, unique): Shipment identifier
- `orderId` (String, required): Associated order identifier
- `lines` (Array, required): Shipment line items with sku, qtyOrdered, qtyPicked, qtyShipped
- `createdAt` (LocalDateTime, auto-generated): Entity creation timestamp
- `updatedAt` (LocalDateTime, auto-generated): Entity last update timestamp

**Entity State Values** (managed by workflow):
- `PICKING`: Items are being picked from warehouse
- `WAITING_TO_SEND`: Items picked, waiting to ship
- `SENT`: Shipment has been sent
- `DELIVERED`: Shipment has been delivered

**Relationships**:
- References Order entity (via orderId)
- References Product entities via line item SKUs

**Validation Rules**:
- ShipmentId must be unique
- OrderId must reference an existing Order
- Lines must contain valid product SKUs
- QtyPicked must be <= qtyOrdered
- QtyShipped must be <= qtyPicked

## Common Patterns

### Timestamps
All entities include `createdAt` and `updatedAt` timestamps that are automatically managed by the system.

### Identifiers
All entities have unique identifier fields that serve as primary keys in the Cyoda system.

### State Management
Entity states are managed automatically by the workflow system and should not be included as entity fields. Access via `entity.meta.state` in processors.

### Validation
All entities implement the `isValid()` method from CyodaEntity interface to ensure data integrity before persistence.
