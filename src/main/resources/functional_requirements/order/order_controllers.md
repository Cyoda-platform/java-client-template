# Order Controller Requirements

## Overview
OrderController manages REST API endpoints for order operations in the Purrfect Pets store.

## Endpoints

### GET /api/orders
Get all orders
**Response**: List of Order entities with metadata

### GET /api/orders/{orderId}
Get specific order by ID
**Response**: Order entity with metadata

### GET /api/orders/customer/{customerId}
Get all orders for a specific customer
**Response**: List of Order entities with metadata

### POST /api/orders
Create new order
**Request**: Order entity data
**Response**: Created Order entity with metadata

### PUT /api/orders/{orderId}
Update order with optional state transition
**Request**: 
```json
{
  "order": { "customerId": "CUST001", "petId": "PET001", "totalAmount": 250.0, "adoptionFee": 200.0 },
  "transitionName": "process_payment"
}
```
**Response**: Updated Order entity with metadata

### DELETE /api/orders/{orderId}
Cancel order
**Request**: Empty body with transition
**Response**: Updated Order entity with metadata

## Transition Names
- create_order (automatic)
- process_payment
- confirm_order
- complete_order
- cancel_pending_order
- cancel_paid_order
