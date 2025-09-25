# Order Entity Requirements

## Overview
Order entity represents confirmed orders created from paid carts with fulfillment tracking.

## Attributes
- **orderId**: Unique order identifier (required)
- **orderNumber**: Short ULID for customer reference (required)
- **status**: Order state - WAITING_TO_FULFILL, PICKING, WAITING_TO_SEND, SENT, DELIVERED (required)
- **lines**: Array of order line items with sku, name, unitPrice, qty, lineTotal (required)
- **totals**: Order totals with items and grand total (required)
- **guestContact**: Customer contact information (required)
  - name (required), email, phone (optional)
  - address with line1, city, postcode, country (required)
- **createdAt**, **updatedAt**: Timestamps

## Relationships
- Created from Cart and Payment entities
- Associated with single Shipment entity
- References Product entities for stock updates

## Business Rules
- Created only from PAID payments
- Snapshots cart data at creation time
- Decrements product stock on creation
- Single shipment per order
- Order number uses short ULID format
