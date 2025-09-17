# Payment Controller Requirements

## PaymentController

### POST /ui/payment/start
Start dummy payment for cart.

**Request Example:**
```
POST /ui/payment/start
Content-Type: application/json

{
  "cartId": "cart_01J8X9Y2Z3A4B5C6D7E8F9"
}
```

**Response Example:**
```json
{
  "paymentId": "pay_01J8X9Y2Z3A4B5C6D7E8F9",
  "cartId": "cart_01J8X9Y2Z3A4B5C6D7E8F9",
  "amount": 2599.98,
  "status": "INITIATED",
  "provider": "DUMMY",
  "createdAt": "2025-09-17T10:10:00Z"
}
```

### GET /ui/payment/{paymentId}
Poll payment status.

**Request Example:**
```
GET /ui/payment/pay_01J8X9Y2Z3A4B5C6D7E8F9
```

**Response Example (after 3 seconds):**
```json
{
  "paymentId": "pay_01J8X9Y2Z3A4B5C6D7E8F9",
  "cartId": "cart_01J8X9Y2Z3A4B5C6D7E8F9",
  "amount": 2599.98,
  "status": "PAID",
  "provider": "DUMMY",
  "createdAt": "2025-09-17T10:10:00Z",
  "updatedAt": "2025-09-17T10:10:03Z"
}
```
