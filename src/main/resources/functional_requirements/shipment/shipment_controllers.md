# Shipment Controller Requirements

## ShipmentController

### GET /ui/shipment/{shipmentId}
Get shipment details for tracking.

**Request Example:**
```
GET /ui/shipment/ship_01J8X9Y2Z3A4B5C6D7E8F9
```

**Response Example:**
```json
{
  "shipmentId": "ship_01J8X9Y2Z3A4B5C6D7E8F9",
  "orderId": "order_01J8X9Y2Z3A4B5C6D7E8F9",
  "status": "SENT",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "qtyOrdered": 2,
      "qtyPicked": 2,
      "qtyShipped": 2
    }
  ],
  "createdAt": "2025-09-17T10:15:00Z",
  "updatedAt": "2025-09-17T10:25:00Z"
}
```

### GET /ui/order/{orderId}/shipment
Get shipment for specific order.

**Request Example:**
```
GET /ui/order/order_01J8X9Y2Z3A4B5C6D7E8F9/shipment
```

**Response Example:**
```json
{
  "shipmentId": "ship_01J8X9Y2Z3A4B5C6D7E8F9",
  "orderId": "order_01J8X9Y2Z3A4B5C6D7E8F9",
  "status": "DELIVERED",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "qtyOrdered": 2,
      "qtyPicked": 2,
      "qtyShipped": 2
    }
  ],
  "createdAt": "2025-09-17T10:15:00Z",
  "updatedAt": "2025-09-17T10:30:00Z"
}
```
