# Controller Requirements

## Overview
This document defines the REST API controllers for the Cyoda OMS Backend system. All endpoints are under `/ui/**` path and handle UI-facing operations without browser authentication.

## Controllers

### 1. ProductController
**Base Path:** `/ui/products`
**Description:** Manages product catalog operations for the UI.

#### Endpoints

**GET /ui/products**
- **Description:** Search and filter products with pagination
- **Parameters:**
  - `search` (optional): Free-text search on name/description
  - `category` (optional): Filter by product category
  - `minPrice` (optional): Minimum price filter
  - `maxPrice` (optional): Maximum price filter
  - `page` (optional, default=0): Page number
  - `pageSize` (optional, default=20): Items per page
- **Response:** Slim product list DTO for performance
- **Transition:** None (read-only operation)

**Request Example:**
```
GET /ui/products?search=laptop&category=electronics&minPrice=500&maxPrice=2000&page=0&pageSize=10
```

**Response Example:**
```json
{
  "content": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "description": "High-performance gaming laptop",
      "price": 1299.99,
      "quantityAvailable": 15,
      "category": "electronics",
      "imageUrl": "https://example.com/images/laptop-001.jpg"
    }
  ],
  "page": 0,
  "pageSize": 10,
  "totalElements": 25,
  "totalPages": 3
}
```

**GET /ui/products/{sku}**
- **Description:** Get full product details by SKU
- **Parameters:**
  - `sku` (path): Product SKU
- **Response:** Complete product document with full schema
- **Transition:** None (read-only operation)

**Request Example:**
```
GET /ui/products/LAPTOP-001
```

**Response Example:**
```json
{
  "sku": "LAPTOP-001",
  "name": "Gaming Laptop Pro",
  "description": "High-performance gaming laptop with RTX graphics",
  "price": 1299.99,
  "quantityAvailable": 15,
  "category": "electronics",
  "warehouseId": "WH-001",
  "attributes": {
    "brand": "TechBrand",
    "model": "Pro-X1",
    "dimensions": { "l": 35, "w": 25, "h": 2.5, "unit": "cm" },
    "weight": { "value": 2.1, "unit": "kg" }
  },
  "media": [
    {
      "type": "image",
      "url": "https://example.com/images/laptop-001.jpg",
      "alt": "Gaming Laptop Pro",
      "tags": ["hero"]
    }
  ]
}
```

### 2. CartController
**Base Path:** `/ui/cart`
**Description:** Manages shopping cart operations.

#### Endpoints

**POST /ui/cart**
- **Description:** Create new cart or return existing cart
- **Request Body:** Empty or cart initialization data
- **Response:** Cart entity
- **Transition:** `create_on_first_add` (if first item added)

**Request Example:**
```
POST /ui/cart
Content-Type: application/json

{}
```

**Response Example:**
```json
{
  "cartId": "cart-01HGW2E8K9QR5T3N7M6P4S2A1Z",
  "lines": [],
  "totalItems": 0,
  "grandTotal": 0.0,
  "guestContact": null,
  "createdAt": "2025-09-03T10:00:00Z",
  "updatedAt": "2025-09-03T10:00:00Z"
}
```

**POST /ui/cart/{cartId}/lines**
- **Description:** Add or increment item in cart
- **Parameters:**
  - `cartId` (path): Cart identifier
- **Request Body:** Line item with SKU and quantity
- **Response:** Updated cart entity
- **Transition:** `add_item`

**Request Example:**
```
POST /ui/cart/cart-01HGW2E8K9QR5T3N7M6P4S2A1Z/lines
Content-Type: application/json

{
  "sku": "LAPTOP-001",
  "qty": 1
}
```

**Response Example:**
```json
{
  "cartId": "cart-01HGW2E8K9QR5T3N7M6P4S2A1Z",
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
  "guestContact": null,
  "createdAt": "2025-09-03T10:00:00Z",
  "updatedAt": "2025-09-03T10:05:00Z"
}
```

**PATCH /ui/cart/{cartId}/lines**
- **Description:** Update item quantity in cart (remove if qty=0)
- **Parameters:**
  - `cartId` (path): Cart identifier
- **Request Body:** Line item with SKU and new quantity
- **Response:** Updated cart entity
- **Transition:** `update_item`

**Request Example:**
```
PATCH /ui/cart/cart-01HGW2E8K9QR5T3N7M6P4S2A1Z/lines
Content-Type: application/json

{
  "sku": "LAPTOP-001",
  "qty": 2
}
```

**POST /ui/cart/{cartId}/open-checkout**
- **Description:** Start checkout process
- **Parameters:**
  - `cartId` (path): Cart identifier
- **Request Body:** Empty
- **Response:** Updated cart entity
- **Transition:** `open_checkout`

**Request Example:**
```
POST /ui/cart/cart-01HGW2E8K9QR5T3N7M6P4S2A1Z/open-checkout
Content-Type: application/json

{}
```

**GET /ui/cart/{cartId}**
- **Description:** Get cart details
- **Parameters:**
  - `cartId` (path): Cart identifier
- **Response:** Cart entity
- **Transition:** None (read-only operation)

