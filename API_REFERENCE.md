# OMS API Reference

## Base URL
```
http://localhost:8080/ui
```

## Product Endpoints

### Search Products
```
GET /ui/products?search=&category=&minPrice=&maxPrice=&page=0&pageSize=10
```
**Response**: Page of ProductSlimDTO
```json
{
  "content": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop",
      "description": "High-performance gaming laptop",
      "price": 1299.99,
      "quantityAvailable": 50,
      "category": "electronics",
      "imageUrl": "https://..."
    }
  ],
  "totalElements": 100,
  "totalPages": 10
}
```

### Get Product Details
```
GET /ui/products/{sku}
```
**Response**: Full Product entity with all nested structures

---

## Cart Endpoints

### Create Cart
```
POST /ui/cart
```
**Response**:
```json
{
  "entity": {
    "cartId": "uuid-1234",
    "lines": [],
    "totalItems": 0,
    "grandTotal": 0.0
  },
  "meta": {
    "id": "technical-uuid",
    "state": "initial"
  }
}
```

### Get Cart
```
GET /ui/cart/{cartId}
```

### Add Item to Cart
```
POST /ui/cart/{cartId}/lines
Content-Type: application/json

{
  "sku": "LAPTOP-001",
  "name": "Gaming Laptop",
  "price": 1299.99,
  "qty": 1
}
```
**Note**: Automatically recalculates totals

### Update/Remove Item
```
PATCH /ui/cart/{cartId}/lines
Content-Type: application/json

{
  "sku": "LAPTOP-001",
  "qty": 2
}
```
**Note**: Set qty=0 to remove item

### Open Checkout
```
POST /ui/cart/{cartId}/open-checkout
```
**Transitions cart to CHECKING_OUT state**

---

## Checkout Endpoint

### Add Guest Contact (via Cart Update)
```
PUT /ui/cart/{cartId}?transition=checkout
Content-Type: application/json

{
  "cartId": "uuid-1234",
  "lines": [...],
  "totalItems": 1,
  "grandTotal": 1299.99,
  "guestContact": {
    "name": "John Doe",
    "email": "john@example.com",
    "phone": "+1-555-0123",
    "address": {
      "line1": "123 Main St",
      "city": "New York",
      "postcode": "10001",
      "country": "USA"
    }
  }
}
```

---

## Payment Endpoints

### Start Dummy Payment
```
POST /ui/payment/start
Content-Type: application/json

{
  "cartId": "uuid-1234",
  "amount": 1299.99
}
```
**Response**:
```json
{
  "paymentId": "PAY-uuid-5678"
}
```
**Note**: Auto-transitions to PAID after ~3 seconds

### Get Payment Status
```
GET /ui/payment/{paymentId}
```
**Response**: Payment entity with state (INITIATED, PAID, FAILED, CANCELED)

---

## Order Endpoints

### Create Order from Paid Payment
```
POST /ui/order/create
Content-Type: application/json

{
  "paymentId": "PAY-uuid-5678",
  "cartId": "uuid-1234"
}
```
**Response**:
```json
{
  "orderId": "ORD-uuid-9999",
  "orderNumber": "ORD-1729610400000",
  "status": "waiting_to_fulfill"
}
```
**Side Effects**:
- Snapshots cart lines and guest contact into Order
- Decrements Product.quantityAvailable for each item
- Creates Shipment in PICKING state

### Get Order Details
```
GET /ui/order/{orderId}
```
**Response**: Full Order entity with lines, totals, and guest contact

---

## Complete Happy Path Example

```bash
# 1. Create cart
CART_ID=$(curl -X POST http://localhost:8080/ui/cart | jq -r '.entity.cartId')

# 2. Add item
curl -X POST http://localhost:8080/ui/cart/$CART_ID/lines \
  -H "Content-Type: application/json" \
  -d '{"sku":"LAPTOP-001","name":"Gaming Laptop","price":1299.99,"qty":1}'

# 3. Get cart to verify totals
curl http://localhost:8080/ui/cart/$CART_ID

# 4. Open checkout
curl -X POST http://localhost:8080/ui/cart/$CART_ID/open-checkout

# 5. Start payment
PAYMENT_ID=$(curl -X POST http://localhost:8080/ui/payment/start \
  -H "Content-Type: application/json" \
  -d "{\"cartId\":\"$CART_ID\",\"amount\":1299.99}" | jq -r '.paymentId')

# 6. Wait 3 seconds for auto-approval
sleep 3

# 7. Verify payment is PAID
curl http://localhost:8080/ui/payment/$PAYMENT_ID

# 8. Create order
ORDER=$(curl -X POST http://localhost:8080/ui/order/create \
  -H "Content-Type: application/json" \
  -d "{\"paymentId\":\"$PAYMENT_ID\",\"cartId\":\"$CART_ID\"}")

ORDER_ID=$(echo $ORDER | jq -r '.orderId')

# 9. Get order details
curl http://localhost:8080/ui/order/$ORDER_ID
```

---

## Error Responses

All errors return RFC 7807 ProblemDetail format:
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Failed to create cart: ..."
}
```

Common HTTP Status Codes:
- 200: Success
- 201: Created (with Location header)
- 204: No Content (delete success)
- 400: Bad Request
- 404: Not Found
- 409: Conflict (duplicate business ID)
- 500: Internal Server Error

