# Controller Requirements

## Overview

This document defines the detailed requirements for REST API controllers in the Cyoda OMS Backend system. Controllers expose UI-facing endpoints under `/ui/**` and handle all browser interactions without requiring authentication.

## Controller Design Principles

1. Separate controller for each entity (ProductController, CartController, etc.)
2. All endpoints under `/ui/**` prefix for UI-facing APIs
3. No authentication required (anonymous access)
4. Update endpoints include optional transition name parameter
5. Complete request/response examples for all endpoints
6. Proper HTTP status codes and error handling

## 1. ProductController

**Base Path:** `/ui/products`
**Purpose:** Manages product catalog operations for the UI

### Endpoints

#### GET /ui/products
**Purpose:** Search and filter products with pagination

**Query Parameters:**
- `search` (string, optional) - Free text search on name/description
- `category` (string, optional) - Filter by product category
- `minPrice` (number, optional) - Minimum price filter
- `maxPrice` (number, optional) - Maximum price filter
- `page` (integer, optional, default=0) - Page number for pagination
- `pageSize` (integer, optional, default=20) - Number of items per page

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
      "description": "High-performance gaming laptop with RTX graphics",
      "price": 1299.99,
      "quantityAvailable": 15,
      "category": "electronics",
      "imageUrl": "https://example.com/images/laptop-001.jpg"
    },
    {
      "sku": "LAPTOP-002", 
      "name": "Business Laptop",
      "description": "Professional laptop for business use",
      "price": 899.99,
      "quantityAvailable": 8,
      "category": "electronics",
      "imageUrl": "https://example.com/images/laptop-002.jpg"
    }
  ],
  "page": 0,
  "pageSize": 10,
  "totalElements": 25,
  "totalPages": 3,
  "hasNext": true,
  "hasPrevious": false
}
```

#### GET /ui/products/{sku}
**Purpose:** Get complete product details by SKU

**Path Parameters:**
- `sku` (string, required) - Product SKU

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
  "warehouseId": "LON-01",
  "attributes": {
    "brand": "TechBrand",
    "model": "Pro-X1",
    "dimensions": { "l": 35.5, "w": 24.2, "h": 2.1, "unit": "cm" },
    "weight": { "value": 2.3, "unit": "kg" },
    "custom": { "warranty": "2 years", "color": "black" }
  },
  "localizations": {
    "defaultLocale": "en-GB",
    "content": [
      {
        "locale": "en-GB",
        "name": "Gaming Laptop Pro",
        "description": "High-performance gaming laptop with RTX graphics",
        "regulatory": { "ukca": true }
      }
    ]
  },
  "media": [
    {
      "type": "image",
      "url": "https://example.com/images/laptop-001-hero.jpg",
      "alt": "Gaming Laptop Pro - Hero Image",
      "tags": ["hero"]
    }
  ]
}
```

## 2. CartController

**Base Path:** `/ui/cart`
**Purpose:** Manages shopping cart operations

### Endpoints

#### POST /ui/cart
**Purpose:** Create new cart or return existing cart

**Request Body:** Empty

**Request Example:**
```
POST /ui/cart
Content-Type: application/json
{}
```

**Response Example:**
```json
{
  "cartId": "cart-12345-abcde",
  "lines": [],
  "totalItems": 0,
  "grandTotal": 0.0,
  "guestContact": null,
  "createdAt": "2025-09-02T10:30:00Z",
  "updatedAt": "2025-09-02T10:30:00Z"
}
```

#### POST /ui/cart/{cartId}/lines
**Purpose:** Add item to cart or increment quantity

**Path Parameters:**
- `cartId` (string, required) - Cart identifier

**Query Parameters:**
- `transition` (string, optional) - Workflow transition name ("create_on_first_add" or "add_item")

**Request Body:**
```json
{
  "sku": "LAPTOP-001",
  "qty": 2
}
```

**Request Example:**
```
POST /ui/cart/cart-12345-abcde/lines?transition=add_item
Content-Type: application/json

{
  "sku": "LAPTOP-001", 
  "qty": 2
}
```

**Response Example:**
```json
{
  "cartId": "cart-12345-abcde",
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
  "createdAt": "2025-09-02T10:30:00Z",
  "updatedAt": "2025-09-02T10:35:00Z"
}
```

#### PATCH /ui/cart/{cartId}/lines
**Purpose:** Update item quantity or remove item (qty=0)

**Path Parameters:**
- `cartId` (string, required) - Cart identifier

**Query Parameters:**
- `transition` (string, optional) - Workflow transition name ("update_item")

**Request Body:**
```json
{
  "sku": "LAPTOP-001",
  "qty": 1
}
```

