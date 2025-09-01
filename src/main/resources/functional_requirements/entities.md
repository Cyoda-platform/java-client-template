# Entities

This document defines the entities for the Cyoda OMS Backend system.

## Product

**Description**: Represents a product in the catalog with full schema for persistence and round-trip.

**Attributes**:
- `sku` (string, required, unique): Stock Keeping Unit identifier
- `name` (string, required): Product name
- `description` (string, optional): Product description
- `price` (number, required): Product price
- `quantityAvailable` (number, required): Available stock quantity
- `category` (string, required): Product category
- `warehouseId` (string, optional): Warehouse identifier
- `attributes` (object, optional): Additional product attributes
- `localizations` (object, optional): Localization data
- `media` (object, optional): Media files (images, videos)
- `options` (object, optional): Product options/variants
- `variants` (object, optional): Product variants
- `bundles` (object, optional): Bundle information
- `inventory` (object, optional): Inventory details
- `compliance` (object, optional): Compliance information
- `relationships` (object, optional): Related products
- `events` (object, optional): Product events

**Relationships**:
- Referenced by Cart lines via `sku`
- Referenced by Order lines via `sku`
- Referenced by Shipment lines via `sku`

**Notes**: 
- The complete Product schema must be persisted exactly as provided
- List endpoints return slim DTO, detail endpoints return full document
- Entity state is managed by the system (not part of schema)

---

## Cart

**Description**: Shopping cart for anonymous users during the shopping process.

**Attributes**:
- `cartId` (string, required): Unique cart identifier
- `lines` (array, required): Cart line items
  - `sku` (string): Product SKU
  - `name` (string): Product name
  - `price` (number): Product price
  - `qty` (number): Quantity
- `totalItems` (number, required): Total number of items
- `grandTotal` (number, required): Grand total amount
- `guestContact` (object, optional): Guest contact information
  - `name` (string, optional): Guest name
  - `email` (string, optional): Guest email
  - `phone` (string, optional): Guest phone
  - `address` (object, optional): Guest address
    - `line1` (string, optional): Address line 1
    - `city` (string, optional): City
    - `postcode` (string, optional): Postal code
    - `country` (string, optional): Country
- `createdAt` (datetime): Creation timestamp
- `updatedAt` (datetime): Last update timestamp

**Relationships**:
- References Product entities via `lines[].sku`
- Referenced by Payment via `cartId`
- Referenced by Order via cart snapshot

**State Management**:
- Entity state represents cart workflow status: NEW → ACTIVE → CHECKING_OUT → CONVERTED
- State is managed automatically by the workflow system

---

## Payment

**Description**: Dummy payment processing for demonstration purposes.

**Attributes**:
- `paymentId` (string, required): Unique payment identifier
- `cartId` (string, required): Associated cart identifier
- `amount` (number, required): Payment amount
- `provider` (string, required): Payment provider (always "DUMMY")
- `createdAt` (datetime): Creation timestamp
- `updatedAt` (datetime): Last update timestamp

**Relationships**:
- References Cart via `cartId`
- Referenced by Order creation process

**State Management**:
- Entity state represents payment status: INITIATED → PAID | FAILED | CANCELED
- Auto-transitions to PAID after ~3 seconds for demo purposes

---

## Order

**Description**: Confirmed order after successful payment processing.

**Attributes**:
- `orderId` (string, required): Unique order identifier
- `orderNumber` (string, required): Short ULID for customer reference
- `lines` (array, required): Order line items
  - `sku` (string): Product SKU
  - `name` (string): Product name
  - `unitPrice` (number): Unit price
  - `qty` (number): Quantity ordered
  - `lineTotal` (number): Line total amount
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
- `createdAt` (datetime): Creation timestamp
- `updatedAt` (datetime): Last update timestamp

**Relationships**:
- Created from Cart snapshot
- References Product entities via `lines[].sku`
- Has one Shipment

**State Management**:
- Entity state represents order lifecycle: WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED
- State progresses based on shipment status

---

## Shipment

**Description**: Single shipment per order for demo purposes.

**Attributes**:
- `shipmentId` (string, required): Unique shipment identifier
- `orderId` (string, required): Associated order identifier
- `lines` (array, required): Shipment line items
  - `sku` (string): Product SKU
  - `qtyOrdered` (number): Quantity ordered
  - `qtyPicked` (number): Quantity picked
  - `qtyShipped` (number): Quantity shipped
- `createdAt` (datetime): Creation timestamp
- `updatedAt` (datetime): Last update timestamp

**Relationships**:
- References Order via `orderId`
- References Product entities via `lines[].sku`

**State Management**:
- Entity state represents shipment status: PICKING → WAITING_TO_SEND → SENT → DELIVERED
- State changes drive Order state updates
