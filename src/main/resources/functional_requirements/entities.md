# Entity Requirements

## Product Entity

**Name**: Product
**Description**: Represents a product in the e-commerce catalog with full schema support for complex product data.

### Attributes:
- **sku** (string, required, unique): Stock Keeping Unit identifier
- **name** (string, required): Product display name
- **description** (string, required): Product description
- **price** (number, required): Base price for the product
- **quantityAvailable** (number, required): Available stock quantity
- **category** (string, required): Product category
- **warehouseId** (string, optional): Primary warehouse identifier

### Complex Attributes:
- **attributes**: Object containing brand, model, dimensions, weight, hazards, and custom fields
- **localizations**: Multi-language content with locale-specific names, descriptions, and regulatory info
- **media**: Array of images and documents with metadata
- **options**: Product option axes (color, capacity) with constraints
- **variants**: Product variants with option values, barcodes, and pricing overrides
- **bundles**: Kit and bundle configurations with components
- **inventory**: Inventory nodes, lots, reservations, and policies
- **compliance**: Regulatory documents and restrictions
- **relationships**: Supplier and related product information
- **events**: Product lifecycle events

### Relationships:
- **Cart**: Products can be added to cart lines (many-to-many through CartLine)
- **Order**: Products are referenced in order lines (many-to-many through OrderLine)
- **Shipment**: Products are tracked in shipment lines (many-to-many through ShipmentLine)

### Notes:
- Entity state is managed automatically by the workflow system
- The full Product schema must be persisted exactly as provided
- UI list views use a slim DTO mapping for performance

---

## Cart Entity

**Name**: Cart
**Description**: Shopping cart for anonymous users with line items and totals.

### Attributes:
- **cartId** (string, required, unique): Cart identifier
- **lines** (array, required): Cart line items with sku, name, price, qty
- **totalItems** (number, required): Total quantity of items
- **grandTotal** (number, required): Total cart value
- **guestContact** (object, optional): Guest contact information
  - **name** (string, optional): Guest name
  - **email** (string, optional): Guest email
  - **phone** (string, optional): Guest phone
  - **address** (object, optional): Guest address
    - **line1** (string, optional): Address line 1
    - **city** (string, optional): City
    - **postcode** (string, optional): Postal code
    - **country** (string, optional): Country
- **createdAt** (datetime, auto): Creation timestamp
- **updatedAt** (datetime, auto): Last update timestamp

### Relationships:
- **Product**: Cart lines reference products by SKU (many-to-many)
- **Payment**: One-to-one relationship with payment
- **Order**: Converted to order upon checkout completion

### Workflow States:
- NEW: Initial state when cart is created
- ACTIVE: Cart with items, can be modified
- CHECKING_OUT: Cart in checkout process
- CONVERTED: Cart converted to order

### Notes:
- Status field is managed as entity.meta.state, not included in schema
- Totals are recalculated automatically via processors

---

## Payment Entity

**Name**: Payment
**Description**: Dummy payment processing for demonstration purposes.

### Attributes:
- **paymentId** (string, required, unique): Payment identifier
- **cartId** (string, required): Associated cart identifier
- **amount** (number, required): Payment amount
- **provider** (string, required): Payment provider (always "DUMMY")
- **createdAt** (datetime, auto): Creation timestamp
- **updatedAt** (datetime, auto): Last update timestamp

### Relationships:
- **Cart**: One-to-one relationship with cart
- **Order**: Referenced during order creation

### Workflow States:
- INITIATED: Payment started
- PAID: Payment completed successfully
- FAILED: Payment failed
- CANCELED: Payment canceled

### Notes:
- Status field is managed as entity.meta.state, not included in schema
- Auto-approval after 3 seconds via processor

---

## Order Entity

**Name**: Order
**Description**: Customer order created from successful cart checkout.

### Attributes:
- **orderId** (string, required, unique): Order identifier
- **orderNumber** (string, required): Short ULID for customer reference
- **lines** (array, required): Order line items with sku, name, unitPrice, qty, lineTotal
- **totals** (object, required): Order totals
  - **items** (number): Total items count
  - **grand** (number): Grand total amount
- **guestContact** (object, required): Customer contact information
  - **name** (string, required): Customer name
  - **email** (string, optional): Customer email
  - **phone** (string, optional): Customer phone
  - **address** (object, required): Shipping address
    - **line1** (string, required): Address line 1
    - **city** (string, required): City
    - **postcode** (string, required): Postal code
    - **country** (string, required): Country
- **createdAt** (datetime, auto): Creation timestamp
- **updatedAt** (datetime, auto): Last update timestamp

### Relationships:
- **Cart**: Created from cart data
- **Payment**: References payment for creation
- **Shipment**: One-to-one relationship with shipment
- **Product**: Order lines reference products (many-to-many)

### Workflow States:
- WAITING_TO_FULFILL: Order created, waiting for fulfillment
- PICKING: Order being picked in warehouse
- WAITING_TO_SEND: Order picked, waiting for shipment
- SENT: Order shipped
- DELIVERED: Order delivered to customer

### Notes:
- Status field is managed as entity.meta.state, not included in schema
- Created by snapshotting cart data and guest contact

---

## Shipment Entity

**Name**: Shipment
**Description**: Single shipment per order for tracking fulfillment.

### Attributes:
- **shipmentId** (string, required, unique): Shipment identifier
- **orderId** (string, required): Associated order identifier
- **lines** (array, required): Shipment line items
  - **sku** (string): Product SKU
  - **qtyOrdered** (number): Quantity ordered
  - **qtyPicked** (number): Quantity picked
  - **qtyShipped** (number): Quantity shipped
- **createdAt** (datetime, auto): Creation timestamp
- **updatedAt** (datetime, auto): Last update timestamp

### Relationships:
- **Order**: One-to-one relationship with order
- **Product**: Shipment lines reference products (many-to-many)

### Workflow States:
- PICKING: Items being picked from warehouse
- WAITING_TO_SEND: Items picked, waiting for dispatch
- SENT: Shipment dispatched
- DELIVERED: Shipment delivered

### Notes:
- Status field is managed as entity.meta.state, not included in schema
- Single shipment per order for demo simplicity
- Shipment status drives order status updates
