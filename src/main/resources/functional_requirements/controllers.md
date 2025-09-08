# Controller Requirements

## Overview
This document defines the detailed requirements for all UI-facing REST API controllers in the Cyoda OMS Backend system. All endpoints are under `/ui/**` and provide anonymous access without browser authentication.

**Important Notes**:
- Server holds Cyoda credentials, never expose to browser
- Use EntityService to interact with Cyoda backend
- Map full Product schema to slim DTOs for list endpoints
- Include transition names in update endpoints when state changes are needed
- Provide complete request/response examples

## Controllers

### 1. ProductController

**Base Path**: `/ui/products`
**Purpose**: Manage product catalog operations

#### 1.1 List Products (with filters)

**Endpoint**: `GET /ui/products`

**Query Parameters**:
- `search` (string, optional): Free-text search on name/description
- `category` (string, optional): Filter by product category
- `minPrice` (number, optional): Minimum price filter
- `maxPrice` (number, optional): Maximum price filter
- `page` (integer, optional, default=0): Page number
- `pageSize` (integer, optional, default=20): Page size

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
  "totalElements": 2,
  "totalPages": 1
}
```

#### 1.2 Get Product Details

**Endpoint**: `GET /ui/products/{sku}`

**Path Parameters**:
- `sku` (string, required): Product SKU

**Request Example**:
```
GET /ui/products/LAPTOP-001
```

**Response Example** (Full Product Schema):
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

### 2. CartController

**Base Path**: `/ui/cart`
**Purpose**: Manage shopping cart operations

#### 2.1 Create or Get Cart

**Endpoint**: `POST /ui/cart`

**Request Body**: Empty or with first item
```json
{
  "sku": "LAPTOP-001",
  "qty": 1
}
```

**Response Example**:
```json
{
  "cartId": "cart-123e4567-e89b-12d3-a456-426614174000",
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
  "status": "ACTIVE"
}
```

#### 2.2 Add Item to Cart

**Endpoint**: `POST /ui/cart/{cartId}/lines`

**Path Parameters**:
- `cartId` (string, required): Cart identifier

**Request Body**:
```json
{
  "sku": "MOUSE-001",
  "qty": 2
}
```

**Transition**: `ADD_ITEM` (if cart is ACTIVE)

**Response Example**:
```json
{
  "cartId": "cart-123e4567-e89b-12d3-a456-426614174000",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro", 
      "price": 1299.99,
      "qty": 1
    },
    {
      "sku": "MOUSE-001",
      "name": "Wireless Mouse",
      "price": 29.99,
      "qty": 2
    }
  ],
  "totalItems": 3,
  "grandTotal": 1359.97,
  "status": "ACTIVE"
}
```

#### 2.3 Update Item in Cart

**Endpoint**: `PATCH /ui/cart/{cartId}/lines`

**Request Body**:
```json
{
  "sku": "MOUSE-001",
  "qty": 1
}
```

**Transition**: `UPDATE_ITEM` (if qty > 0) or `REMOVE_ITEM` (if qty = 0)

**Response Example**:
```json
{
  "cartId": "cart-123e4567-e89b-12d3-a456-426614174000",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "price": 1299.99,
      "qty": 1
    },
    {
      "sku": "MOUSE-001", 
      "name": "Wireless Mouse",
      "price": 29.99,
      "qty": 1
    }
  ],
  "totalItems": 2,
  "grandTotal": 1329.98,
  "status": "ACTIVE"
}
```

#### 2.4 Open Checkout

**Endpoint**: `POST /ui/cart/{cartId}/open-checkout`

**Transition**: `OPEN_CHECKOUT`

**Response Example**:
```json
{
  "cartId": "cart-123e4567-e89b-12d3-a456-426614174000",
  "status": "CHECKING_OUT",
  "lines": [...],
  "totalItems": 2,
  "grandTotal": 1329.98
}
```

#### 2.5 Get Cart

**Endpoint**: `GET /ui/cart/{cartId}`

**Response Example**:
```json
{
  "cartId": "cart-123e4567-e89b-12d3-a456-426614174000",
  "status": "ACTIVE",
  "lines": [...],
  "totalItems": 2,
  "grandTotal": 1329.98,
  "guestContact": null
}
```

### 3. CheckoutController

**Base Path**: `/ui/checkout`
**Purpose**: Handle anonymous checkout process

#### 3.1 Submit Guest Information

**Endpoint**: `POST /ui/checkout/{cartId}`

**Request Body**:
```json
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