**Request Example:**
```
PATCH /ui/cart/cart-12345-abcde/lines?transition=update_item
Content-Type: application/json

{
  "sku": "LAPTOP-001",
  "qty": 1
}
```

**Response Example:**
```json
{
  "cartId": "cart-12345-abcde", 
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
  "createdAt": "2025-09-02T10:30:00Z",
  "updatedAt": "2025-09-02T10:40:00Z"
}
```

#### POST /ui/cart/{cartId}/open-checkout
**Purpose:** Begin checkout process

**Path Parameters:**
- `cartId` (string, required) - Cart identifier

**Query Parameters:**
- `transition` (string, required) - Workflow transition name ("open_checkout")

**Request Body:** Empty

**Request Example:**
```
POST /ui/cart/cart-12345-abcde/open-checkout?transition=open_checkout
Content-Type: application/json
{}
```

**Response Example:**
```json
{
  "cartId": "cart-12345-abcde",
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
  "createdAt": "2025-09-02T10:30:00Z",
  "updatedAt": "2025-09-02T10:45:00Z"
}
```

#### GET /ui/cart/{cartId}
**Purpose:** Get cart details

**Path Parameters:**
- `cartId` (string, required) - Cart identifier

**Request Example:**
```
GET /ui/cart/cart-12345-abcde
```

**Response Example:**
```json
{
  "cartId": "cart-12345-abcde",
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
    "phone": "+44 20 1234 5678",
    "address": {
      "line1": "123 Main Street",
      "city": "London",
      "postcode": "SW1A 1AA",
      "country": "UK"
    }
  },
  "createdAt": "2025-09-02T10:30:00Z",
  "updatedAt": "2025-09-02T10:50:00Z"
}
```

## 3. CheckoutController

**Base Path:** `/ui/checkout`
**Purpose:** Manages anonymous checkout process

### Endpoints

#### POST /ui/checkout/{cartId}
**Purpose:** Add guest contact information to cart

**Path Parameters:**
- `cartId` (string, required) - Cart identifier

**Query Parameters:**
- `transition` (string, optional) - Workflow transition name ("checkout")

**Request Body:**
```json
{
  "guestContact": {
    "name": "John Doe",
    "email": "john.doe@example.com",
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

**Request Example:**
```
POST /ui/checkout/cart-12345-abcde?transition=checkout
Content-Type: application/json

