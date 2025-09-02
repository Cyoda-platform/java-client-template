# Controller Requirements

## Overview

This document defines the detailed requirements for all controllers in the Cyoda OMS Backend system. Controllers expose UI-facing REST APIs under `/ui/**` that the browser will call. The backend holds server-side Cyoda credentials and never exposes service tokens to the browser.

## Controller Definitions

### 1. ProductController

**Base Path**: `/ui/products`
**Purpose**: Manages product catalog operations for UI

#### GET /ui/products

**Description**: Search and filter products with pagination
**Parameters**:
- `search` (string, optional): Free-text search on name/description
- `category` (string, optional): Filter by product category
- `minPrice` (number, optional): Minimum price filter
- `maxPrice` (number, optional): Maximum price filter
- `page` (integer, optional, default=0): Page number
- `pageSize` (integer, optional, default=20): Items per page

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

#### GET /ui/products/{sku}

**Description**: Get full product details by SKU
**Parameters**:
- `sku` (string, required): Product SKU

**Request Example**:
```
GET /ui/products/LAPTOP-001
```

**Response Example**: Returns complete Product schema as defined in user requirements
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
    "dimensions": { "l": 35.5, "w": 25.0, "h": 2.5, "unit": "cm" },
    "weight": { "value": 2.1, "unit": "kg" }
  },
  "localizations": {
    "defaultLocale": "en-GB",
    "content": [
      { "locale": "en-GB", "name": "Gaming Laptop Pro", "description": "High-performance gaming laptop" }
    ]
  },
  "media": [
    { "type": "image", "url": "https://example.com/images/laptop-001.jpg", "alt": "Gaming Laptop Pro", "tags": ["hero"] }
  ]
}
```

---

### 2. CartController

**Base Path**: `/ui/cart`
**Purpose**: Manages shopping cart operations

#### POST /ui/cart

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
  "lines": [],
  "totalItems": 0,
  "grandTotal": 0.0,
  "guestContact": null,
  "createdAt": "2025-09-02T10:00:00Z",
  "updatedAt": "2025-09-02T10:00:00Z"
}
```

#### POST /ui/cart/{cartId}/lines

**Description**: Add or increment item in cart
**Parameters**:
- `cartId` (string, required): Cart ID
- `transitionName` (string, optional): "ADD_ITEM" for new items, null for existing

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
Content-Type: application/json

{
  "sku": "LAPTOP-001", 
  "qty": 2
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
  "guestContact": null,
  "createdAt": "2025-09-02T10:00:00Z",
  "updatedAt": "2025-09-02T10:05:00Z"
}
```

#### PATCH /ui/cart/{cartId}/lines

**Description**: Update item quantity in cart (remove if qty=0)
**Parameters**:
- `cartId` (string, required): Cart ID
- `transitionName` (string, optional): "UPDATE_ITEM"

**Request Body**:
```json
{
  "sku": "LAPTOP-001",
  "qty": 1
}
```

**Request Example**:
```
PATCH /ui/cart/cart-12345/lines
Content-Type: application/json

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
  "guestContact": null,
  "createdAt": "2025-09-02T10:00:00Z",
  "updatedAt": "2025-09-02T10:10:00Z"
}
```

#### POST /ui/cart/{cartId}/open-checkout

**Description**: Set cart to checkout state
**Parameters**:
- `cartId` (string, required): Cart ID
- `transitionName` (string, required): "OPEN_CHECKOUT"

**Request Example**:
```
POST /ui/cart/cart-12345/open-checkout
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
  "guestContact": null,
  "createdAt": "2025-09-02T10:00:00Z",
  "updatedAt": "2025-09-02T10:15:00Z"
}
```

#### GET /ui/cart/{cartId}

**Description**: Get cart details
**Parameters**:
- `cartId` (string, required): Cart ID

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
      "qty": 1
    }
  ],
  "totalItems": 1,
  "grandTotal": 1299.99,
  "guestContact": null,
  "createdAt": "2025-09-02T10:00:00Z",
  "updatedAt": "2025-09-02T10:15:00Z"
}
```

---

### 3. CheckoutController

**Base Path**: `/ui/checkout`
**Purpose**: Manages anonymous checkout process

#### POST /ui/checkout/{cartId}

**Description**: Attach guest contact information to cart
**Parameters**:
- `cartId` (string, required): Cart ID

**Request Body**:
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

**Request Example**:
```
POST /ui/checkout/cart-12345
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
  "createdAt": "2025-09-02T10:00:00Z",
  "updatedAt": "2025-09-02T10:20:00Z"
}
```

---

### 4. PaymentController

**Base Path**: `/ui/payment`
**Purpose**: Manages dummy payment processing

#### POST /ui/payment/start

**Description**: Start dummy payment process
**Request Body**:
```json
{
  "cartId": "cart-12345"
}
```

**Request Example**:
```
POST /ui/payment/start
Content-Type: application/json

{
  "cartId": "cart-12345"
}
```

**Response Example**:
```json
{
  "paymentId": "payment-67890",
  "cartId": "cart-12345",
  "amount": 1299.99,
  "provider": "DUMMY",
  "createdAt": "2025-09-02T10:25:00Z",
  "updatedAt": "2025-09-02T10:25:00Z"
}
```

#### GET /ui/payment/{paymentId}

**Description**: Poll payment status
**Parameters**:
- `paymentId` (string, required): Payment ID

**Request Example**:
```
GET /ui/payment/payment-67890
```

