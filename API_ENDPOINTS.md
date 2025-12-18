# OMS Backend API Endpoints

## Product Endpoints

### List Products with Filters
```
GET /ui/products?search=&category=&minPrice=&maxPrice=&page=0&pageSize=50
```
**Response:** Paginated list of ProductSlimDTO (sku, name, description, price, quantityAvailable, category, imageUrl)

### Get Product Detail
```
GET /ui/products/{sku}
```
**Response:** Full Product entity with all schema fields

---

## Cart Endpoints

### Create Cart
```
POST /ui/cart
```
**Response:** New Cart entity with cartId, status=NEW

### Get Cart
```
GET /ui/cart/{cartId}
```
**Response:** Cart entity with current state

### Add/Increment Line Item
```
POST /ui/cart/{cartId}/lines
Content-Type: application/json

{
  "sku": "LAPTOP-PRO-15-2025",
  "name": "ProBook 15 Laptop",
  "price": 1299.99,
  "qty": 1
}
```
**Response:** Updated Cart entity with recalculated totals

### Update/Remove Line Item
```
PATCH /ui/cart/{cartId}/lines
Content-Type: application/json

{
  "sku": "LAPTOP-PRO-15-2025",
  "name": "ProBook 15 Laptop",
  "price": 1299.99,
  "qty": 0  // Set to 0 to remove
}
```
**Response:** Updated Cart entity

### Open Checkout
```
POST /ui/cart/{cartId}/open-checkout
```
**Response:** Cart entity with status=CHECKING_OUT

---

## Checkout Endpoints

### Attach Guest Contact
```
POST /ui/checkout/{cartId}
Content-Type: application/json

{
  "guestContact": {
    "name": "John Doe",
    "email": "john@example.com",
    "phone": "+44 20 7946 0958",
    "address": {
      "line1": "123 Oxford Street",
      "city": "London",
      "postcode": "W1D 2HD",
      "country": "GB"
    }
  }
}
```
**Response:** Updated Cart entity with guest contact attached

---

## Payment Endpoints

### Start Dummy Payment
```
POST /ui/payment/start
Content-Type: application/json

{
  "cartId": "CART-2025-001"
}
```
**Response:** 
```json
{
  "paymentId": "PAY-2025-001"
}
```
**Note:** Payment auto-approves to PAID status after ~3 seconds

### Get Payment Status
```
GET /ui/payment/{paymentId}
```
**Response:** Payment entity with current status (INITIATED, PAID, FAILED, CANCELED)

---

## Order Endpoints

### Create Order from Paid Payment
```
POST /ui/order/create
Content-Type: application/json

{
  "paymentId": "PAY-2025-001",
  "cartId": "CART-2025-001"
}
```
**Response:**
```json
{
  "orderId": "ORD-2025-001",
  "orderNumber": "01ARZ3NDEKTSV4RRFFQ69G5FAV",
  "status": "WAITING_TO_FULFILL"
}
```
**Side Effects:**
- Product quantities decremented
- Shipment created in PICKING state
- Cart marked as CONVERTED

### Get Order
```
GET /ui/order/{orderId}
```
**Response:** Order entity with all lines, totals, and guest contact

---

## Happy Path Example

1. **List Products**
   ```
   GET /ui/products?category=Electronics
   ```

2. **Create Cart**
   ```
   POST /ui/cart
   ```

3. **Add Items to Cart**
   ```
   POST /ui/cart/{cartId}/lines
   ```

4. **Open Checkout**
   ```
   POST /ui/cart/{cartId}/open-checkout
   ```

5. **Attach Guest Contact**
   ```
   POST /ui/checkout/{cartId}
   ```

6. **Start Payment**
   ```
   POST /ui/payment/start
   ```

7. **Wait ~3 seconds for auto-approval**

8. **Create Order**
   ```
   POST /ui/order/create
   ```

9. **Get Order Confirmation**
   ```
   GET /ui/order/{orderId}
   ```

---

## Error Handling

All endpoints return RFC 7807 ProblemDetail on errors:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Failed to create cart: ..."
}
```

Common Status Codes:
- 200: Success
- 201: Created (with Location header)
- 204: No Content (delete)
- 400: Bad Request
- 404: Not Found
- 409: Conflict (duplicate business ID)

