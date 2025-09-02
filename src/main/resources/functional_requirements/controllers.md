# Controller Requirements

## Overview
This document defines the controller requirements for the Cyoda OMS Backend system. Controllers expose REST APIs under `/ui/**` for the browser UI, with no authentication required.

## ProductController

### GET /ui/products
**Purpose**: List products with filtering and pagination
**Parameters**:
- `search` (optional): Free-text search on name/description
- `category` (optional): Filter by product category
- `minPrice` (optional): Minimum price filter
- `maxPrice` (optional): Maximum price filter
- `page` (optional, default=0): Page number
- `pageSize` (optional, default=20): Items per page

**Request Example**:
```
GET /ui/products?search=laptop&category=electronics&minPrice=500&maxPrice=2000&page=0&pageSize=10
```

**Response Example**:
```json
{
  "products": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "description": "High-performance gaming laptop",
      "price": 1299.99,
      "quantityAvailable": 15,
      "category": "electronics",
      "imageUrl": "https://example.com/laptop.jpg"
    }
  ],
  "pagination": {
    "page": 0,
    "pageSize": 10,
    "totalElements": 25,
    "totalPages": 3
  }
}
```

### GET /ui/products/{sku}
**Purpose**: Get full product details by SKU
**Parameters**:
- `sku` (path): Product SKU

**Request Example**:
```
GET /ui/products/LAPTOP-001
```

**Response Example**:
```json
{
  "sku": "LAPTOP-001",
  "name": "Gaming Laptop Pro",
  "description": "High-performance gaming laptop with RTX graphics",
  "price": 1299.99,
  "quantityAvailable": 15,
  "category": "electronics",
  "warehouseId": "LON-01",
  "attributes": {
    "brand": "TechBrand",
    "model": "Pro-X1",
    "dimensions": { "l": 35, "w": 25, "h": 2.5, "unit": "cm" },
    "weight": { "value": 2.1, "unit": "kg" }
  },
  "media": [
    { "type": "image", "url": "https://example.com/laptop.jpg", "alt": "Gaming Laptop", "tags": ["hero"] }
  ]
}
```

## CartController

### POST /ui/cart
**Purpose**: Create new cart or return existing cart
**Transition**: create_on_first_add (none → new), activate_cart (new → active)

**Request Example**:
```
POST /ui/cart
Content-Type: application/json

{}
```

**Response Example**:
```json
{
  "cartId": "cart_01HZXYZ123",
  "lines": [],
  "totalItems": 0,
  "grandTotal": 0.0,
  "state": "active"
}
```

### POST /ui/cart/{cartId}/lines
**Purpose**: Add item to cart
**Transition**: add_item (active → active)
**Parameters**:
- `cartId` (path): Cart identifier

**Request Example**:
```
POST /ui/cart/cart_01HZXYZ123/lines
Content-Type: application/json

{
  "sku": "LAPTOP-001",
  "qty": 2
}
```

**Response Example**:
```json
{
  "cartId": "cart_01HZXYZ123",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "price": 1299.99,
      "qty": 2
    }
  ],
  "totalItems": 2,
  "grandTotal": 2599.98
}
```

### PATCH /ui/cart/{cartId}/lines
**Purpose**: Update item quantity in cart
**Transition**: update_item (active → active) or remove_item (active → active)
**Parameters**:
- `cartId` (path): Cart identifier

**Request Example**:
```
PATCH /ui/cart/cart_01HZXYZ123/lines
Content-Type: application/json

{
  "sku": "LAPTOP-001",
  "qty": 1
}
```

**Response Example**:
```json
{
  "cartId": "cart_01HZXYZ123",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro", 
      "price": 1299.99,
      "qty": 1
    }
  ],
  "totalItems": 1,
  "grandTotal": 1299.99
}
```

### POST /ui/cart/{cartId}/open-checkout
**Purpose**: Open checkout process
**Transition**: open_checkout (active → checking_out)
**Parameters**:
- `cartId` (path): Cart identifier

**Request Example**:
```
POST /ui/cart/cart_01HZXYZ123/open-checkout
Content-Type: application/json

{}
```

**Response Example**:
```json
{
  "cartId": "cart_01HZXYZ123",
  "state": "checking_out",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "price": 1299.99,
      "qty": 1
    }
  ],
  "totalItems": 1,
  "grandTotal": 1299.99
}
```

### GET /ui/cart/{cartId}
**Purpose**: Get cart details
**Parameters**:
- `cartId` (path): Cart identifier

**Request Example**:
```
GET /ui/cart/cart_01HZXYZ123
```

**Response Example**:
```json
{
  "cartId": "cart_01HZXYZ123",
  "state": "active",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "price": 1299.99,
      "qty": 1
    }
  ],
  "totalItems": 1,
  "grandTotal": 1299.99,
  "guestContact": null
}
```

## CheckoutController

### POST /ui/checkout/{cartId}
**Purpose**: Attach guest contact information to cart
**Transition**: checkout (checking_out → converted)
**Parameters**:
- `cartId` (path): Cart identifier

