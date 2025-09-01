# Controllers

This document defines the REST API controllers for the Cyoda OMS Backend system.

## ProductController

**Base Path**: `/ui/products`

### GET /ui/products

**Description**: Search and filter products with pagination

**Parameters**:
- `search` (string, optional): Free-text search on name/description
- `category` (string, optional): Filter by category
- `minPrice` (number, optional): Minimum price filter
- `maxPrice` (number, optional): Maximum price filter
- `page` (int, optional, default=0): Page number
- `pageSize` (int, optional, default=20): Page size

**Response**: List of slim product DTOs

**Example Request**:
```
GET /ui/products?search=laptop&category=electronics&minPrice=500&maxPrice=2000&page=0&pageSize=10
```

**Example Response**:
```json
{
  "content": [
    {
      "sku": "LAPTOP001",
      "name": "Gaming Laptop Pro",
      "description": "High-performance gaming laptop",
      "price": 1299.99,
      "quantityAvailable": 15,
      "category": "electronics",
      "imageUrl": "https://example.com/laptop001.jpg"
    }
  ],
  "page": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

### GET /ui/products/{sku}

**Description**: Get full product details by SKU

**Parameters**:
- `sku` (string, path): Product SKU

**Response**: Full Product document with complete schema

**Example Request**:
```
GET /ui/products/LAPTOP001
```

**Example Response**:
```json
{
  "sku": "LAPTOP001",
  "name": "Gaming Laptop Pro",
  "description": "High-performance gaming laptop",
  "price": 1299.99,
  "quantityAvailable": 15,
  "category": "electronics",
  "warehouseId": "WH001",
  "attributes": {
    "brand": "TechCorp",
    "model": "GP-2024"
  },
  "media": {
    "images": ["https://example.com/laptop001.jpg"]
  },
  "variants": [],
  "inventory": {},
  "compliance": {},
  "relationships": {},
  "events": []
}
```

---

## CartController

**Base Path**: `/ui/cart`

### POST /ui/cart

**Description**: Create new cart or return existing cart

**Request Body**: Empty or cart initialization data

**Response**: Cart entity

**Example Request**:
```
POST /ui/cart
```

**Example Response**:
```json
{
  "cartId": "cart_01HGW2E8K9XYZ123",
  "status": "NEW",
  "lines": [],
  "totalItems": 0,
  "grandTotal": 0.0,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

### POST /ui/cart/{cartId}/lines

**Description**: Add or increment item in cart

**Parameters**:
- `cartId` (string, path): Cart identifier
- `transition` (string, query, optional): Workflow transition name

**Request Body**:
```json
{
  "sku": "LAPTOP001",
  "qty": 1
}
```

**Response**: Updated Cart entity

**Example Response**:
```json
{
  "cartId": "cart_01HGW2E8K9XYZ123",
  "status": "ACTIVE",
  "lines": [
    {
      "sku": "LAPTOP001",
      "name": "Gaming Laptop Pro",
      "price": 1299.99,
      "qty": 1
    }
  ],
  "totalItems": 1,
  "grandTotal": 1299.99,
  "updatedAt": "2024-01-15T10:35:00Z"
}
```

### PATCH /ui/cart/{cartId}/lines

**Description**: Update or remove item in cart

**Parameters**:
- `cartId` (string, path): Cart identifier
- `transition` (string, query, optional): Workflow transition name

**Request Body**:
```json
{
  "sku": "LAPTOP001",
  "qty": 2
}
```

**Response**: Updated Cart entity

### POST /ui/cart/{cartId}/open-checkout

**Description**: Transition cart to checkout state

**Parameters**:
- `cartId` (string, path): Cart identifier
- `transition` (string, query, default="OPEN_CHECKOUT"): Workflow transition name

**Response**: Updated Cart entity with CHECKING_OUT status

### GET /ui/cart/{cartId}

**Description**: Get cart details

**Parameters**:
- `cartId` (string, path): Cart identifier

**Response**: Cart entity

---

## CheckoutController

**Base Path**: `/ui/checkout`

### POST /ui/checkout/{cartId}

**Description**: Attach guest contact information to cart

**Parameters**:
- `cartId` (string, path): Cart identifier

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
      "country": "USA"
    }
  }
}
```

**Response**: Updated Cart entity with guest contact information

---

## PaymentController

**Base Path**: `/ui/payment`

### POST /ui/payment/start

**Description**: Start dummy payment process

**Request Body**:
```json
{
  "cartId": "cart_01HGW2E8K9XYZ123"
}
```

**Response**: Payment entity

**Example Response**:
```json
{
  "paymentId": "pay_01HGW2F1M2ABC456",
  "cartId": "cart_01HGW2E8K9XYZ123",
  "amount": 1299.99,
  "status": "INITIATED",
  "provider": "DUMMY",
  "createdAt": "2024-01-15T10:40:00Z",
  "updatedAt": "2024-01-15T10:40:00Z"
}
```

### GET /ui/payment/{paymentId}

**Description**: Get payment status (for polling)

**Parameters**:
- `paymentId` (string, path): Payment identifier

**Response**: Payment entity with current status

**Example Response**:
```json
{
  "paymentId": "pay_01HGW2F1M2ABC456",
  "cartId": "cart_01HGW2E8K9XYZ123",
  "amount": 1299.99,
  "status": "PAID",
  "provider": "DUMMY",
  "createdAt": "2024-01-15T10:40:00Z",
  "updatedAt": "2024-01-15T10:40:03Z"
}
```

---

## OrderController

**Base Path**: `/ui/order`

### POST /ui/order/create

**Description**: Create order from paid payment

**Request Body**:
```json
{
  "paymentId": "pay_01HGW2F1M2ABC456",
  "cartId": "cart_01HGW2E8K9XYZ123"
}
```

**Response**: Created Order entity

**Example Response**:
```json
{
  "orderId": "ord_01HGW2F5N3DEF789",
  "orderNumber": "01HGW2F5N3",
  "status": "WAITING_TO_FULFILL",
  "lines": [
    {
      "sku": "LAPTOP001",
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
    "address": {
      "line1": "123 Main Street",
      "city": "New York",
      "postcode": "10001",
      "country": "USA"
    }
  },
  "createdAt": "2024-01-15T10:40:05Z",
  "updatedAt": "2024-01-15T10:40:05Z"
}
```

### GET /ui/order/{orderId}

**Description**: Get order details

**Parameters**:
- `orderId` (string, path): Order identifier

**Response**: Order entity

### PUT /ui/order/{orderId}

**Description**: Update order (for status transitions)

**Parameters**:
- `orderId` (string, path): Order identifier
- `transition` (string, query, required): Workflow transition name

**Request Body**: Order entity (optional updates)

**Response**: Updated Order entity

**Example Request**:
```
PUT /ui/order/ord_01HGW2F5N3DEF789?transition=START_PICKING
```

---

## ShipmentController

**Base Path**: `/ui/shipment`

### GET /ui/shipment/order/{orderId}

**Description**: Get shipment details by order ID

**Parameters**:
- `orderId` (string, path): Order identifier

**Response**: Shipment entity

**Example Response**:
```json
{
  "shipmentId": "ship_01HGW2F7P4GHI012",
  "orderId": "ord_01HGW2F5N3DEF789",
  "status": "PICKING",
  "lines": [
    {
      "sku": "LAPTOP001",
      "qtyOrdered": 1,
      "qtyPicked": 0,
      "qtyShipped": 0
    }
  ],
  "createdAt": "2024-01-15T10:40:05Z",
  "updatedAt": "2024-01-15T10:40:05Z"
}
```

### PUT /ui/shipment/{shipmentId}

**Description**: Update shipment (for status transitions)

**Parameters**:
- `shipmentId` (string, path): Shipment identifier
- `transition` (string, query, required): Workflow transition name

**Request Body**: Shipment entity (optional updates)

**Response**: Updated Shipment entity

**Example Request**:
```
PUT /ui/shipment/ship_01HGW2F7P4GHI012?transition=READY_FOR_SHIPPING
```

**Example Response**:
```json
{
  "shipmentId": "ship_01HGW2F7P4GHI012",
  "orderId": "ord_01HGW2F5N3DEF789",
  "status": "WAITING_TO_SEND",
  "lines": [
    {
      "sku": "LAPTOP001",
      "qtyOrdered": 1,
      "qtyPicked": 1,
      "qtyShipped": 0
    }
  ],
  "updatedAt": "2024-01-15T11:00:00Z"
}
```

---

## Error Handling

All controllers should implement consistent error handling:

### Standard Error Response Format:
```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable error message",
    "details": "Additional error details",
    "timestamp": "2024-01-15T10:40:00Z"
  }
}
```

### Common HTTP Status Codes:
- `200 OK`: Successful operation
- `201 Created`: Resource created successfully
- `400 Bad Request`: Invalid request data
- `404 Not Found`: Resource not found
- `409 Conflict`: Business rule violation (e.g., insufficient stock)
- `500 Internal Server Error`: Server error

### Example Error Responses:

**Product Not Found (404)**:
```json
{
  "error": {
    "code": "PRODUCT_NOT_FOUND",
    "message": "Product with SKU 'INVALID001' not found",
    "timestamp": "2024-01-15T10:40:00Z"
  }
}
```

**Insufficient Stock (409)**:
```json
{
  "error": {
    "code": "INSUFFICIENT_STOCK",
    "message": "Insufficient stock for product LAPTOP001. Available: 5, Requested: 10",
    "timestamp": "2024-01-15T10:40:00Z"
  }
}
```

**Invalid Transition (400)**:
```json
{
  "error": {
    "code": "INVALID_TRANSITION",
    "message": "Cannot transition from DELIVERED to PICKING",
    "details": "Order is already in final state",
    "timestamp": "2024-01-15T10:40:00Z"
  }
}
```