**Transition**: null (updates cart without state change)

**Response Example**:
```json
{
  "cartId": "cart-123e4567-e89b-12d3-a456-426614174000",
  "status": "CHECKING_OUT",
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

### 4. PaymentController

**Base Path**: `/ui/payment`
**Purpose**: Handle dummy payment processing

#### 4.1 Start Payment

**Endpoint**: `POST /ui/payment/start`

**Request Body**:
```json
{
  "cartId": "cart-123e4567-e89b-12d3-a456-426614174000"
}
```

**Transition**: `START_DUMMY_PAYMENT`

**Response Example**:
```json
{
  "paymentId": "pay-123e4567-e89b-12d3-a456-426614174000",
  "cartId": "cart-123e4567-e89b-12d3-a456-426614174000",
  "amount": 1329.98,
  "status": "INITIATED",
  "provider": "DUMMY"
}
```

#### 4.2 Get Payment Status

**Endpoint**: `GET /ui/payment/{paymentId}`

**Response Example**:
```json
{
  "paymentId": "pay-123e4567-e89b-12d3-a456-426614174000",
  "cartId": "cart-123e4567-e89b-12d3-a456-426614174000",
  "amount": 1329.98,
  "status": "PAID",
  "provider": "DUMMY"
}
```

### 5. OrderController

**Base Path**: `/ui/order`
**Purpose**: Handle order creation and tracking

#### 5.1 Create Order

**Endpoint**: `POST /ui/order/create`

**Request Body**:
```json
{
  "paymentId": "pay-123e4567-e89b-12d3-a456-426614174000",
  "cartId": "cart-123e4567-e89b-12d3-a456-426614174000"
}
```

**Transition**: `CREATE_ORDER_FROM_PAID`

**Response Example**:
```json
{
  "orderId": "order-123e4567-e89b-12d3-a456-426614174000",
  "orderNumber": "01HN7K8M9P",
  "status": "WAITING_TO_FULFILL",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "unitPrice": 1299.99,
      "qty": 1,
      "lineTotal": 1299.99
    },
    {
      "sku": "MOUSE-001",
      "name": "Wireless Mouse", 
      "unitPrice": 29.99,
      "qty": 1,
      "lineTotal": 29.99
    }
  ],
  "totals": {
    "items": 2,
    "grand": 1329.98
  }
}
```

#### 5.2 Get Order

**Endpoint**: `GET /ui/order/{orderId}`

**Response Example**:
```json
{
  "orderId": "order-123e4567-e89b-12d3-a456-426614174000",
  "orderNumber": "01HN7K8M9P",
  "status": "SENT",
  "lines": [...],
  "totals": {
    "items": 2,
    "grand": 1329.98
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
  }
}
```

## Error Handling

All controllers should return appropriate HTTP status codes and error messages:

### Common Error Responses

**404 Not Found**:
```json
{
  "error": "NOT_FOUND",
  "message": "Cart not found",
  "timestamp": "2025-09-08T10:30:00Z"
}
```

**400 Bad Request**:
```json
{
  "error": "BAD_REQUEST", 
  "message": "Invalid quantity: must be greater than 0",
  "timestamp": "2025-09-08T10:30:00Z"
}
```

**409 Conflict**:
```json
{
  "error": "CONFLICT",
  "message": "Insufficient stock for product LAPTOP-001",
  "timestamp": "2025-09-08T10:30:00Z"
}
```

## Implementation Guidelines

### Security
- No authentication required for /ui/** endpoints
- Server-side Cyoda credentials only
- Input validation on all request bodies
- Rate limiting considerations

### Performance
- Use slim DTOs for product lists
- Implement pagination for large result sets
- Cache frequently accessed data
- Optimize EntityService calls

### State Management
- Always specify transition names when state changes are expected
- Use null transition for data updates without state changes
- Validate entity states before operations
- Handle concurrent modifications gracefully
