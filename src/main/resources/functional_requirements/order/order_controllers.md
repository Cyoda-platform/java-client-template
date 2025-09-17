# Order Controller Requirements

## OrderController

### POST /ui/order/create
Create order from paid cart.

**Request Example:**
```
POST /ui/order/create
Content-Type: application/json

{
  "paymentId": "pay_01J8X9Y2Z3A4B5C6D7E8F9",
  "cartId": "cart_01J8X9Y2Z3A4B5C6D7E8F9"
}
```

**Response Example:**
```json
{
  "orderId": "order_01J8X9Y2Z3A4B5C6D7E8F9",
  "orderNumber": "ORD-ABC123",
  "status": "WAITING_TO_FULFILL",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "unitPrice": 1299.99,
      "qty": 2,
      "lineTotal": 2599.98
    }
  ],
  "totals": {
    "items": 2,
    "grand": 2599.98
  },
  "guestContact": {
    "name": "John Doe",
    "email": "john@example.com",
    "phone": "+1234567890",
    "address": {
      "line1": "123 Main St",
      "city": "London",
      "postcode": "SW1A 1AA",
      "country": "UK"
    }
  },
  "createdAt": "2025-09-17T10:15:00Z"
}
```

### GET /ui/order/{orderId}
Get order details for confirmation/status.

**Request Example:**
```
GET /ui/order/order_01J8X9Y2Z3A4B5C6D7E8F9
```

**Response Example:**
```json
{
  "orderId": "order_01J8X9Y2Z3A4B5C6D7E8F9",
  "orderNumber": "ORD-ABC123",
  "status": "PICKING",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "unitPrice": 1299.99,
      "qty": 2,
      "lineTotal": 2599.98
    }
  ],
  "totals": {
    "items": 2,
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
  "createdAt": "2025-09-17T10:15:00Z",
  "updatedAt": "2025-09-17T10:20:00Z"
}
```
