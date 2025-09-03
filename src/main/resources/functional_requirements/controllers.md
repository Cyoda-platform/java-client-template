# Controller Requirements

This document defines the detailed requirements for all controllers in the Cyoda OMS Backend system.

## Controller Overview

Controllers expose UI-facing REST APIs under `/ui/**` endpoints. The application holds server-side Cyoda credentials and never exposes service tokens to the browser. All controllers handle anonymous access without authentication.

## Controller Structure

Each entity has its own controller:
- **ProductController**: Handles product catalog operations
- **CartController**: Manages shopping cart operations  
- **CheckoutController**: Handles anonymous checkout process
- **PaymentController**: Manages dummy payment processing
- **OrderController**: Handles order creation and retrieval

## 1. ProductController

**Base Path**: `/ui/products`
**Purpose**: Product catalog management and search

### GET /ui/products

**Description**: Search and filter products with pagination
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
  "content": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "description": "High-performance gaming laptop with RTX graphics",
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

### GET /ui/products/{sku}

**Description**: Get full product details by SKU
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
  "warehouseId": "WH-001",
  "attributes": {
    "brand": "TechCorp",
    "model": "GP-2024",
    "dimensions": { "l": 35.5, "w": 25.0, "h": 2.5, "unit": "cm" },
    "weight": { "value": 2.1, "unit": "kg" }
  },
  "localizations": {
    "defaultLocale": "en-GB",
    "content": [
      {
        "locale": "en-GB",
        "name": "Gaming Laptop Pro",
        "description": "High-performance gaming laptop with RTX graphics"
      }
    ]
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

## 2. CartController

**Base Path**: `/ui/cart`
**Purpose**: Shopping cart management

### POST /ui/cart

**Description**: Create new cart or return existing cart
**Transition**: CREATE_ON_FIRST_ADD (if new cart with first item)

**Request Example**:
```json
{
  "sku": "LAPTOP-001",
  "qty": 1
}
```

**Response Example**:
```json
{
  "cartId": "CART-12345",
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
  "createdAt": "2025-09-03T10:00:00Z",
  "updatedAt": "2025-09-03T10:00:00Z"
}
```

### POST /ui/cart/{cartId}/lines

**Description**: Add or increment item in cart
**Transition**: ADD_ITEM
**Parameters**:
- `cartId` (path): Cart identifier

**Request Example**:
```json
{
  "sku": "LAPTOP-001",
  "qty": 2
}
```

**Response Example**:
```json
{
  "cartId": "CART-12345",
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

**Description**: Update or remove item from cart
**Transition**: DECREMENT_ITEM or REMOVE_ITEM
**Parameters**:
- `cartId` (path): Cart identifier

**Request Example**:
```json
{
  "sku": "LAPTOP-001",
  "qty": 0
}
```

**Response Example**:
```json
{
  "cartId": "CART-12345", 
  "lines": [],
  "totalItems": 0,
  "grandTotal": 0.00
}
```

### POST /ui/cart/{cartId}/open-checkout

**Description**: Open checkout process
**Transition**: OPEN_CHECKOUT
**Parameters**:
- `cartId` (path): Cart identifier

**Request Example**:
```
POST /ui/cart/CART-12345/open-checkout
```

**Response Example**:
```json
{
  "cartId": "CART-12345",
  "status": "CHECKING_OUT",
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

**Description**: Get cart details
**Parameters**:
- `cartId` (path): Cart identifier

**Request Example**:
```
GET /ui/cart/CART-12345
```

**Response Example**:
```json
{
  "cartId": "CART-12345",
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
    "email": "john@example.com",
    "address": {
      "line1": "123 Main St",
      "city": "London", 
      "postcode": "SW1A 1AA",
      "country": "UK"
    }
  }
}
```

## 3. CheckoutController

**Base Path**: `/ui/checkout`
**Purpose**: Anonymous checkout process

### POST /ui/checkout/{cartId}

**Description**: Set guest contact information for checkout
**Transition**: null (no state change)
**Parameters**:
- `cartId` (path): Cart identifier

**Request Example**:
```json
{
  "guestContact": {
    "name": "John Doe",
    "email": "john@example.com", 
    "phone": "+44 20 1234 5678",
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
  "cartId": "CART-12345",
  "guestContact": {
    "name": "John Doe",
    "email": "john@example.com",
    "phone": "+44 20 1234 5678", 
    "address": {
      "line1": "123 Main Street",
      "city": "London",
      "postcode": "SW1A 1AA",
      "country": "UK"
    }
  },
  "message": "Guest contact information saved"
}
```

## 4. PaymentController

**Base Path**: `/ui/payment`
**Purpose**: Dummy payment processing

### POST /ui/payment/start

**Description**: Start dummy payment process
**Transition**: START_DUMMY_PAYMENT

**Request Example**:
```json
{
  "cartId": "CART-12345"
}
```

**Response Example**:
```json
{
  "paymentId": "PAY-67890",
  "cartId": "CART-12345",
  "amount": 1299.99,
  "status": "INITIATED",
  "provider": "DUMMY",
  "message": "Payment initiated, will auto-approve in ~3 seconds"
}
```

### GET /ui/payment/{paymentId}

**Description**: Get payment status (for polling)
**Parameters**:
- `paymentId` (path): Payment identifier

**Request Example**:
```
GET /ui/payment/PAY-67890
```

**Response Example**:
```json
{
  "paymentId": "PAY-67890",
  "cartId": "CART-12345", 
  "amount": 1299.99,
  "status": "PAID",
  "provider": "DUMMY",
  "createdAt": "2025-09-03T10:05:00Z",
  "updatedAt": "2025-09-03T10:05:03Z"
}
```

## 5. OrderController

**Base Path**: `/ui/order`
**Purpose**: Order creation and management

### POST /ui/order/create

**Description**: Create order from paid payment
**Transition**: CREATE_ORDER_FROM_PAID

**Request Example**:
```json
{
  "paymentId": "PAY-67890",
  "cartId": "CART-12345"
}
```

**Response Example**:
```json
{
  "orderId": "ORD-ABCDEF",
  "orderNumber": "01J6QZXM2K",
  "status": "PICKING",
  "message": "Order created successfully"
}
```

### GET /ui/order/{orderId}

**Description**: Get order details
**Parameters**:
- `orderId` (path): Order identifier

**Request Example**:
```
GET /ui/order/ORD-ABCDEF
```

**Response Example**:
```json
{
  "orderId": "ORD-ABCDEF",
  "orderNumber": "01J6QZXM2K",
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
    "items": 1,
    "grand": 1299.99
  },
  "guestContact": {
    "name": "John Doe",
    "email": "john@example.com",
    "address": {
      "line1": "123 Main Street", 
      "city": "London",
      "postcode": "SW1A 1AA",
      "country": "UK"
    }
  },
  "createdAt": "2025-09-03T10:05:05Z",
  "updatedAt": "2025-09-03T10:05:05Z"
}
```

## Error Handling

All controllers should return consistent error responses:

```json
{
  "error": "RESOURCE_NOT_FOUND",
  "message": "Cart with ID CART-12345 not found",
  "timestamp": "2025-09-03T10:00:00Z"
}
```

## Security & Configuration

- **No Authentication**: All endpoints are anonymous
- **CORS**: Enable CORS for browser access
- **Rate Limiting**: Consider rate limiting for production
- **Validation**: Validate all input parameters
- **Logging**: Log all API requests for monitoring