{
  "guestContact": {
    "name": "John Doe",
    "email": "john.doe@example.com",
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

**Response Example:**
```json
{
  "cartId": "cart-12345-abcde",
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
    "phone": "+44 20 1234 5678",
    "address": {
      "line1": "123 Main Street",
      "city": "London",
      "postcode": "SW1A 1AA",
      "country": "UK"
    }
  },
  "createdAt": "2025-09-02T10:30:00Z",
  "updatedAt": "2025-09-02T10:55:00Z"
}
```

## 4. PaymentController

**Base Path:** `/ui/payment`
**Purpose:** Manages dummy payment processing

### Endpoints

#### POST /ui/payment/start
**Purpose:** Start dummy payment process

**Query Parameters:**
- `transition` (string, optional) - Workflow transition name ("initialize_payment")

**Request Body:**
```json
{
  "cartId": "cart-12345-abcde"
}
```

**Request Example:**
```
POST /ui/payment/start?transition=initialize_payment
Content-Type: application/json

{
  "cartId": "cart-12345-abcde"
}
```

**Response Example:**
```json
{
  "paymentId": "payment-67890-fghij",
  "cartId": "cart-12345-abcde",
  "amount": 1299.99,
  "provider": "DUMMY",
  "createdAt": "2025-09-02T11:00:00Z",
  "updatedAt": "2025-09-02T11:00:00Z"
}
```

#### GET /ui/payment/{paymentId}
**Purpose:** Poll payment status

**Path Parameters:**
- `paymentId` (string, required) - Payment identifier

**Request Example:**
```
GET /ui/payment/payment-67890-fghij
```

**Response Example (INITIATED):**
```json
{
  "paymentId": "payment-67890-fghij",
  "cartId": "cart-12345-abcde",
  "amount": 1299.99,
  "status": "INITIATED",
  "provider": "DUMMY",
  "createdAt": "2025-09-02T11:00:00Z",
  "updatedAt": "2025-09-02T11:00:00Z"
}
```

**Response Example (PAID):**
```json
{
  "paymentId": "payment-67890-fghij",
  "cartId": "cart-12345-abcde",
  "amount": 1299.99,
  "status": "PAID",
  "provider": "DUMMY",
  "createdAt": "2025-09-02T11:00:00Z",
  "updatedAt": "2025-09-02T11:00:03Z"
}
```

## 5. OrderController

**Base Path:** `/ui/order`
**Purpose:** Manages order creation and tracking

### Endpoints

#### POST /ui/order/create
**Purpose:** Create order from paid cart

**Query Parameters:**
- `transition` (string, required) - Workflow transition name ("create_order_from_paid")

**Request Body:**
```json
{
  "paymentId": "payment-67890-fghij",
  "cartId": "cart-12345-abcde"
}
```

**Request Example:**
```
POST /ui/order/create?transition=create_order_from_paid
Content-Type: application/json

{
  "paymentId": "payment-67890-fghij",
  "cartId": "cart-12345-abcde"
}
```

**Response Example:**
```json
{
  "orderId": "order-11111-22222",
  "orderNumber": "01J6K7M8N9P0Q1",
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
    "email": "john.doe@example.com",
    "phone": "+44 20 1234 5678",
    "address": {
      "line1": "123 Main Street",
      "city": "London",
      "postcode": "SW1A 1AA",
      "country": "UK"
    }
  },
  "createdAt": "2025-09-02T11:00:05Z",
  "updatedAt": "2025-09-02T11:00:05Z"
}
```

#### GET /ui/order/{orderId}
**Purpose:** Get order details for confirmation/tracking

**Path Parameters:**
- `orderId` (string, required) - Order identifier

**Request Example:**
```
GET /ui/order/order-11111-22222
```

**Response Example:**
```json
{
  "orderId": "order-11111-22222",
  "orderNumber": "01J6K7M8N9P0Q1",
  "status": "SENT",
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
    "email": "john.doe@example.com",
    "phone": "+44 20 1234 5678",
    "address": {
      "line1": "123 Main Street",
      "city": "London",
      "postcode": "SW1A 1AA",
      "country": "UK"
    }
  },
  "createdAt": "2025-09-02T11:00:05Z",
  "updatedAt": "2025-09-02T11:30:00Z"
}
```

## Error Handling

### Standard Error Response Format
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Cart has no items",
    "details": {
      "field": "lines",
      "value": "[]"
    }
  },
  "timestamp": "2025-09-02T11:00:00Z",
  "path": "/ui/cart/cart-12345-abcde/open-checkout"
}
```

### HTTP Status Codes
- `200 OK` - Successful operation
- `201 Created` - Resource created successfully
- `400 Bad Request` - Invalid request data or business rule violation
- `404 Not Found` - Resource not found
- `409 Conflict` - State conflict (e.g., cart already converted)
- `500 Internal Server Error` - Unexpected server error

### Common Error Scenarios
1. **Cart not found** - 404 with message "Cart not found"
2. **Product not found** - 404 with message "Product not found"
3. **Invalid transition** - 400 with message "Invalid transition for current state"
4. **Insufficient stock** - 400 with message "Insufficient stock available"
5. **Payment not paid** - 400 with message "Payment must be in PAID state"

## Controller Implementation Guidelines

### Naming Conventions
- Controller classes: `{Entity}Controller` (e.g., `ProductController`)
- Endpoint methods: descriptive verbs (`searchProducts`, `addToCart`, `createOrder`)
- Request/Response DTOs: `{Operation}Request/Response` (e.g., `AddToCartRequest`)

### Validation Rules
1. **Path Parameters** - Validate format and existence
2. **Query Parameters** - Validate ranges and formats
3. **Request Bodies** - Validate required fields and data types
4. **Business Rules** - Validate state transitions and business constraints

### Response Patterns
1. **Success Responses** - Return updated entity state
2. **Error Responses** - Use standard error format
3. **Pagination** - Include metadata for list endpoints
4. **Timestamps** - Use ISO 8601 format (UTC)

### Security Considerations
1. **Input Sanitization** - Sanitize all user inputs
2. **Rate Limiting** - Implement rate limiting for public endpoints
3. **CORS** - Configure CORS for browser access
4. **Logging** - Log all API calls for monitoring

### Performance Optimization
1. **Caching** - Cache product catalog data
2. **Pagination** - Implement efficient pagination
3. **Lazy Loading** - Load related data only when needed
4. **Connection Pooling** - Use connection pooling for database access

## Integration with Workflows

### Transition Parameter Usage
- Include `transition` query parameter for state-changing operations
- Map transition names to workflow configuration
- Validate current state allows the specified transition
- Handle transition failures gracefully

### Entity State Management
- Always return current entity state in responses
- Use `entity.meta.state` to access workflow state
- Ensure state consistency across related entities
- Log state transitions for audit trail

### Asynchronous Processing
- Use appropriate execution modes for processors
- Handle long-running operations gracefully
- Provide status polling endpoints where needed
- Implement timeout handling for external calls
