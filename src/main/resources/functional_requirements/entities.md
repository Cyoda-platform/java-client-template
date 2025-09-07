# Entity Requirements

## Overview
This document defines the detailed requirements for all entities in the Cyoda OMS Backend system. Each entity represents a core business object that will be persisted in Cyoda and managed through workflows.

**Important Note on Entity State**: Entity state is an internal attribute managed by the workflow system and accessed via `entity.meta.state`. Status/state fields should NOT be included in the entity schema as they are automatically managed by Cyoda workflows.

## Entities

### 1. Product Entity

**Entity Name**: Product  
**Business Identifier**: sku (unique)  
**Description**: Represents a product in the catalog with complete schema for persistence and round-trip compatibility.

#### Attributes

**Core Required Fields:**
- `sku`: String (required, unique) - Stock Keeping Unit identifier
- `name`: String (required) - Product display name
- `description`: String (required) - Product description
- `price`: Number (required) - Default/base price (fallback)
- `quantityAvailable`: Number (required) - Quick projection field for inventory
- `category`: String (required) - Product category for filtering
- `warehouseId`: String (optional) - Default primary warehouse node

**Extended Schema (as per attached Product schema):**
- `attributes`: Object containing:
  - `brand`: String
  - `model`: String
  - `dimensions`: Object with `l`, `w`, `h` (numbers), `unit` (string)
  - `weight`: Object with `value` (number), `unit` (string)
  - `hazards`: Array of objects with `class`, `transportNotes`
  - `custom`: Object (open extension bag)

- `localizations`: Object containing:
  - `defaultLocale`: String
  - `content`: Array of localization objects with `locale`, `name`, `description`, `regulatory`, `salesRestrictions`

- `media`: Array of media objects with `type`, `url`, `alt`, `tags`, `sha256`, `title`, `regionScope`

- `options`: Object containing:
  - `axes`: Array of option axes with `code`, `values`
  - `constraints`: Array of constraint objects

- `variants`: Array of variant objects with `variantSku`, `optionValues`, `attributes`, `barcodes`, `priceOverrides`, `inventoryPolicy`

- `bundles`: Array of bundle objects with `type`, `sku`, `components`

- `inventory`: Object containing:
  - `nodes`: Array of inventory nodes with detailed lot, reservation, and transit information
  - `policies`: Object with allocation and oversell policies

- `compliance`: Object containing:
  - `docs`: Array of compliance documents
  - `restrictions`: Array of regional restrictions

- `relationships`: Object containing:
  - `suppliers`: Array of supplier relationships
  - `relatedProducts`: Array of related product references

- `events`: Array of event objects with `type`, `at`, `payload`

#### Relationships
- **One-to-Many with Cart Lines**: Products can appear in multiple cart lines
- **One-to-Many with Order Lines**: Products can appear in multiple order lines
- **One-to-Many with Shipment Lines**: Products can appear in multiple shipment lines

#### Business Rules
- SKU must be unique across all products
- quantityAvailable is decremented when orders are created
- Price must be positive
- Category is required for filtering functionality

---

### 2. Cart Entity

**Entity Name**: Cart  
**Business Identifier**: cartId (unique)  
**Description**: Represents a shopping cart for anonymous checkout process.

#### Attributes

**Core Fields:**
- `cartId`: String (required, unique) - Cart identifier
- `lines`: Array of cart line objects (required) - Cart items
  - Each line contains: `sku`, `name`, `price`, `qty`
- `totalItems`: Number (required) - Total quantity of items
- `grandTotal`: Number (required) - Total cart value
- `guestContact`: Object (optional) - Guest contact information
  - `name`: String (optional)
  - `email`: String (optional)
  - `phone`: String (optional)
  - `address`: Object (optional)
    - `line1`: String (optional)
    - `city`: String (optional)
    - `postcode`: String (optional)
    - `country`: String (optional)
- `createdAt`: DateTime (auto-generated)
- `updatedAt`: DateTime (auto-generated)

#### Relationships
- **One-to-One with Payment**: Cart can have one payment
- **One-to-One with Order**: Cart converts to one order
- **Many-to-One with Product**: Cart lines reference products via SKU

#### Business Rules
- Cart starts in NEW state, moves to ACTIVE on first item add
- Totals must be recalculated when lines change
- Guest contact is required before checkout
- Cart can only be converted once to an order

#### Workflow States (managed by entity.meta.state)
- NEW: Initial state
- ACTIVE: Has items, can be modified
- CHECKING_OUT: Guest contact added, ready for payment
- CONVERTED: Successfully converted to order

---

### 3. Payment Entity

