# Controller Requirements

## Overview

This document defines the detailed requirements for UI-facing REST API controllers in the Cyoda OMS Backend system. All endpoints are under `/ui/**` and provide anonymous access without browser authentication.

## Controller Definitions

### 1. ProductController

**Base Path**: `/ui/products`
**Purpose**: Manages product catalog operations for the UI

#### Endpoints

##### GET /ui/products
**Purpose**: List products with filtering and pagination
**Parameters**:
- `search` (string, optional): Free-text search on name/description
- `category` (string, optional): Filter by product category
- `minPrice` (number, optional): Minimum price filter
- `maxPrice` (number, optional): Maximum price filter
- `page` (number, optional, default=0): Page number
- `pageSize` (number, optional, default=20): Items per page

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
  "totalElements": 1,
  "totalPages": 1
}
```

##### GET /ui/products/{sku}
**Purpose**: Get full product details by SKU

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
    "brand": "TechBrand",
    "model": "Pro-X1",
    "dimensions": { "l": 35.5, "w": 24.2, "h": 2.1, "unit": "cm" },
    "weight": { "value": 2.3, "unit": "kg" }
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
**Purpose**: Manages shopping cart operations

#### Endpoints

##### POST /ui/cart
**Purpose**: Create new cart or return existing cart

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
  "cartId": "cart-12345",
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
  "state": "ACTIVE"
}
```

##### GET /ui/cart/{cartId}
**Purpose**: Get cart details

**Request Example**:
```
GET /ui/cart/cart-12345
```

**Response Example**:
```json
{
  "cartId": "cart-12345",
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
  "state": "ACTIVE",
  "guestContact": {
    "name": "John Doe",
    "email": "john@example.com"
  }
}
```

##### POST /ui/cart/{cartId}/lines
**Purpose**: Add or increment item in cart
**Transition**: ADD_ITEM

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
  "cartId": "cart-12345",
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
  "state": "ACTIVE"
}
```

##### PATCH /ui/cart/{cartId}/lines
**Purpose**: Update item quantity in cart (remove if qty=0)
**Transition**: UPDATE_ITEM or REMOVE_ITEM

**Request Example**:
```json
{
  "sku": "LAPTOP-001",
  "qty": 3
}
```

**Response Example**:
```json
{
  "cartId": "cart-12345",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "price": 1299.99,
      "qty": 3
    }
  ],
  "totalItems": 3,
  "grandTotal": 3899.97,
  "state": "ACTIVE"
}
```

##### POST /ui/cart/{cartId}/open-checkout
**Purpose**: Move cart to checkout state
**Transition**: OPEN_CHECKOUT

**Request Example**:
```
POST /ui/cart/cart-12345/open-checkout
```

**Response Example**:
```json
{
  "cartId": "cart-12345",
  "state": "CHECKING_OUT",
  "totalItems": 3,
  "grandTotal": 3899.97
}
```

### 3. CheckoutController

**Base Path**: `/ui/checkout`
**Purpose**: Manages anonymous checkout process

#### Endpoints

##### POST /ui/checkout/{cartId}
**Purpose**: Add guest contact information to cart

**Request Example**:
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
      "country": "USA"
    }
  }
}
```

**Response Example**:
```json
{
  "cartId": "cart-12345",
  "state": "CHECKING_OUT",
  "guestContact": {
    "name": "John Doe",
    "email": "john.doe@example.com",
    "phone": "+1234567890",
    "address": {
      "line1": "123 Main Street",
      "city": "New York",
      "postcode": "10001",
      "country": "USA"
    }
  },
  "totalItems": 3,
  "grandTotal": 3899.97
}
```

### 4. PaymentController

**Base Path**: `/ui/payment`
**Purpose**: Manages dummy payment processing

#### Endpoints

##### POST /ui/payment/start
**Purpose**: Start dummy payment process
**Transition**: START_DUMMY_PAYMENT

**Request Example**:
```json
{
  "cartId": "cart-12345"
}
```

**Response Example**:
```json
{
  "paymentId": "pay-67890",
  "cartId": "cart-12345",
  "amount": 3899.97,
  "provider": "DUMMY",
  "state": "INITIATED"
}
```

##### GET /ui/payment/{paymentId}
**Purpose**: Poll payment status

**Request Example**:
```
GET /ui/payment/pay-67890
```

**Response Example**:
```json
{
  "paymentId": "pay-67890",
  "cartId": "cart-12345",
  "amount": 3899.97,
  "provider": "DUMMY",
  "state": "PAID"
}
```

### 5. OrderController

**Base Path**: `/ui/order`
**Purpose**: Manages order operations

#### Endpoints

##### POST /ui/order/create
**Purpose**: Create order from paid payment
**Transition**: CREATE_ORDER_FROM_PAID

**Request Example**:
```json
{
  "paymentId": "pay-67890",
  "cartId": "cart-12345"
}
```

**Response Example**:
```json
{
  "orderId": "order-abc123",
  "orderNumber": "01HZXK2M3N4P5Q",
  "state": "WAITING_TO_FULFILL",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "unitPrice": 1299.99,
      "qty": 3,
      "lineTotal": 3899.97
    }
  ],
  "totals": {
    "items": 3,
    "grand": 3899.97
  },
  "guestContact": {
    "name": "John Doe",
    "email": "john.doe@example.com",
    "address": {
      "line1": "123 Main Street",
      "city": "New York",
      "postcode": "10001",
      "country": "USA"
    }
  }
}
```

##### GET /ui/order/{orderId}
**Purpose**: Get order details for confirmation/status

**Request Example**:
```
GET /ui/order/order-abc123
```

**Response Example**:
```json
{
  "orderId": "order-abc123",
  "orderNumber": "01HZXK2M3N4P5Q",
  "state": "SENT",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "unitPrice": 1299.99,
      "qty": 3,
      "lineTotal": 3899.97
    }
  ],
  "totals": {
    "items": 3,
    "grand": 3899.97
  },
  "guestContact": {
    "name": "John Doe",
    "email": "john.doe@example.com",
    "address": {
      "line1": "123 Main Street",
      "city": "New York",
      "postcode": "10001",
      "country": "USA"
    }
  },
  "createdAt": "2025-09-02T10:30:00Z",
  "updatedAt": "2025-09-02T11:45:00Z"
}
```

## Implementation Guidelines

1. **Error Handling**: All endpoints should return appropriate HTTP status codes and error messages
2. **Validation**: Validate all input parameters and request bodies
3. **State Management**: Use transition names when updating entity states
4. **Security**: No authentication required, but validate all inputs
5. **Performance**: Use slim DTOs for list endpoints, full entities for detail endpoints
6. **Logging**: Log all significant operations for debugging and monitoring

## HTTP Status Codes

- `200 OK`: Successful operation
- `201 Created`: Resource created successfully
- `400 Bad Request`: Invalid input data
- `404 Not Found`: Resource not found
- `409 Conflict`: Business rule violation (e.g., insufficient stock)
- `500 Internal Server Error`: System error
