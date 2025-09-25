# Shipment Controller API Specifications

## ShipmentController

### GET /ui/shipment/{shipmentId}
**Purpose**: Get shipment details

**Request Example**:
```
GET /ui/shipment/shipment-101
```

**Response Example**:
```json
{
  "shipmentId": "shipment-101",
  "orderId": "order-789",
  "status": "PICKING",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "qtyOrdered": 2,
      "qtyPicked": 0,
      "qtyShipped": 0
    }
  ],
  "createdAt": "2025-09-25T10:06:00Z"
}
```

### POST /ui/shipment/{shipmentId}/pick
**Purpose**: Complete picking
**Transition**: COMPLETE_PICKING

**Request Example**:
```json
{
  "lines": [
    {
      "sku": "LAPTOP-001",
      "qtyPicked": 2
    }
  ],
  "transition": "COMPLETE_PICKING"
}
```

### POST /ui/shipment/{shipmentId}/dispatch
**Purpose**: Dispatch shipment
**Transition**: DISPATCH_SHIPMENT

### POST /ui/shipment/{shipmentId}/deliver
**Purpose**: Confirm delivery
**Transition**: CONFIRM_DELIVERY
