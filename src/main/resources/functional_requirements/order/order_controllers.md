# Order Controller Requirements

## Overview
OrderController manages REST endpoints for pet adoption orders and order tracking.

## Endpoints

### GET /orders
List all orders with optional filtering.

**Request Example:**
```
GET /orders?ownerId=owner-123&status=confirmed
```

**Response Example:**
```json
[
  {
    "uuid": "order-123",
    "petId": "pet-456",
    "ownerId": "owner-789",
    "storeId": "store-001",
    "orderDate": "2024-01-15T14:30:00Z",
    "totalAmount": 550.00,
    "adoptionFee": 500.00,
    "serviceFee": 50.00,
    "notes": "Delivery requested",
    "meta": {
      "state": "confirmed",
      "createdAt": "2024-01-15T14:30:00Z"
    }
  }
]
```

### POST /orders
Create a new order.

**Request Example:**
```json
{
  "petId": "pet-456",
  "ownerId": "owner-789",
  "storeId": "store-001",
  "adoptionFee": 500.00,
  "serviceFee": 50.00,
  "notes": "Delivery to home address"
}
```

### PUT /orders/{id}/transition
Execute workflow transition.

**Request Example:**
```json
{
  "transitionName": "confirm_order",
  "data": {
    "paymentMethod": "credit_card",
    "paymentToken": "tok_1234567890"
  }
}
```
