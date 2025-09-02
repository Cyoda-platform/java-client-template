# Entity Requirements

## Overview
This document defines the detailed requirements for all entities in the Cyoda OMS Backend system. Each entity represents a business object that will be persisted in Cyoda and managed through workflows.

**Important Note on Entity State:**
- Entity state is an internal attribute managed automatically by the workflow system
- It is accessed via `entity.meta.state` in processor code but cannot be directly modified
- Status fields in entity schemas should NOT be included as they are replaced by the workflow state management
- The system automatically manages state transitions based on workflow definitions

## Entities

### 1. Product

**Purpose:** Represents catalog items available for purchase with comprehensive product information.

**Business ID:** `sku` (unique identifier for business operations)

**Attributes:**
- `sku` (string, required, unique): Stock Keeping Unit - unique product identifier
- `name` (string, required): Product display name
- `description` (string, required): Product description
- `price` (number, required): Base price in system currency
- `quantityAvailable` (number, required): Current available stock quantity
- `category` (string, required): Product category for filtering and organization
- `warehouseId` (string, optional): Default warehouse location identifier

**Extended Schema Attributes (as per attached schema):**
- `attributes`: Product attributes including brand, model, dimensions, weight, hazards, custom fields
- `localizations`: Multi-language content and regulatory information
- `media`: Images, documents, and other media assets
- `options`: Product option axes and constraints for variants
- `variants`: Product variants with different option combinations
- `bundles`: Kit and bundle configurations
- `inventory`: Detailed inventory information across nodes
- `compliance`: Regulatory compliance documents and restrictions
- `relationships`: Supplier and related product information
- `events`: Product lifecycle events

**Relationships:**
- Referenced by Cart lines (via sku)
- Referenced by Order lines (via sku)
- Referenced by Shipment lines (via sku)

**Workflow State:** Managed by ProductWorkflow (states like active, discontinued, etc.)

### 2. Cart

**Purpose:** Represents a shopping cart for anonymous users during the shopping session.

**Business ID:** `cartId` (unique identifier for business operations)

**Attributes:**
- `cartId` (string, required, unique): Unique cart identifier
- `lines` (array, required): Cart line items
  - `sku` (string): Product SKU
  - `name` (string): Product name (snapshot)
  - `price` (number): Product price (snapshot)
  - `qty` (number): Quantity in cart
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
- `createdAt` (timestamp, auto-generated): Cart creation timestamp
- `updatedAt` (timestamp, auto-generated): Last update timestamp

**Relationships:**
- References Product entities (via sku in lines)
- Referenced by Payment (via cartId)
- Referenced by Order creation process

**Workflow State:** Managed by CartWorkflow (NEW → ACTIVE → CHECKING_OUT → CONVERTED)

### 3. Payment

**Purpose:** Represents payment transactions for cart checkout (dummy implementation for demo).

**Business ID:** `paymentId` (unique identifier for business operations)

**Attributes:**
- `paymentId` (string, required, unique): Unique payment identifier
- `cartId` (string, required): Associated cart identifier
- `amount` (number, required): Payment amount
- `provider` (string, required): Payment provider (always "DUMMY" for demo)
- `createdAt` (timestamp, auto-generated): Payment creation timestamp
- `updatedAt` (timestamp, auto-generated): Last update timestamp

**Relationships:**
- References Cart (via cartId)
- Referenced by Order creation process

**Workflow State:** Managed by PaymentWorkflow (INITIATED → PAID | FAILED | CANCELED)

### 4. Order

**Purpose:** Represents confirmed orders after successful payment processing.

**Business ID:** `orderId` (unique identifier for business operations)

**Attributes:**
- `orderId` (string, required, unique): Unique order identifier
- `orderNumber` (string, required): Short ULID for customer reference
- `lines` (array, required): Order line items (snapshot from cart)
  - `sku` (string): Product SKU
  - `name` (string): Product name
  - `unitPrice` (number): Unit price at time of order
  - `qty` (number): Ordered quantity
  - `lineTotal` (number): Line total (unitPrice * qty)
- `totals` (object, required): Order totals
  - `items` (number): Total items count
  - `grand` (number): Grand total amount
- `guestContact` (object, required): Customer contact information
  - `name` (string, required): Customer name
  - `email` (string, optional): Customer email
  - `phone` (string, optional): Customer phone
  - `address` (object, required): Shipping address
    - `line1` (string, required): Address line 1
    - `city` (string, required): City
    - `postcode` (string, required): Postal code
    - `country` (string, required): Country
- `createdAt` (timestamp, auto-generated): Order creation timestamp
- `updatedAt` (timestamp, auto-generated): Last update timestamp

**Relationships:**
- Created from Cart and Payment
- Has one Shipment (single shipment per order for demo)
- References Product entities (via sku in lines)

**Workflow State:** Managed by OrderWorkflow (WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED)

### 5. Shipment

**Purpose:** Represents the physical shipment of order items (single shipment per order for demo).

**Business ID:** `shipmentId` (unique identifier for business operations)

**Attributes:**
- `shipmentId` (string, required, unique): Unique shipment identifier
- `orderId` (string, required): Associated order identifier
- `lines` (array, required): Shipment line items
  - `sku` (string): Product SKU
  - `qtyOrdered` (number): Originally ordered quantity
  - `qtyPicked` (number): Quantity picked for shipment
  - `qtyShipped` (number): Quantity actually shipped
- `createdAt` (timestamp, auto-generated): Shipment creation timestamp
- `updatedAt` (timestamp, auto-generated): Last update timestamp

**Relationships:**
- Belongs to one Order (via orderId)
- References Product entities (via sku in lines)

**Workflow State:** Managed by ShipmentWorkflow (PICKING → WAITING_TO_SEND → SENT → DELIVERED)

## Entity State Management

All entities use workflow-managed states instead of status fields:

- **Product**: States managed by ProductWorkflow
- **Cart**: States managed by CartWorkflow (NEW → ACTIVE → CHECKING_OUT → CONVERTED)
- **Payment**: States managed by PaymentWorkflow (INITIATED → PAID | FAILED | CANCELED)
- **Order**: States managed by OrderWorkflow (WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED)
- **Shipment**: States managed by ShipmentWorkflow (PICKING → WAITING_TO_SEND → SENT → DELIVERED)

State transitions are triggered by workflow processors and criteria, ensuring consistent business logic execution.
