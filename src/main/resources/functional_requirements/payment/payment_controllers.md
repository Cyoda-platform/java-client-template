# Payment Controller API Specifications

## PaymentController

### POST /ui/payment/start
**Purpose**: Start dummy payment
**Transition**: START_PAYMENT (automatic)

**Request Example**:
```json
{
  "cartId": "cart-123",
  "amount": 2599.98
}
```

**Response Example**:
```json
{
  "paymentId": "payment-456",
  "cartId": "cart-123",
  "amount": 2599.98,
  "status": "INITIATED",
  "provider": "DUMMY",
  "createdAt": "2025-09-25T10:05:00Z"
}
```

### GET /ui/payment/{paymentId}
**Purpose**: Get payment status for polling

**Request Example**:
```
GET /ui/payment/payment-456
```

**Response Example**:
```json
{
  "paymentId": "payment-456",
  "cartId": "cart-123",
  "amount": 2599.98,
  "status": "PAID",
  "provider": "DUMMY",
  "updatedAt": "2025-09-25T10:05:03Z"
}
```

### POST /ui/payment/{paymentId}/approve
**Purpose**: Manually trigger payment approval (for testing)
**Transition**: AUTO_APPROVE_PAYMENT

**Request Example**:
```json
{
  "transition": "AUTO_APPROVE_PAYMENT"
}
```
