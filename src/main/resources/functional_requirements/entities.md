# Entity Requirements

## Overview
This document defines the detailed requirements for all entities in the Cyoda OMS Backend system. Each entity implements the CyodaEntity interface and represents business data that flows through workflows.

**Important Note on Entity State:**
- Entity state is managed internally by the Cyoda workflow system
- State is accessed via `entity.meta.state` in processor code
- State cannot be directly modified by application code
- The workflow system automatically manages state transitions
- Fields like "status" in the user requirements map to the internal entity state

## 1. Product Entity

**Entity Name:** Product
**Model Key:** "Product"
**Package:** `com.java_template.application.entity`

### Attributes
The Product entity must use the complete schema provided in the user requirements verbatim for persistence and round-trip operations.

**Core Required Fields:**
- `sku` (String) - Unique product identifier
- `name` (String) - Product name
- `description` (String) - Product description
- `price` (BigDecimal) - Default/base price
- `quantityAvailable` (Integer) - Available quantity for quick projection
- `category` (String) - Product category
- `warehouseId` (String, nullable) - Optional default primary warehouse node

**Complex Nested Objects:**
- `attributes` (Object) - Brand, model, dimensions, weight, hazards, custom fields
- `localizations` (Object) - Multi-locale content with regulatory information
- `media` (List) - Images, documents with metadata
- `options` (Object) - Product option axes and constraints
- `variants` (List) - Product variants with SKUs and option values
- `bundles` (List) - Kit and bundle configurations
- `inventory` (Object) - Inventory nodes, lots, reservations, policies
- `compliance` (Object) - Compliance documents and restrictions
- `relationships` (Object) - Suppliers and related products
- `events` (List) - Product lifecycle events

**Timestamps:**
- `createdAt` (LocalDateTime) - Creation timestamp
- `updatedAt` (LocalDateTime) - Last update timestamp

### Relationships
- **One-to-Many with Cart:** Products can be referenced in multiple cart lines
- **One-to-Many with Order:** Products can be referenced in multiple order lines
- **One-to-Many with Shipment:** Products can be referenced in multiple shipment lines

### Validation Rules
- SKU must be unique across all products
- Price must be greater than or equal to 0
- QuantityAvailable must be greater than or equal to 0
- Category must not be empty
- Name must not be empty

## 2. Cart Entity

**Entity Name:** Cart
**Model Key:** "Cart"
**Package:** `com.java_template.application.entity`

### Attributes
**Core Fields:**
- `cartId` (String) - Unique cart identifier
- `lines` (List<CartLine>) - Cart line items
- `totalItems` (Integer) - Total number of items in cart
- `grandTotal` (BigDecimal) - Total cart value
- `guestContact` (GuestContact, nullable) - Guest contact information

**CartLine Object:**
- `sku` (String) - Product SKU
- `name` (String) - Product name (snapshot)
- `price` (BigDecimal) - Product price (snapshot)
- `qty` (Integer) - Quantity

**GuestContact Object:**
- `name` (String, nullable) - Guest name
- `email` (String, nullable) - Guest email
- `phone` (String, nullable) - Guest phone
- `address` (Address, nullable) - Guest address

**Address Object:**
- `line1` (String, nullable) - Address line 1
- `city` (String, nullable) - City
- `postcode` (String, nullable) - Postal code
- `country` (String, nullable) - Country

**Timestamps:**
- `createdAt` (LocalDateTime) - Creation timestamp
- `updatedAt` (LocalDateTime) - Last update timestamp

### Entity State Mapping
The user requirement "status" field maps to entity.meta.state:
- NEW → Initial state
- ACTIVE → Active state
- CHECKING_OUT → CheckingOut state
- CONVERTED → Converted state

### Relationships
- **One-to-One with Payment:** Cart can have one payment
- **One-to-One with Order:** Cart can be converted to one order

### Validation Rules
- CartId must be unique
- Lines cannot be empty when cart is ACTIVE
- Quantity in each line must be greater than 0
- GrandTotal must equal sum of all line totals
- TotalItems must equal sum of all line quantities

## 3. Payment Entity

**Entity Name:** Payment
**Model Key:** "Payment"
**Package:** `com.java_template.application.entity`

### Attributes
**Core Fields:**
- `paymentId` (String) - Unique payment identifier
- `cartId` (String) - Associated cart identifier
- `amount` (BigDecimal) - Payment amount
- `provider` (String) - Payment provider (always "DUMMY" for this demo)

**Timestamps:**
- `createdAt` (LocalDateTime) - Creation timestamp
- `updatedAt` (LocalDateTime) - Last update timestamp

