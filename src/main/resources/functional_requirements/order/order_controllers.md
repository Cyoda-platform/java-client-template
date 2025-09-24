# Order Controller Requirements

## Overview
OrderController manages REST API endpoints for order creation, processing, and status tracking.

## Endpoints

### GET /api/orders
**Purpose**: Retrieve all orders with optional filtering
**Request**: `GET /api/orders?customerId=cust-001&status=processing`
**Response**:
```json
[
  {
    "entity": {
      "orderId": "order-001",
      "customerId": "cust-001",
      "petId": "pet-001",
      "orderType": "adoption",
      "totalAmount": 275.0,
      "adoptionFee": 250.0,
      "additionalFees": 25.0,
      "orderDate": "2024-01-15T10:30:00"
    },
    "meta": {
      "uuid": "uuid-789",
      "state": "processing"
    }
  }
]
```

### GET /api/orders/{id}
**Purpose**: Retrieve specific order by ID
**Request**: `GET /api/orders/order-001`
**Response**: Single order object with metadata

### POST /api/orders
**Purpose**: Create new order
**Request**:
```json
{
  "orderId": "order-002",
  "customerId": "cust-001",
  "petId": "pet-002",
  "orderType": "purchase",
  "adoptionFee": 300.0,
  "additionalFees": 50.0,
  "notes": "Customer prefers weekend pickup"
}
```
**Response**: Created order with metadata

### PUT /api/orders/{id}
**Purpose**: Update order with optional state transition
**Request**: `PUT /api/orders/order-001?transition=start_processing`
**Body**: Updated order data
**Response**: Updated order with new state
