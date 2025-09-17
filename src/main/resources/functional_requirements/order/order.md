# Order Entity Requirements

## Overview
Order represents a confirmed purchase after successful payment.

## Attributes
- **orderId** (string, required): Unique order identifier
- **orderNumber** (string, required): Short ULID for customer reference
- **lines** (array, required): Order line items with sku, name, unitPrice, qty, lineTotal
- **totals** (object, required): Order totals
  - **items** (number): Total item count
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
- **createdAt** (timestamp): Creation timestamp
- **updatedAt** (timestamp): Last update timestamp

## State Management
Order state is managed via `entity.meta.state`:
- WAITING_TO_FULFILL: Order created, awaiting fulfillment
- PICKING: Items being picked
- WAITING_TO_SEND: Ready for shipment
- SENT: Shipped
- DELIVERED: Delivered to customer

## Relationships
- Created from Cart after successful Payment
- Associated with single Shipment
- References Product entities via line item SKUs

## Business Rules
- Created only from PAID payments
- Snapshots cart data at creation time
- Decrements Product.quantityAvailable on creation
- Single shipment per order
- Order number is short ULID format
