# OMS API Endpoints Reference

## Product Endpoints

### Search Products
```
GET /ui/products?search=&category=&minPrice=&maxPrice=&page=0&pageSize=50
```
**Description**: Search products with filters
**Query Parameters**:
- `search` (optional): Free-text search on name/description
- `category` (optional): Filter by category
- `minPrice` (optional): Minimum price filter
- `maxPrice` (optional): Maximum price filter
- `page` (optional, default=0): Page number
- `pageSize` (optional, default=50): Items per page

**Response**: PageResult with ProductDTO list (slim view)
```json
{
  "searchId": "uuid",
  "data": [
    {
      "sku": "LAPTOP-PRO-001",
      "name": "Professional Laptop",
      "description": "High-performance laptop",
      "price": 1299.99,
      "quantityAvailable": 150,
      "category": "Electronics",
      "imageUrl": "https://..."
    }
  ],
  "pageNumber": 0,
  "pageSize": 50,
  "totalElements": 100,
  "totalPages": 2
}
```

### Get Product Detail
```
GET /ui/products/{sku}
```
**Description**: Get full product document with all schema fields
**Response**: Full Product entity with all nested objects

---

## Cart Endpoints

### Create Cart
```
POST /ui/cart
```
**Description**: Create new shopping cart
**Response**: EntityWithMetadata<Cart>

### Get Cart
```
GET /ui/cart/{cartId}
```
**Description**: Retrieve cart by ID
**Response**: EntityWithMetadata<Cart>

### Add Item to Cart
```
POST /ui/cart/{cartId}/lines
```
**Body**:
```json
{
  "sku": "LAPTOP-PRO-001",
  "name": "Professional Laptop",
  "price": 1299.99,
  "qty": 1
}
```
**Description**: Add or increment item in cart
**Response**: EntityWithMetadata<Cart>

### Update Cart Line
```
PATCH /ui/cart/{cartId}/lines
```
**Body**:
```json
{
  "sku": "LAPTOP-PRO-001",
  "name": "Professional Laptop",
  "price": 1299.99,
  "qty": 2
}
```
**Description**: Update quantity (qty=0 removes item)
**Response**: EntityWithMetadata<Cart>

### Open Checkout
```
POST /ui/cart/{cartId}/open-checkout
```
**Description**: Transition cart to CHECKING_OUT state
**Response**: EntityWithMetadata<Cart>

---

## Checkout Endpoint

### Checkout with Guest Contact
```
POST /ui/checkout/{cartId}
```
**Body**:
```json
{
  "guestContact": {
    "name": "John Doe",
    "email": "john@example.com",
    "phone": "+44 20 7946 0958",
    "address": {
      "line1": "123 Main Street",
      "city": "London",
      "postcode": "SW1A 1AA",
      "country": "United Kingdom"
    }
  }
}
```
**Description**: Attach guest contact information to cart
**Response**: EntityWithMetadata<Cart>

---

## Payment Endpoints

### Start Dummy Payment
```
POST /ui/payment/start
```
**Body**:
```json
{
  "cartId": "CART-001"
}
```
**Description**: Create payment and auto-approve after ~3 seconds
**Response**:
```json
{
  "paymentId": "PAY-001"
}
```

### Get Payment Status
```
GET /ui/payment/{paymentId}
```
**Description**: Poll payment status
**Response**: EntityWithMetadata<Payment>
```json
{
  "metadata": {
    "id": "uuid",
    "state": "PAID",
    ...
  },
  "entity": {
    "paymentId": "PAY-001",
    "cartId": "CART-001",
    "amount": 1359.97,
    "status": "PAID",
    "provider": "DUMMY",
    ...
  }
}
```

---

## Order Endpoints

### Create Order from Paid Payment
```
POST /ui/order/create
```
**Body**:
```json
{
  "paymentId": "PAY-001",
  "cartId": "CART-001"
}
```
**Description**: Create order from paid payment
- Snapshots cart lines and guest contact
- Decrements product quantities
- Creates shipment in PICKING state
**Response**:
```json
{
  "orderId": "ORD-001",
  "orderNumber": "01ARZ3NDEKTSV4RRFFQ69G5FAV",
  "status": "WAITING_TO_FULFILL"
}
```

### Get Order
```
GET /ui/order/{orderId}
```
**Description**: Retrieve order details
**Response**: EntityWithMetadata<Order>

---

## Complete Happy Path Example

```bash
# 1. Search products
curl -X GET "http://localhost:8080/ui/products?category=Electronics&maxPrice=2000"

# 2. Get product detail
curl -X GET "http://localhost:8080/ui/products/LAPTOP-PRO-001"

# 3. Create cart
CART_ID=$(curl -X POST http://localhost:8080/ui/cart | jq -r '.entity.cartId')

# 4. Add item to cart
curl -X POST "http://localhost:8080/ui/cart/$CART_ID/lines" \
  -H "Content-Type: application/json" \
  -d '{"sku":"LAPTOP-PRO-001","name":"Professional Laptop","price":1299.99,"qty":1}'

# 5. Open checkout
curl -X POST "http://localhost:8080/ui/cart/$CART_ID/open-checkout"

# 6. Checkout with guest contact
curl -X POST "http://localhost:8080/ui/checkout/$CART_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "guestContact": {
      "name": "John Doe",
      "email": "john@example.com",
      "phone": "+44 20 7946 0958",
      "address": {
        "line1": "123 Main Street",
        "city": "London",
        "postcode": "SW1A 1AA",
        "country": "United Kingdom"
      }
    }
  }'

# 7. Start payment
PAYMENT_ID=$(curl -X POST "http://localhost:8080/ui/payment/start" \
  -H "Content-Type: application/json" \
  -d "{\"cartId\":\"$CART_ID\"}" | jq -r '.paymentId')

# 8. Wait for payment to be PAID (auto-approves after ~3 seconds)
sleep 4
curl -X GET "http://localhost:8080/ui/payment/$PAYMENT_ID"

# 9. Create order
ORDER=$(curl -X POST "http://localhost:8080/ui/order/create" \
  -H "Content-Type: application/json" \
  -d "{\"paymentId\":\"$PAYMENT_ID\",\"cartId\":\"$CART_ID\"}")

ORDER_ID=$(echo $ORDER | jq -r '.orderId')

# 10. Get order details
curl -X GET "http://localhost:8080/ui/order/$ORDER_ID"
```

---

## Error Handling

All endpoints return RFC 7807 ProblemDetail on errors:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Failed to create order: Payment not found or not in PAID status"
}
```

Common HTTP Status Codes:
- `200 OK` - Successful GET/POST/PATCH
- `201 Created` - Resource created
- `204 No Content` - Successful DELETE
- `400 Bad Request` - Invalid input or business logic error
- `404 Not Found` - Resource not found
- `409 Conflict` - Duplicate business identifier