**Request Example**:
```
POST /ui/checkout/cart_01HZXYZ123
Content-Type: application/json

{
  "guestContact": {
    "name": "John Doe",
    "email": "john.doe@example.com",
    "phone": "+1234567890",
    "address": {
      "line1": "123 Main Street",
      "city": "London",
      "postcode": "SW1A 1AA",
      "country": "UK"
    }
  }
}
```

**Response Example**:
```json
{
  "cartId": "cart_01HZXYZ123",
  "state": "converted",
  "guestContact": {
    "name": "John Doe",
    "email": "john.doe@example.com",
    "phone": "+1234567890",
    "address": {
      "line1": "123 Main Street",
      "city": "London", 
      "postcode": "SW1A 1AA",
      "country": "UK"
    }
  },
  "grandTotal": 1299.99
}
```

## PaymentController

### POST /ui/payment/start
**Purpose**: Start dummy payment process
**Transition**: start_dummy_payment (none → initiated)

**Request Example**:
```
POST /ui/payment/start
Content-Type: application/json

{
  "cartId": "cart_01HZXYZ123"
}
```

**Response Example**:
```json
{
  "paymentId": "pay_01HZXYZ456",
  "cartId": "cart_01HZXYZ123",
  "amount": 1299.99,
  "status": "initiated",
  "provider": "DUMMY"
}
```

### GET /ui/payment/{paymentId}
**Purpose**: Poll payment status
**Parameters**:
- `paymentId` (path): Payment identifier

**Request Example**:
```
GET /ui/payment/pay_01HZXYZ456
```

**Response Example**:
```json
{
  "paymentId": "pay_01HZXYZ456",
  "cartId": "cart_01HZXYZ123",
  "amount": 1299.99,
  "status": "paid",
  "provider": "DUMMY",
  "createdAt": "2025-09-02T10:00:00Z",
  "updatedAt": "2025-09-02T10:00:03Z"
}
```

### POST /ui/payment/{paymentId}/cancel
**Purpose**: Cancel payment
**Transition**: mark_canceled (initiated → canceled)
**Parameters**:
- `paymentId` (path): Payment identifier

**Request Example**:
```
POST /ui/payment/pay_01HZXYZ456/cancel
Content-Type: application/json

{}
```

**Response Example**:
```json
{
  "paymentId": "pay_01HZXYZ456",
  "status": "canceled"
}
```

## OrderController

### POST /ui/order/create
**Purpose**: Create order from paid payment
**Transition**: create_order_from_paid (none → waiting_to_fulfill)

**Request Example**:
```
POST /ui/order/create
Content-Type: application/json

{
  "paymentId": "pay_01HZXYZ456",
  "cartId": "cart_01HZXYZ123"
}
```

**Response Example**:
```json
{
  "orderId": "ord_01HZXYZ789",
  "orderNumber": "01HZXYZ789",
  "status": "waiting_to_fulfill",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "unitPrice": 1299.99,
      "qty": 1,
      "lineTotal": 1299.99
    }
  ],
  "totals": {
    "items": 1299.99,
    "grand": 1299.99
  },
  "guestContact": {
    "name": "John Doe",
    "email": "john.doe@example.com",
    "address": {
      "line1": "123 Main Street",
      "city": "London",
      "postcode": "SW1A 1AA",
      "country": "UK"
    }
  }
}
```

### GET /ui/order/{orderId}
**Purpose**: Get order details for confirmation/status
**Parameters**:
- `orderId` (path): Order identifier

**Request Example**:
```
GET /ui/order/ord_01HZXYZ789
```

**Response Example**:
```json
{
  "orderId": "ord_01HZXYZ789",
  "orderNumber": "01HZXYZ789",
  "status": "picking",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "unitPrice": 1299.99,
      "qty": 1,
      "lineTotal": 1299.99
    }
  ],
  "totals": {
    "items": 1299.99,
    "grand": 1299.99
  },
  "guestContact": {
    "name": "John Doe",
    "email": "john.doe@example.com",
    "address": {
      "line1": "123 Main Street",
      "city": "London",
      "postcode": "SW1A 1AA",
      "country": "UK"
    }
  },
  "createdAt": "2025-09-02T10:00:05Z",
  "updatedAt": "2025-09-02T10:05:00Z"
}
```

### POST /ui/order/{orderId}/start-picking
**Purpose**: Start order picking process
**Transition**: start_picking (waiting_to_fulfill → picking)
**Parameters**:
- `orderId` (path): Order identifier

**Request Example**:
```
POST /ui/order/ord_01HZXYZ789/start-picking
Content-Type: application/json

{}
```

**Response Example**:
```json
{
  "orderId": "ord_01HZXYZ789",
  "status": "picking"
}
```

### POST /ui/order/{orderId}/ready-to-send
**Purpose**: Mark order ready for shipment
**Transition**: ready_to_send (picking → waiting_to_send)
**Parameters**:
- `orderId` (path): Order identifier

