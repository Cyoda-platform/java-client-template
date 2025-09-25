# Order Controller API Specifications

## OrderController

### POST /ui/order/create
**Purpose**: Create order from paid cart
**Transition**: CREATE_ORDER (automatic)

**Request Example**:
```json
{
  "paymentId": "payment-456",
  "cartId": "cart-123"
}
```

**Response Example**:
```json
{
  "orderId": "order-789",
  "orderNumber": "01J8XYZABC",
  "status": "WAITING_TO_FULFILL",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop",
      "unitPrice": 1299.99,
      "qty": 2,
      "lineTotal": 2599.98
    }
  ],
  "totals": {
    "items": 2599.98,
    "grand": 2599.98
  },
  "guestContact": {
    "name": "John Doe",
    "email": "john@example.com",
    "address": {
      "line1": "123 Main St",
      "city": "London",
      "postcode": "SW1A 1AA",
      "country": "UK"
    }
  },
  "createdAt": "2025-09-25T10:06:00Z"
}
```

### GET /ui/order/{orderId}
**Purpose**: Get order details

### POST /ui/order/{orderId}/pick
**Purpose**: Start order picking
**Transition**: START_PICKING

### POST /ui/order/{orderId}/ship
**Purpose**: Ship order
**Transition**: SHIP_ORDER

### POST /ui/order/{orderId}/deliver
**Purpose**: Mark order as delivered
**Transition**: DELIVER_ORDER
