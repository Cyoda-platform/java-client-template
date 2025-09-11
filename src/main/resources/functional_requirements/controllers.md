# Controller Requirements

## Overview
This document defines the detailed requirements for all REST controllers in the Cyoda OMS Backend system. Controllers expose UI-facing APIs under `/ui/**` endpoints and handle all interactions with the Cyoda service on behalf of the browser UI.

## Controller Definitions

### 1. ProductController

**Controller Name**: ProductController
**Base Path**: `/ui/products`
**Description**: Manages product catalog operations for the UI.

#### Endpoints

##### GET /ui/products
**Description**: Search and filter products with pagination
**Parameters**:
- `search` (String, optional): Free-text search on name/description
- `category` (String, optional): Filter by product category
- `minPrice` (Double, optional): Minimum price filter
- `maxPrice` (Double, optional): Maximum price filter
- `page` (Integer, optional, default=0): Page number
- `pageSize` (Integer, optional, default=20): Page size

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

##### GET /ui/products/{sku}
**Description**: Get full product details by SKU
**Parameters**:
- `sku` (String, required): Product SKU

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

---

### 2. CartController

**Controller Name**: CartController
**Base Path**: `/ui/cart`
**Description**: Manages shopping cart operations for anonymous checkout.

#### Endpoints

##### POST /ui/cart
**Description**: Create new cart or return existing cart
**Request Body**: Empty

**Request Example**:
```
POST /ui/cart
```

**Response Example**:
```json
{
  "cartId": "cart-12345",
  "status": "NEW",
  "lines": [],
  "totalItems": 0,
  "grandTotal": 0.0,
  "createdAt": "2025-09-11T10:00:00Z",
  "updatedAt": "2025-09-11T10:00:00Z"
}
```

##### POST /ui/cart/{cartId}/lines
**Description**: Add or increment item in cart
**Parameters**:
- `cartId` (String, required): Cart identifier
**Request Body**:
```json
{
  "sku": "LAPTOP-001",
  "qty": 2
}
```

**Request Example**:
```
POST /ui/cart/cart-12345/lines
{
  "sku": "LAPTOP-001",
  "qty": 2
}
```

**Response Example**:
```json
{
  "cartId": "cart-12345",
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
  "updatedAt": "2025-09-11T10:05:00Z"
}
```

##### PATCH /ui/cart/{cartId}/lines
**Description**: Update or remove item in cart (remove if qty=0)
**Parameters**:
- `cartId` (String, required): Cart identifier
**Request Body**:
```json
{
  "sku": "LAPTOP-001",
  "qty": 1
}
```

**Transition Name**: `MODIFY_ITEMS` (if cart is in ACTIVE state)

##### POST /ui/cart/{cartId}/open-checkout
**Description**: Set cart to checkout mode
**Parameters**:
- `cartId` (String, required): Cart identifier

**Transition Name**: `OPEN_CHECKOUT`

**Request Example**:
```
POST /ui/cart/cart-12345/open-checkout
```

##### GET /ui/cart/{cartId}
**Description**: Get cart details
**Parameters**:
- `cartId` (String, required): Cart identifier

---

### 3. CheckoutController

**Controller Name**: CheckoutController
**Base Path**: `/ui/checkout`
**Description**: Manages anonymous checkout process.

#### Endpoints

##### POST /ui/checkout/{cartId}
**Description**: Attach guest contact information to cart
**Parameters**:
- `cartId` (String, required): Cart identifier
**Request Body**:
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

**Request Example**:
```
POST /ui/checkout/cart-12345
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

**Response Example**:
```json
{
  "cartId": "cart-12345",
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
  },
  "updatedAt": "2025-09-11T10:10:00Z"
}
```

---

### 4. PaymentController

**Controller Name**: PaymentController
**Base Path**: `/ui/payment`
**Description**: Manages dummy payment processing.

#### Endpoints

##### POST /ui/payment/start
**Description**: Start dummy payment process
**Request Body**:
```json
{
  "cartId": "cart-12345"
}
```

**Transition Name**: `START_PAYMENT`

**Request Example**:
```
POST /ui/payment/start
{
  "cartId": "cart-12345"
}
```

**Response Example**:
```json
{
  "paymentId": "payment-67890",
  "cartId": "cart-12345",
  "amount": 2599.98,
  "status": "INITIATED",
  "provider": "DUMMY",
  "createdAt": "2025-09-11T10:15:00Z"
}
```

##### GET /ui/payment/{paymentId}
**Description**: Get payment status for polling
**Parameters**:
- `paymentId` (String, required): Payment identifier

**Request Example**:
```
GET /ui/payment/payment-67890
```

**Response Example**:
```json
{
  "paymentId": "payment-67890",
  "cartId": "cart-12345",
  "amount": 2599.98,
  "status": "PAID",
  "provider": "DUMMY",
  "updatedAt": "2025-09-11T10:15:03Z"
}
```

---

### 5. OrderController

**Controller Name**: OrderController
**Base Path**: `/ui/order`
**Description**: Manages order creation and tracking.

#### Endpoints

##### POST /ui/order/create
**Description**: Create order from paid cart
**Request Body**:
```json
{
  "paymentId": "payment-67890",
  "cartId": "cart-12345"
}
```

**Transition Name**: `CREATE_ORDER`

**Request Example**:
```
POST /ui/order/create
{
  "paymentId": "payment-67890",
  "cartId": "cart-12345"
}
```

**Response Example**:
```json
{
  "orderId": "order-abc123",
  "orderNumber": "01HZXK2M3N",
  "status": "WAITING_TO_FULFILL",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "unitPrice": 1299.99,
      "qty": 2,
      "lineTotal": 2599.98
    }
  ],
  "totals": {
    "items": 2,
    "grand": 2599.98
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
  "createdAt": "2025-09-11T10:15:05Z"
}
```

##### GET /ui/order/{orderId}
**Description**: Get order details for confirmation/tracking
**Parameters**:
- `orderId` (String, required): Order identifier

**Request Example**:
```
GET /ui/order/order-abc123
```

## Controller Implementation Guidelines

### Error Handling
All controllers should implement consistent error handling:
- Return appropriate HTTP status codes
- Provide meaningful error messages
- Handle validation errors gracefully
- Log errors for debugging

### Security
- No authentication required (anonymous checkout)
- Validate all input parameters
- Sanitize user input
- Rate limiting considerations

### Response Format
- Use consistent JSON response format
- Include relevant timestamps
- Provide complete entity data where needed
- Use camelCase for JSON properties

### Transition Management
- Specify transition names for state changes
- Pass null for operations without state transitions
- Validate current state allows the transition
- Handle transition failures appropriately

### Pagination
- Use standard pagination parameters (page, pageSize)
- Return pagination metadata in responses
- Set reasonable default page sizes
- Validate pagination parameters