**Response Example** (INITIATED):
```json
{
  "paymentId": "payment-67890",
  "cartId": "cart-12345",
  "amount": 1299.99,
  "provider": "DUMMY",
  "createdAt": "2025-09-02T10:25:00Z",
  "updatedAt": "2025-09-02T10:25:00Z"
}
```

**Response Example** (PAID after ~3 seconds):
```json
{
  "paymentId": "payment-67890",
  "cartId": "cart-12345",
  "amount": 1299.99,
  "provider": "DUMMY",
  "createdAt": "2025-09-02T10:25:00Z",
  "updatedAt": "2025-09-02T10:28:00Z"
}
```

---

### 5. OrderController

**Base Path**: `/ui/order`
**Purpose**: Manages order creation and tracking

#### POST /ui/order/create

**Description**: Create order from paid cart
**Parameters**:
- `transitionName` (string, required): "CREATE_ORDER_FROM_PAID"

**Request Body**:
```json
{
  "paymentId": "payment-67890",
  "cartId": "cart-12345"
}
```

**Request Example**:
```
POST /ui/order/create
Content-Type: application/json

{
  "paymentId": "payment-67890",
  "cartId": "cart-12345"
}
```

**Response Example**:
```json
{
  "orderId": "order-abc123",
  "orderNumber": "01J6QZXM2K",
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
  "createdAt": "2025-09-02T10:30:00Z",
  "updatedAt": "2025-09-02T10:30:00Z"
}
```

#### GET /ui/order/{orderId}

**Description**: Get order details for confirmation/status
**Parameters**:
- `orderId` (string, required): Order ID

**Request Example**:
```
GET /ui/order/order-abc123
```

**Response Example**:
```json
{
  "orderId": "order-abc123",
  "orderNumber": "01J6QZXM2K",
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
  "createdAt": "2025-09-02T10:30:00Z",
  "updatedAt": "2025-09-02T10:30:00Z"
}
```

---

### 6. ShipmentController

**Base Path**: `/ui/shipment`
**Purpose**: Manages shipment operations (for demo/admin purposes)

#### GET /ui/shipment/order/{orderId}

**Description**: Get shipment details for an order
**Parameters**:
- `orderId` (string, required): Order ID

**Request Example**:
```
GET /ui/shipment/order/order-abc123
```

**Response Example**:
```json
{
  "shipmentId": "shipment-xyz789",
  "orderId": "order-abc123",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "qtyOrdered": 1,
      "qtyPicked": 1,
      "qtyShipped": 1
    }
  ],
  "createdAt": "2025-09-02T10:30:00Z",
  "updatedAt": "2025-09-02T10:35:00Z"
}
```

#### PATCH /ui/shipment/{shipmentId}/ready-to-send

**Description**: Mark shipment ready to send
**Parameters**:
- `shipmentId` (string, required): Shipment ID
- `transitionName` (string, required): "READY_TO_SEND"

**Request Example**:
```
PATCH /ui/shipment/shipment-xyz789/ready-to-send
```

**Response Example**:
```json
{
  "shipmentId": "shipment-xyz789",
  "orderId": "order-abc123",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "qtyOrdered": 1,
      "qtyPicked": 1,
      "qtyShipped": 0
    }
  ],
  "createdAt": "2025-09-02T10:30:00Z",
  "updatedAt": "2025-09-02T10:40:00Z"
}
```

#### PATCH /ui/shipment/{shipmentId}/mark-sent

**Description**: Mark shipment as sent
**Parameters**:
- `shipmentId` (string, required): Shipment ID
- `transitionName` (string, required): "MARK_SENT"

**Request Example**:
```
PATCH /ui/shipment/shipment-xyz789/mark-sent
```

**Response Example**:
```json
{
  "shipmentId": "shipment-xyz789",
  "orderId": "order-abc123",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "qtyOrdered": 1,
      "qtyPicked": 1,
      "qtyShipped": 1
    }
  ],
  "createdAt": "2025-09-02T10:30:00Z",
  "updatedAt": "2025-09-02T10:45:00Z"
}
```

#### PATCH /ui/shipment/{shipmentId}/mark-delivered

**Description**: Mark shipment as delivered
**Parameters**:
- `shipmentId` (string, required): Shipment ID
- `transitionName` (string, required): "MARK_DELIVERED"

**Request Example**:
```
PATCH /ui/shipment/shipment-xyz789/mark-delivered
```

**Response Example**:
```json
{
  "shipmentId": "shipment-xyz789",
  "orderId": "order-abc123",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "qtyOrdered": 1,
      "qtyPicked": 1,
      "qtyShipped": 1
    }
  ],
  "createdAt": "2025-09-02T10:30:00Z",
  "updatedAt": "2025-09-02T10:50:00Z"
}
```

## Controller Summary

| Controller | Base Path | Purpose | Entities |
|------------|-----------|---------|----------|
| ProductController | `/ui/products` | Product catalog operations | Product |
| CartController | `/ui/cart` | Shopping cart management | Cart |
| CheckoutController | `/ui/checkout` | Anonymous checkout process | Cart |
| PaymentController | `/ui/payment` | Dummy payment processing | Payment |
| OrderController | `/ui/order` | Order creation and tracking | Order |
| ShipmentController | `/ui/shipment` | Shipment management | Shipment |

## API Design Principles

1. **No Authentication**: All `/ui/**` endpoints are anonymous
2. **RESTful Design**: Standard HTTP methods and status codes
3. **Transition Names**: Update endpoints include transition name parameter when state changes
4. **Complete Examples**: All request/response examples include full parameter sets
5. **Error Handling**: Controllers should return appropriate HTTP status codes and error messages
6. **State Management**: Entity states are managed through workflow transitions, not direct updates
