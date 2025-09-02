# Entity Requirements

## Overview
This document defines the entities for the Cyoda OMS Backend system. Each entity represents a core business object with specific attributes and relationships. Entity state is managed internally by the workflow system and should not be included in the entity schema.

## Entities

### 1. Product
**Purpose**: Represents catalog items available for purchase

**Attributes**:
- `sku` (string, required, unique): Stock Keeping Unit identifier
- `name` (string, required): Product name
- `description` (string, required): Product description
- `price` (number, required): Default/base price (fallback)
- `quantityAvailable` (number, required): Quick projection field for available stock
- `category` (string, required): Product category for filtering
- `warehouseId` (string, optional): Default primary warehouse node
- `attributes` (object, optional): Extended attributes including brand, model, dimensions, weight, hazards, custom fields
- `localizations` (object, optional): Multi-language content and regulatory information
- `media` (array, optional): Images, documents, and other media assets
- `options` (object, optional): Product option axes and constraints
- `variants` (array, optional): Product variants with option values and overrides
- `bundles` (array, optional): Kit and bundle configurations
- `inventory` (object, optional): Detailed inventory information across nodes
- `compliance` (object, optional): Compliance documents and restrictions
- `relationships` (object, optional): Supplier and related product information
- `events` (array, optional): Product lifecycle events
- `createdAt` (timestamp): Creation timestamp
- `updatedAt` (timestamp): Last update timestamp

**Relationships**:
- Referenced by Cart lines (via sku)
- Referenced by Order lines (via sku)
- Referenced by Shipment lines (via sku)

**Notes**: 
- Must use the complete Product schema as provided in the requirements
- Entity state is managed by workflow (not included in schema)

### 2. Cart
**Purpose**: Represents a shopping cart for anonymous users

**Attributes**:
- `cartId` (string, required, unique): Cart identifier
- `lines` (array, required): Cart line items with sku, name, price, qty
- `totalItems` (number, required): Total number of items in cart
- `grandTotal` (number, required): Total cart value
- `guestContact` (object, optional): Guest contact information including name, email, phone, address
- `createdAt` (timestamp): Creation timestamp
- `updatedAt` (timestamp): Last update timestamp

**Relationships**:
- References Product entities via line items (sku)
- Referenced by Payment (via cartId)
- Referenced by Order creation process

**Notes**: 
- Status field is managed as entity state (NEW, ACTIVE, CHECKING_OUT, CONVERTED)
- Cart lines contain product snapshot data for consistency

### 3. Payment
**Purpose**: Represents payment transactions (dummy implementation)

**Attributes**:
- `paymentId` (string, required, unique): Payment identifier
- `cartId` (string, required): Associated cart identifier
- `amount` (number, required): Payment amount
- `provider` (string, required): Payment provider (always "DUMMY")
- `createdAt` (timestamp): Creation timestamp
- `updatedAt` (timestamp): Last update timestamp

**Relationships**:
- References Cart (via cartId)
- Referenced by Order creation process

**Notes**: 
- Status field is managed as entity state (INITIATED, PAID, FAILED, CANCELED)
- Dummy implementation auto-approves after ~3 seconds

### 4. Order
**Purpose**: Represents confirmed orders from successful payments

**Attributes**:
- `orderId` (string, required, unique): Order identifier
- `orderNumber` (string, required): Short ULID for customer reference
- `lines` (array, required): Order line items with sku, name, unitPrice, qty, lineTotal
- `totals` (object, required): Order totals including items and grand total
- `guestContact` (object, required): Customer contact information including name, email, phone, address
- `createdAt` (timestamp): Creation timestamp
- `updatedAt` (timestamp): Last update timestamp

**Relationships**:
- Created from Cart and Payment entities
- Has one Shipment (single shipment per order)
- References Product entities via line items (sku)

**Notes**: 
- Status field is managed as entity state (WAITING_TO_FULFILL, PICKING, WAITING_TO_SEND, SENT, DELIVERED)
- Contains snapshot of cart data at time of order creation

### 5. Shipment
**Purpose**: Represents physical shipment of order items

**Attributes**:
- `shipmentId` (string, required, unique): Shipment identifier
- `orderId` (string, required): Associated order identifier
- `lines` (array, required): Shipment line items with sku, qtyOrdered, qtyPicked, qtyShipped
- `createdAt` (timestamp): Creation timestamp
- `updatedAt` (timestamp): Last update timestamp

**Relationships**:
- Belongs to one Order (via orderId)
- References Product entities via line items (sku)

**Notes**: 
- Status field is managed as entity state (PICKING, WAITING_TO_SEND, SENT, DELIVERED)
- Single shipment per order for demo purposes
- Shipment status drives Order status updates

## Entity State Management

All entities use the Cyoda workflow system for state management:
- Entity state is accessed via `entity.meta.state`
- State transitions are controlled by workflows
- Status fields are NOT included in entity schemas
- State changes trigger workflow processors and criteria
