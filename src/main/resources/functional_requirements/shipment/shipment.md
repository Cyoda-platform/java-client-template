# Shipment Entity Requirements

## Overview
Shipment entity manages order fulfillment and delivery tracking with single shipment per order policy.

## Attributes
- **shipmentId**: Unique shipment identifier (required)
- **orderId**: Associated order identifier (required)
- **status**: Shipment state - PICKING, WAITING_TO_SEND, SENT, DELIVERED (required)
- **lines**: Array of shipment lines with sku, qtyOrdered, qtyPicked, qtyShipped (required)
- **createdAt**, **updatedAt**: Timestamps

## Relationships
- Associated with Order entity via orderId
- Tracks fulfillment of order line items

## Business Rules
- Single shipment per order
- Created automatically when order is created
- Status progression drives order status updates
- Tracks picked and shipped quantities per line item