**Entity Name**: Payment  
**Business Identifier**: paymentId (unique)  
**Description**: Represents a dummy payment for demonstration purposes.

#### Attributes

**Core Fields:**
- `paymentId`: String (required, unique) - Payment identifier
- `cartId`: String (required) - Reference to cart
- `amount`: Number (required) - Payment amount
- `provider`: String (required) - Always "DUMMY" for demo
- `createdAt`: DateTime (auto-generated)
- `updatedAt`: DateTime (auto-generated)

#### Relationships
- **One-to-One with Cart**: Payment belongs to one cart
- **One-to-One with Order**: Payment triggers order creation

#### Business Rules
- Provider is always "DUMMY" for demo purposes
- Amount must match cart grand total
- Auto-approves after ~3 seconds via processor
- Payment must be PAID before order creation

#### Workflow States (managed by entity.meta.state)
- INITIATED: Payment started
- PAID: Payment successful (auto after 3s)
- FAILED: Payment failed
- CANCELED: Payment canceled

---

### 4. Order Entity

**Entity Name**: Order  
**Business Identifier**: orderId (unique)  
**Description**: Represents a customer order created from a paid cart.

#### Attributes

**Core Fields:**
- `orderId`: String (required, unique) - Order identifier
- `orderNumber`: String (required, unique) - Short ULID for display
- `lines`: Array of order line objects (required) - Order items
  - Each line contains: `sku`, `name`, `unitPrice`, `qty`, `lineTotal`
- `totals`: Object (required) - Order totals
  - `items`: Number - Total items count
  - `grand`: Number - Grand total amount
- `guestContact`: Object (required) - Customer contact information
  - `name`: String (required)
  - `email`: String (optional)
  - `phone`: String (optional)
  - `address`: Object (required)
    - `line1`: String (required)
    - `city`: String (required)
    - `postcode`: String (required)
    - `country`: String (required)
- `createdAt`: DateTime (auto-generated)
- `updatedAt`: DateTime (auto-generated)

#### Relationships
- **One-to-One with Cart**: Order created from one cart
- **One-to-One with Payment**: Order created from one payment
- **One-to-One with Shipment**: Order has one shipment (demo constraint)
- **Many-to-One with Product**: Order lines reference products via SKU

#### Business Rules
- Created only from PAID payments
- Order number is a short ULID for user-friendly display
- Guest contact address is required (not optional like in cart)
- Stock is decremented when order is created
- Single shipment per order (demo constraint)

#### Workflow States (managed by entity.meta.state)
- WAITING_TO_FULFILL: Order created, waiting to start fulfillment
- PICKING: Items being picked from warehouse
- WAITING_TO_SEND: Picked, waiting for shipment
- SENT: Shipped to customer
- DELIVERED: Delivered to customer

---

### 5. Shipment Entity

**Entity Name**: Shipment  
**Business Identifier**: shipmentId (unique)  
**Description**: Represents a shipment for order fulfillment (single shipment per order for demo).

#### Attributes

**Core Fields:**
- `shipmentId`: String (required, unique) - Shipment identifier
- `orderId`: String (required) - Reference to order
- `lines`: Array of shipment line objects (required) - Shipment items
  - Each line contains: `sku`, `qtyOrdered`, `qtyPicked`, `qtyShipped`
- `createdAt`: DateTime (auto-generated)
- `updatedAt`: DateTime (auto-generated)

#### Relationships
- **One-to-One with Order**: Shipment belongs to one order
- **Many-to-One with Product**: Shipment lines reference products via SKU

#### Business Rules
- Created automatically when order is created
- Single shipment per order (demo constraint)
- Shipment state drives order state
- Quantities track progression: ordered → picked → shipped

#### Workflow States (managed by entity.meta.state)
- PICKING: Items being picked from warehouse
- WAITING_TO_SEND: Picked, ready for shipment
- SENT: Shipped to customer
- DELIVERED: Delivered to customer

## Entity Relationships Summary

```
Product (1) ←→ (M) Cart.lines
Product (1) ←→ (M) Order.lines  
Product (1) ←→ (M) Shipment.lines

Cart (1) ←→ (1) Payment
Cart (1) ←→ (1) Order

Payment (1) ←→ (1) Order

Order (1) ←→ (1) Shipment
```

## Technical Notes

1. **State Management**: All entity states are managed by Cyoda workflows via `entity.meta.state`
2. **Business Identifiers**: Each entity has a user-friendly business ID for API operations
3. **Timestamps**: createdAt/updatedAt are auto-generated by the system
4. **Product Schema**: Must use the complete attached schema for persistence and round-trip compatibility
5. **Demo Constraints**: Single shipment per order, dummy payments, anonymous checkout only
