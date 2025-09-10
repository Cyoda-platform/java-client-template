# Controller Requirements

## Overview
This document defines the detailed requirements for all REST controllers in the Cyoda OMS Backend system. Controllers expose UI-facing APIs under `/ui/**` endpoints and handle all browser interactions without requiring authentication.

**Security Note:** No browser authentication required. Server holds Cyoda credentials and never exposes service tokens to browser.

## 1. ProductController

**Controller Name:** ProductController
**Base Path:** `/ui/products`
**Package:** `com.java_template.application.controller`
**Entity:** Product

### Endpoints

#### 1.1 GET /ui/products - List Products with Filters

**Purpose:** Search and filter products with pagination, returning slim DTOs for performance.

**Query Parameters:**
- `search` (String, optional) - Free-text search on name/description
- `category` (String, optional) - Filter by product category
- `minPrice` (BigDecimal, optional) - Minimum price filter
- `maxPrice` (BigDecimal, optional) - Maximum price filter
- `page` (Integer, optional, default=0) - Page number
- `pageSize` (Integer, optional, default=20) - Page size

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
  "totalElements": 1,
  "totalPages": 1
}
```

#### 1.2 GET /ui/products/{sku} - Get Product Details

**Purpose:** Retrieve complete product document with full schema.

**Path Parameters:**
- `sku` (String, required) - Product SKU

**Request Example:**
```
GET /ui/products/LAPTOP-001
```

**Response Example:**
```json
{
  "sku": "LAPTOP-001",
  "name": "Gaming Laptop Pro",
  "description": "High-performance gaming laptop",
  "price": 1299.99,
  "quantityAvailable": 15,
  "category": "electronics",
  "warehouseId": "LON-01",
  "attributes": {
    "brand": "TechBrand",
    "model": "Pro-X1",
    "dimensions": { "l": 35.5, "w": 25.0, "h": 2.5, "unit": "cm" },
    "weight": { "value": 2.1, "unit": "kg" }
  },
  "localizations": { ... },
  "media": [ ... ],
  "options": { ... },
  "variants": [ ... ],
  "bundles": [ ... ],
  "inventory": { ... },
  "compliance": { ... },
  "relationships": { ... },
  "events": [ ... ]
}
```

## 2. CartController

**Controller Name:** CartController
**Base Path:** `/ui/cart`
**Package:** `com.java_template.application.controller`
**Entity:** Cart

### Endpoints

#### 2.1 POST /ui/cart - Create or Get Cart

**Purpose:** Create new cart or return existing cart for session.

**Request Body:** Empty or cart initialization data

**Request Example:**
```
POST /ui/cart
Content-Type: application/json
{}
```

**Response Example:**
```json
{
  "cartId": "cart-123e4567-e89b-12d3-a456-426614174000",
  "status": "NEW",
  "lines": [],
  "totalItems": 0,
  "grandTotal": 0.00,
  "guestContact": null,
  "createdAt": "2025-09-10T10:00:00Z",
  "updatedAt": "2025-09-10T10:00:00Z"
}
```

#### 2.2 POST /ui/cart/{cartId}/lines - Add/Increment Item

**Purpose:** Add item to cart or increment existing item quantity.

**Path Parameters:**
- `cartId` (String, required) - Cart identifier

**Request Body:**
```json
{
  "sku": "LAPTOP-001",
  "qty": 2
}
```

**Transition:** ADD_ITEM

**Request Example:**
```
POST /ui/cart/cart-123e4567-e89b-12d3-a456-426614174000/lines
Content-Type: application/json

{
  "sku": "LAPTOP-001",
  "qty": 2
}
```

**Response Example:**
```json
{
  "cartId": "cart-123e4567-e89b-12d3-a456-426614174000",
  "status": "ACTIVE",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "price": 1299.99,
      "qty": 2
    }
  ],
  "totalItems": 2,
  "grandTotal": 2599.98,
  "guestContact": null,
  "updatedAt": "2025-09-10T10:05:00Z"
}
```

#### 2.3 PATCH /ui/cart/{cartId}/lines - Update/Remove Item

**Purpose:** Set item quantity or remove item (qty=0).

**Path Parameters:**
- `cartId` (String, required) - Cart identifier

**Request Body:**
```json
{
  "sku": "LAPTOP-001",
  "qty": 1
}
```

**Transition:** UPDATE_ITEM (or REMOVE_ITEM if qty=0)

**Request Example:**
```
PATCH /ui/cart/cart-123e4567-e89b-12d3-a456-426614174000/lines
Content-Type: application/json

{
  "sku": "LAPTOP-001",
  "qty": 1
}
```

#### 2.4 POST /ui/cart/{cartId}/open-checkout - Open Checkout

**Purpose:** Move cart to checkout state.

**Path Parameters:**
- `cartId` (String, required) - Cart identifier

**Transition:** OPEN_CHECKOUT

**Request Example:**
```
POST /ui/cart/cart-123e4567-e89b-12d3-a456-426614174000/open-checkout
```

#### 2.5 GET /ui/cart/{cartId} - Get Cart

**Purpose:** Retrieve current cart state.

**Path Parameters:**
- `cartId` (String, required) - Cart identifier

**Request Example:**
```
GET /ui/cart/cart-123e4567-e89b-12d3-a456-426614174000
```

## 3. CheckoutController

**Controller Name:** CheckoutController
**Base Path:** `/ui/checkout`
**Package:** `com.java_template.application.controller`
**Entity:** Cart

### Endpoints

#### 3.1 POST /ui/checkout/{cartId} - Set Guest Contact

**Purpose:** Attach guest contact information to cart for anonymous checkout.

**Path Parameters:**
- `cartId` (String, required) - Cart identifier

**Request Body:**
```json
{
  "guestContact": {
    "name": "John Doe",
    "email": "john.doe@example.com",
    "phone": "+1-555-0123",
    "address": {
      "line1": "123 Main Street",
      "city": "New York",
      "postcode": "10001",
      "country": "US"
    }
  }
}
```

**Transition:** null (updates cart without state change)

**Request Example:**
```
POST /ui/checkout/cart-123e4567-e89b-12d3-a456-426614174000
Content-Type: application/json

