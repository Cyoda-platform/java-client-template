# Shipment Entity Requirements

## Overview
Shipment represents the physical fulfillment of an order.

## Attributes
- **shipmentId** (string, required): Unique shipment identifier
- **orderId** (string, required): Associated order identifier
- **lines** (array, required): Shipment line items
  - **sku** (string): Product SKU
  - **qtyOrdered** (number): Quantity ordered
  - **qtyPicked** (number): Quantity picked
  - **qtyShipped** (number): Quantity shipped
- **createdAt** (timestamp): Creation timestamp
- **updatedAt** (timestamp): Last update timestamp

## State Management
Shipment state is managed via `entity.meta.state`:
- PICKING: Items being picked from warehouse
- WAITING_TO_SEND: Ready for shipment
- SENT: Shipped to customer
- DELIVERED: Delivered to customer

## Relationships
- Associated with Order via orderId
- Single shipment per order for demo

## Business Rules
- Created automatically when order is created
- Single shipment per order (demo simplification)
- Shipment state drives order state
- Tracks picking and shipping quantities
