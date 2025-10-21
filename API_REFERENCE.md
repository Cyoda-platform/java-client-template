# OMS API Reference

## Base URL
All API endpoints are prefixed with `/ui/` and support CORS for browser integration.

## Product Endpoints

### Search Products
```http
GET /ui/products?search={text}&category={category}&minPrice={min}&maxPrice={max}&page={page}&size={size}
```
**Description**: Search products with optional filters
**Parameters**:
- `search` (optional): Free-text search on name/description
- `category` (optional): Filter by product category
- `minPrice` (optional): Minimum price filter
- `maxPrice` (optional): Maximum price filter
- `page` (optional): Page number for pagination
- `size` (optional): Page size for pagination

**Response**: Paginated list of slim product DTOs
```json
{
  "content": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop",
      "description": "High-performance gaming laptop",
      "price": 1299.99,
      "quantityAvailable": 15,
      "category": "electronics",
      "imageUrl": "https://example.com/laptop.jpg"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

### Get Product Details
```http
GET /ui/products/{sku}
```
**Description**: Get full product document with complete schema
**Response**: Complete product entity with all attributes, variants, inventory, etc.

### Create Product
```http
POST /ui/products
Content-Type: application/json

{
  "sku": "LAPTOP-001",
  "name": "Gaming Laptop",
  "description": "High-performance gaming laptop",
  "price": 1299.99,
  "quantityAvailable": 15,
  "category": "electronics"
}
```

## Cart Endpoints

### Create Cart
```http
POST /ui/cart
```
**Description**: Create new empty cart
**Response**: Cart entity with generated cartId

### Get Cart
```http
GET /ui/cart/{cartId}
```
**Description**: Get cart details including line items and totals

### Add Item to Cart
```http
POST /ui/cart/{cartId}/lines
Content-Type: application/json

{
  "sku": "LAPTOP-001",
  "qty": 1
}
```
**Description**: Add item to cart or increment existing quantity

### Update Cart Line
```http
PATCH /ui/cart/{cartId}/lines
Content-Type: application/json

{
  "sku": "LAPTOP-001",
  "qty": 2
}
```
**Description**: Set item quantity (remove if qty=0)

### Open Checkout
```http
POST /ui/cart/{cartId}/open-checkout
```
**Description**: Set cart status to CHECKING_OUT

## Checkout Endpoints

### Set Guest Contact
```http
POST /ui/checkout/{cartId}
Content-Type: application/json

{
  "guestContact": {
    "name": "John Doe",
    "email": "john@example.com",
    "phone": "+1-555-0123",
    "address": {
      "line1": "123 Main St",
      "city": "New York",
      "postcode": "10001",
      "country": "US"
    }
  }
}
```
**Description**: Set guest contact information for anonymous checkout

## Payment Endpoints

### Start Payment
```http
POST /ui/payment/start
Content-Type: application/json

{
  "cartId": "CART-12345"
}
```
**Description**: Start dummy payment processing (auto-approves after 3s)
**Response**:
```json
{
  "paymentId": "PAY-67890",
  "status": "INITIATED",
  "amount": 1299.99
}
```

### Get Payment Status
```http
GET /ui/payment/{paymentId}
```
**Description**: Poll payment status (for checking auto-approval)

### Cancel Payment
```http
POST /ui/payment/{paymentId}/cancel
```
**Description**: Cancel payment (only if still INITIATED)

## Order Endpoints

### Create Order
```http
POST /ui/order/create
Content-Type: application/json

{
  "paymentId": "PAY-67890",
  "cartId": "CART-12345"
}
```
**Description**: Create order from paid cart
**Preconditions**: Payment must be PAID
**Response**:
```json
{
  "orderId": "ORD-ABCD1234",
  "orderNumber": "O123456",
  "status": "WAITING_TO_FULFILL"
}
```

### Get Order
```http
GET /ui/order/{orderId}
```
**Description**: Get order details including line items and status

### Update Order Status
```http
PUT /ui/order/{orderId}/status
Content-Type: application/json

{
  "status": "PICKING"
}
```
**Description**: Update order status
**Valid statuses**: PICKING, WAITING_TO_SEND, SENT, DELIVERED

## Shipment Endpoints

### Get Shipment
```http
GET /ui/shipment/{shipmentId}
```
**Description**: Get shipment details

### Get Shipments by Order
```http
GET /ui/shipment/order/{orderId}
```
**Description**: Get all shipments for an order

### Update Shipment Status
```http
PUT /ui/shipment/{shipmentId}/status
Content-Type: application/json

{
  "status": "WAITING_TO_SEND"
}
```
**Description**: Update shipment status
**Valid statuses**: WAITING_TO_SEND, SENT, DELIVERED

### Update Shipment Quantities
```http
PUT /ui/shipment/{shipmentId}/quantities
Content-Type: application/json

{
  "lines": [
    {
      "sku": "LAPTOP-001",
      "qtyPicked": 1,
      "qtyShipped": 1
    }
  ]
}
```
**Description**: Update picked and shipped quantities

## Error Responses

All endpoints return RFC 7807 ProblemDetail responses for errors:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Product not found with SKU: INVALID-SKU",
  "instance": "/ui/cart/CART-123/lines"
}
```

## Status Codes

- `200 OK`: Successful operation
- `201 Created`: Resource created successfully
- `204 No Content`: Successful deletion
- `400 Bad Request`: Invalid request data
- `404 Not Found`: Resource not found
- `409 Conflict`: Resource already exists

## Complete Flow Example

```bash
# 1. Search products
curl "http://localhost:8080/ui/products?category=electronics"

# 2. Create cart
curl -X POST "http://localhost:8080/ui/cart"

# 3. Add item to cart
curl -X POST "http://localhost:8080/ui/cart/CART-12345/lines" \
  -H "Content-Type: application/json" \
  -d '{"sku": "LAPTOP-001", "qty": 1}'

# 4. Open checkout
curl -X POST "http://localhost:8080/ui/cart/CART-12345/open-checkout"

# 5. Set guest contact
curl -X POST "http://localhost:8080/ui/checkout/CART-12345" \
  -H "Content-Type: application/json" \
  -d '{
    "guestContact": {
      "name": "John Doe",
      "email": "john@example.com",
      "address": {
        "line1": "123 Main St",
        "city": "New York",
        "postcode": "10001",
        "country": "US"
      }
    }
  }'

# 6. Start payment
curl -X POST "http://localhost:8080/ui/payment/start" \
  -H "Content-Type: application/json" \
  -d '{"cartId": "CART-12345"}'

# 7. Poll payment status (wait for auto-approval)
curl "http://localhost:8080/ui/payment/PAY-67890"

# 8. Create order
curl -X POST "http://localhost:8080/ui/order/create" \
  -H "Content-Type: application/json" \
  -d '{"paymentId": "PAY-67890", "cartId": "CART-12345"}'

# 9. Track order
curl "http://localhost:8080/ui/order/ORD-ABCD1234"
```

## Notes

- All timestamps are in ISO 8601 format
- All monetary amounts are in decimal format
- Cart and payment IDs are auto-generated
- Order numbers use short ULID format for customer reference
- Stock is automatically decremented on order creation
- Shipments are automatically created for orders
- Payment auto-approves after exactly 3 seconds