{
  "guestContact": {
    "name": "John Doe",
    "email": "john.doe@example.com",
    "phone": "+1-555-0123",
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
  "cartId": "cart-123e4567-e89b-12d3-a456-426614174000",
  "status": "CHECKING_OUT",
  "guestContact": {
    "name": "John Doe",
    "email": "john.doe@example.com",
    "phone": "+1-555-0123",
    "address": {
      "line1": "123 Main Street",
      "city": "New York",
      "postcode": "10001",
      "country": "US"
    }
  }
}
```

## 4. PaymentController

**Controller Name:** PaymentController
**Base Path:** `/ui/payment`
**Package:** `com.java_template.application.controller`
**Entity:** Payment

### Endpoints

#### 4.1 POST /ui/payment/start - Start Payment

**Purpose:** Create dummy payment and initiate auto-approval process.

**Request Body:**
```json
{
  "cartId": "cart-123e4567-e89b-12d3-a456-426614174000"
}
```

**Transition:** START_DUMMY_PAYMENT

**Request Example:**
```
POST /ui/payment/start
Content-Type: application/json

{
  "cartId": "cart-123e4567-e89b-12d3-a456-426614174000"
}
```

**Response Example:**
```json
{
  "paymentId": "pay-123e4567-e89b-12d3-a456-426614174000",
  "status": "INITIATED",
  "amount": 2599.98,
  "provider": "DUMMY",
  "createdAt": "2025-09-10T10:10:00Z"
}
```

#### 4.2 GET /ui/payment/{paymentId} - Poll Payment Status

**Purpose:** Check payment status for polling-based UI updates.

**Path Parameters:**
- `paymentId` (String, required) - Payment identifier

**Request Example:**
```
GET /ui/payment/pay-123e4567-e89b-12d3-a456-426614174000
```

**Response Example:**
```json
{
  "paymentId": "pay-123e4567-e89b-12d3-a456-426614174000",
  "status": "PAID",
  "amount": 2599.98,
  "provider": "DUMMY",
  "updatedAt": "2025-09-10T10:10:03Z"
}
```

## 5. OrderController

**Controller Name:** OrderController
**Base Path:** `/ui/order`
**Package:** `com.java_template.application.controller`
**Entity:** Order

### Endpoints

#### 5.1 POST /ui/order/create - Create Order

**Purpose:** Create order from paid cart and payment.

**Request Body:**
```json
{
  "paymentId": "pay-123e4567-e89b-12d3-a456-426614174000",
  "cartId": "cart-123e4567-e89b-12d3-a456-426614174000"
}
```

**Transition:** CREATE_ORDER_FROM_PAID

**Preconditions:**
- Payment must be in PAID state
- Cart must be in CONVERTED state

**Request Example:**
```
POST /ui/order/create
Content-Type: application/json

{
  "paymentId": "pay-123e4567-e89b-12d3-a456-426614174000",
  "cartId": "cart-123e4567-e89b-12d3-a456-426614174000"
}
```

**Response Example:**
```json
{
  "orderId": "order-123e4567-e89b-12d3-a456-426614174000",
  "orderNumber": "01HN2K3M4P5Q6R7S8T9V0W",
  "status": "WAITING_TO_FULFILL",
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
    "address": {
      "line1": "123 Main Street",
      "city": "New York",
      "postcode": "10001",
      "country": "US"
    }
  },
  "createdAt": "2025-09-10T10:10:05Z"
}
```

#### 5.2 GET /ui/order/{orderId} - Get Order

**Purpose:** Retrieve order details for confirmation and status tracking.

**Path Parameters:**
- `orderId` (String, required) - Order identifier

**Request Example:**
```
GET /ui/order/order-123e4567-e89b-12d3-a456-426614174000
```

**Response Example:** (Same as create order response)

## Common Controller Patterns

### Error Handling
All controllers should handle:
- 400 Bad Request - Invalid input data
- 404 Not Found - Entity not found
- 409 Conflict - Business rule violations
- 500 Internal Server Error - System errors

### Response Format
Standard response format:
```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

Error response format:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Cart must have items before checkout",
    "details": { ... }
  }
}
```

### Implementation Guidelines

1. **Package Location:** `com.java_template.application.controller`
2. **Spring Annotations:** Use `@RestController`, `@RequestMapping`
3. **EntityService Usage:** Use EntityService for all Cyoda operations
4. **Transition Names:** Specify correct transition names for state changes
5. **Validation:** Validate input data before processing
6. **Error Handling:** Provide meaningful error messages
7. **Security:** No authentication required, server-side credentials only