### Entity State Mapping
The user requirement "status" field maps to entity.meta.state:
- INITIATED → Initial state
- PAID → Paid state
- FAILED → Failed state
- CANCELED → Canceled state

### Relationships
- **Many-to-One with Cart:** Multiple payments can reference same cart (retry scenarios)

### Validation Rules
- PaymentId must be unique
- CartId must reference an existing cart
- Amount must be greater than 0
- Provider must be "DUMMY"

## 4. Order Entity

**Entity Name:** Order
**Model Key:** "Order"
**Package:** `com.java_template.application.entity`

### Attributes
**Core Fields:**
- `orderId` (String) - Unique order identifier
- `orderNumber` (String) - Short ULID for customer reference
- `lines` (List<OrderLine>) - Order line items
- `totals` (OrderTotals) - Order totals
- `guestContact` (GuestContact) - Guest contact information (required)

**OrderLine Object:**
- `sku` (String) - Product SKU
- `name` (String) - Product name (snapshot)
- `unitPrice` (BigDecimal) - Unit price (snapshot)
- `qty` (Integer) - Quantity ordered
- `lineTotal` (BigDecimal) - Line total (unitPrice * qty)

**OrderTotals Object:**
- `items` (BigDecimal) - Items total
- `grand` (BigDecimal) - Grand total

**GuestContact Object:** (Same as Cart entity)
- `name` (String) - Guest name (required for orders)
- `email` (String, nullable) - Guest email
- `phone` (String, nullable) - Guest phone
- `address` (Address) - Guest address (required for orders)

**Address Object:** (Same as Cart entity, but required fields for orders)
- `line1` (String) - Address line 1 (required)
- `city` (String) - City (required)
- `postcode` (String) - Postal code (required)
- `country` (String) - Country (required)

**Timestamps:**
- `createdAt` (LocalDateTime) - Creation timestamp
- `updatedAt` (LocalDateTime) - Last update timestamp

### Entity State Mapping
The user requirement "status" field maps to entity.meta.state:
- WAITING_TO_FULFILL → Initial state
- PICKING → Picking state
- WAITING_TO_SEND → WaitingToSend state
- SENT → Sent state
- DELIVERED → Delivered state

### Relationships
- **One-to-One with Cart:** Order is created from one cart
- **One-to-One with Payment:** Order is created from one payment
- **One-to-One with Shipment:** Order has one shipment (demo constraint)

### Validation Rules
- OrderId must be unique
- OrderNumber must be unique and follow ULID format
- Lines cannot be empty
- Guest contact name and address are required
- Address line1, city, postcode, and country are required
- Totals must be calculated correctly

## 5. Shipment Entity

**Entity Name:** Shipment
**Model Key:** "Shipment"
**Package:** `com.java_template.application.entity`

### Attributes
**Core Fields:**
- `shipmentId` (String) - Unique shipment identifier
- `orderId` (String) - Associated order identifier
- `lines` (List<ShipmentLine>) - Shipment line items

**ShipmentLine Object:**
- `sku` (String) - Product SKU
- `qtyOrdered` (Integer) - Quantity originally ordered
- `qtyPicked` (Integer) - Quantity picked for shipment
- `qtyShipped` (Integer) - Quantity actually shipped

**Timestamps:**
- `createdAt` (LocalDateTime) - Creation timestamp
- `updatedAt` (LocalDateTime) - Last update timestamp

### Entity State Mapping
The user requirement "status" field maps to entity.meta.state:
- PICKING → Initial state
- WAITING_TO_SEND → WaitingToSend state
- SENT → Sent state
- DELIVERED → Delivered state

### Relationships
- **Many-to-One with Order:** Multiple shipments can belong to one order (but demo uses single shipment)

### Validation Rules
- ShipmentId must be unique
- OrderId must reference an existing order
- Lines cannot be empty
- QtyPicked cannot exceed qtyOrdered
- QtyShipped cannot exceed qtyPicked
- All quantities must be greater than or equal to 0

## Common Patterns

### Entity Implementation Requirements
All entities must:
1. Implement the `CyodaEntity` interface
2. Provide `getModelKey()` method returning the entity name
3. Provide `isValid()` method for validation
4. Use `@Component` annotation for Spring discovery
5. Be placed in `com.java_template.application.entity` package

### Timestamp Management
All entities include:
- `createdAt` - Set when entity is first created
- `updatedAt` - Updated whenever entity is modified

### State Management
- Entity state is managed by the Cyoda workflow system
- Access state via `entity.meta.state` in processors
- State cannot be directly modified by application code
- Workflow transitions automatically update entity state