**Request Example**:
```
POST /ui/order/ord_01HZXYZ789/ready-to-send
Content-Type: application/json

{}
```

**Response Example**:
```json
{
  "orderId": "ord_01HZXYZ789",
  "status": "waiting_to_send"
}
```

### POST /ui/order/{orderId}/mark-sent
**Purpose**: Mark order as sent
**Transition**: mark_sent (waiting_to_send → sent)
**Parameters**:
- `orderId` (path): Order identifier

**Request Example**:
```
POST /ui/order/ord_01HZXYZ789/mark-sent
Content-Type: application/json

{}
```

**Response Example**:
```json
{
  "orderId": "ord_01HZXYZ789",
  "status": "sent"
}
```

### POST /ui/order/{orderId}/mark-delivered
**Purpose**: Mark order as delivered
**Transition**: mark_delivered (sent → delivered)
**Parameters**:
- `orderId` (path): Order identifier

**Request Example**:
```
POST /ui/order/ord_01HZXYZ789/mark-delivered
Content-Type: application/json

{}
```

**Response Example**:
```json
{
  "orderId": "ord_01HZXYZ789",
  "status": "delivered"
}
```

## ShipmentController

### GET /ui/shipment/order/{orderId}
**Purpose**: Get shipment details by order ID
**Parameters**:
- `orderId` (path): Order identifier

**Request Example**:
```
GET /ui/shipment/order/ord_01HZXYZ789
```

**Response Example**:
```json
{
  "shipmentId": "ship_01HZXYZ999",
  "orderId": "ord_01HZXYZ789",
  "status": "picking",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "qtyOrdered": 1,
      "qtyPicked": 0,
      "qtyShipped": 0
    }
  ],
  "createdAt": "2025-09-02T10:00:05Z",
  "updatedAt": "2025-09-02T10:00:05Z"
}
```

### POST /ui/shipment/{shipmentId}/ready-to-send
**Purpose**: Mark shipment ready for dispatch
**Transition**: ready_to_send (picking → waiting_to_send)
**Parameters**:
- `shipmentId` (path): Shipment identifier

**Request Example**:
```
POST /ui/shipment/ship_01HZXYZ999/ready-to-send
Content-Type: application/json

{}
```

**Response Example**:
```json
{
  "shipmentId": "ship_01HZXYZ999",
  "status": "waiting_to_send"
}
```

### POST /ui/shipment/{shipmentId}/mark-sent
**Purpose**: Mark shipment as dispatched
**Transition**: mark_sent (waiting_to_send → sent)
**Parameters**:
- `shipmentId` (path): Shipment identifier

**Request Example**:
```
POST /ui/shipment/ship_01HZXYZ999/mark-sent
Content-Type: application/json

{}
```

**Response Example**:
```json
{
  "shipmentId": "ship_01HZXYZ999",
  "status": "sent"
}
```

### POST /ui/shipment/{shipmentId}/mark-delivered
**Purpose**: Mark shipment as delivered
**Transition**: mark_delivered (sent → delivered)
**Parameters**:
- `shipmentId` (path): Shipment identifier

**Request Example**:
```
POST /ui/shipment/ship_01HZXYZ999/mark-delivered
Content-Type: application/json

{}
```

**Response Example**:
```json
{
  "shipmentId": "ship_01HZXYZ999",
  "status": "delivered"
}
```

## Error Responses

All endpoints return consistent error responses:

**400 Bad Request Example**:
```json
{
  "error": "INVALID_REQUEST",
  "message": "Cart is not in active state",
  "timestamp": "2025-09-02T10:00:00Z"
}
```

**404 Not Found Example**:
```json
{
  "error": "NOT_FOUND",
  "message": "Cart not found: cart_01INVALID",
  "timestamp": "2025-09-02T10:00:00Z"
}
```

**409 Conflict Example**:
```json
{
  "error": "INSUFFICIENT_STOCK",
  "message": "Insufficient stock for product LAPTOP-001",
  "timestamp": "2025-09-02T10:00:00Z"
}
```

## Implementation Notes

### State Validation
- All update endpoints must validate current entity state before applying transitions
- Return appropriate error responses for invalid state transitions
- Use transition names exactly as defined in workflow documentation

### Product Search Implementation
- Use Cyoda condition queries for filtering
- Free-text search: `CONTAINS` on `$.name` OR `$.description`
- Category filter: `EQUALS` on `$.category`
- Price range: `$.price >= minPrice` AND/OR `$.price <= maxPrice`
- Combine filters with `AND` logic

### Response Consistency
- Always include entity state in responses where applicable
- Use consistent field naming across all endpoints
- Include timestamps for audit trail
- Return full entity data for GET operations, minimal data for state changes

### Security Considerations
- No authentication required for `/ui/**` endpoints
- Server-side validation of all input data
- Rate limiting should be implemented for production use
- Input sanitization for search parameters