### 3. CheckoutController
**Base Path:** `/ui/checkout`
**Description:** Manages anonymous checkout process.

#### Endpoints

**POST /ui/checkout/{cartId}**
- **Description:** Complete checkout with guest contact information
- **Parameters:**
  - `cartId` (path): Cart identifier
- **Request Body:** Guest contact information
- **Response:** Updated cart entity
- **Transition:** `checkout`

**Request Example:**
```
POST /ui/checkout/cart-01HGW2E8K9QR5T3N7M6P4S2A1Z
Content-Type: application/json

{
  "guestContact": {
    "name": "John Doe",
    "email": "john.doe@example.com",
    "phone": "+1234567890",
    "address": {
      "line1": "123 Main Street",
      "city": "New York",
      "postcode": "10001",
      "country": "US"
    }
  }
}
```

**Response Example:**
```json
{
  "cartId": "cart-01HGW2E8K9QR5T3N7M6P4S2A1Z",
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
  "guestContact": {
    "name": "John Doe",
    "email": "john.doe@example.com",
    "phone": "+1234567890",
    "address": {
      "line1": "123 Main Street",
      "city": "New York",
      "postcode": "10001",
      "country": "US"
    }
  },
  "createdAt": "2025-09-03T10:00:00Z",
  "updatedAt": "2025-09-03T10:15:00Z"
}
```

### 4. PaymentController
**Base Path:** `/ui/payment`
**Description:** Manages dummy payment processing.

#### Endpoints

**POST /ui/payment/start**
- **Description:** Start dummy payment process
- **Request Body:** Cart ID for payment
- **Response:** Payment ID for polling
- **Transition:** `auto_mark_paid` (automatic after 3s)

**Request Example:**
```
POST /ui/payment/start
Content-Type: application/json

{
  "cartId": "cart-01HGW2E8K9QR5T3N7M6P4S2A1Z"
}
```

**Response Example:**
```json
{
  "paymentId": "pay-01HGW2E8K9QR5T3N7M6P4S2A1Z"
}
```

**GET /ui/payment/{paymentId}**
- **Description:** Poll payment status
- **Parameters:**
  - `paymentId` (path): Payment identifier
- **Response:** Payment status
- **Transition:** None (read-only operation)

**Request Example:**
```
GET /ui/payment/pay-01HGW2E8K9QR5T3N7M6P4S2A1Z
```

**Response Example:**
```json
{
  "paymentId": "pay-01HGW2E8K9QR5T3N7M6P4S2A1Z",
  "cartId": "cart-01HGW2E8K9QR5T3N7M6P4S2A1Z",
  "amount": 1299.99,
  "status": "PAID",
  "provider": "DUMMY",
  "createdAt": "2025-09-03T10:15:00Z",
  "updatedAt": "2025-09-03T10:15:03Z"
}
```

### 5. OrderController
**Base Path:** `/ui/order`
**Description:** Manages order operations.

#### Endpoints

**POST /ui/order/create**
- **Description:** Create order from paid payment and cart
- **Request Body:** Payment ID and Cart ID
- **Response:** Created order with order number
- **Transition:** `create_order_from_paid`

**Request Example:**
```
POST /ui/order/create
Content-Type: application/json

{
  "paymentId": "pay-01HGW2E8K9QR5T3N7M6P4S2A1Z",
  "cartId": "cart-01HGW2E8K9QR5T3N7M6P4S2A1Z"
}
```

**Response Example:**
```json
{
  "orderId": "order-01HGW2E8K9QR5T3N7M6P4S2A1Z",
  "orderNumber": "01HGW2E8K9QR5T",
  "status": "PICKING",
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
      "city": "New York",
      "postcode": "10001",
      "country": "US"
    }
  },
  "createdAt": "2025-09-03T10:15:05Z",
  "updatedAt": "2025-09-03T10:15:05Z"
}
```

**GET /ui/order/{orderId}**
- **Description:** Get order details for confirmation/status
- **Parameters:**
  - `orderId` (path): Order identifier
- **Response:** Order entity with current status
- **Transition:** None (read-only operation)

**Request Example:**
```
GET /ui/order/order-01HGW2E8K9QR5T3N7M6P4S2A1Z
```

## Error Handling

All controllers should implement consistent error handling:

### Standard Error Response Format
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Cart has no items",
    "timestamp": "2025-09-03T10:00:00Z",
    "path": "/ui/cart/cart-123/open-checkout"
  }
}
```

### Common Error Codes
- `ENTITY_NOT_FOUND`: Requested entity does not exist
- `VALIDATION_ERROR`: Request validation failed
- `BUSINESS_RULE_ERROR`: Business rule violation
- `INSUFFICIENT_STOCK`: Product out of stock
- `INVALID_STATE`: Entity in wrong state for operation
- `PAYMENT_REQUIRED`: Payment not completed
- `INTERNAL_ERROR`: Unexpected system error

## Security Notes
- No browser authentication required
- Server-side Cyoda credentials only
- All endpoints under `/ui/**` path
- Input validation on all requests
- Rate limiting recommended for production
