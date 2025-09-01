# Controller Requirements

## ProductController

**Base Path**: `/ui/products`
**Description**: Handles product catalog operations for the UI.

### Endpoints:

#### GET /ui/products
**Description**: Search and filter products with pagination
**Parameters**:
- `search` (optional): Free-text search on name/description
- `category` (optional): Filter by product category
- `minPrice` (optional): Minimum price filter
- `maxPrice` (optional): Maximum price filter
- `page` (optional, default=0): Page number
- `pageSize` (optional, default=20): Items per page

**Response**: Slim product list DTO
```json
{
  "content": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop",
      "description": "High-performance gaming laptop",
      "price": 1299.99,
      "quantityAvailable": 15,
      "category": "Electronics",
      "imageUrl": "https://example.com/laptop.jpg"
    }
  ],
  "page": 0,
  "pageSize": 20,
  "totalElements": 150,
  "totalPages": 8
}
```

#### GET /ui/products/{sku}
**Description**: Get full product details by SKU
**Parameters**:
- `sku` (path): Product SKU

**Response**: Complete product document (full schema)
```json
{
  "sku": "LAPTOP-001",
  "name": "Gaming Laptop",
  "description": "High-performance gaming laptop",
  "price": 1299.99,
  "quantityAvailable": 15,
  "category": "Electronics",
  "warehouseId": "LON-01",
  "attributes": { ... },
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

---

## CartController

**Base Path**: `/ui/cart`
**Description**: Manages shopping cart operations.

### Endpoints:

#### POST /ui/cart
**Description**: Create new cart or return existing cart
**Request Body**: Empty or cart initialization data
**Response**: Created cart
```json
{
  "cartId": "cart_01HQZX8K9M7N2P3Q4R5S6T7U8V",
  "lines": [],
  "totalItems": 0,
  "grandTotal": 0.0,
  "guestContact": null,
  "createdAt": "2025-09-01T10:00:00Z",
  "updatedAt": "2025-09-01T10:00:00Z"
}
```

#### POST /ui/cart/{cartId}/lines
**Description**: Add item to cart or increment quantity
**Parameters**:
- `cartId` (path): Cart identifier
- `transition` (optional): Workflow transition name

**Request Body**:
```json
{
  "sku": "LAPTOP-001",
  "qty": 1
}
```

**Response**: Updated cart with recalculated totals

#### PATCH /ui/cart/{cartId}/lines
**Description**: Update item quantity or remove item (qty=0)
**Parameters**:
- `cartId` (path): Cart identifier
- `transition` (optional): Workflow transition name

**Request Body**:
```json
{
  "sku": "LAPTOP-001",
  "qty": 2
}
```

**Response**: Updated cart with recalculated totals

#### POST /ui/cart/{cartId}/open-checkout
**Description**: Move cart to checkout state
**Parameters**:
- `cartId` (path): Cart identifier
- `transition`: "OPEN_CHECKOUT"

**Response**: Cart in CHECKING_OUT state

#### GET /ui/cart/{cartId}
**Description**: Get cart details
**Parameters**:
- `cartId` (path): Cart identifier

**Response**: Complete cart data

---

## CheckoutController

**Base Path**: `/ui/checkout`
**Description**: Handles anonymous checkout process.

### Endpoints:

#### POST /ui/checkout/{cartId}
**Description**: Attach guest contact information to cart
**Parameters**:
- `cartId` (path): Cart identifier
- `transition`: "CHECKOUT"

**Request Body**:
```json
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

**Response**: Cart with attached guest contact information

---

## PaymentController

**Base Path**: `/ui/payment`
**Description**: Handles dummy payment processing.

### Endpoints:

#### POST /ui/payment/start
**Description**: Start dummy payment process
**Request Body**:
```json
{
  "cartId": "cart_01HQZX8K9M7N2P3Q4R5S6T7U8V"
}
```

**Response**: Payment initiation response
```json
{
  "paymentId": "pay_01HQZX8K9M7N2P3Q4R5S6T7U8V",
  "cartId": "cart_01HQZX8K9M7N2P3Q4R5S6T7U8V",
  "amount": 1299.99,
  "provider": "DUMMY",
  "status": "INITIATED",
  "createdAt": "2025-09-01T10:05:00Z"
}
```

#### GET /ui/payment/{paymentId}
**Description**: Poll payment status
**Parameters**:
- `paymentId` (path): Payment identifier

**Response**: Current payment status
```json
{
  "paymentId": "pay_01HQZX8K9M7N2P3Q4R5S6T7U8V",
  "cartId": "cart_01HQZX8K9M7N2P3Q4R5S6T7U8V",
  "amount": 1299.99,
  "provider": "DUMMY",
  "status": "PAID",
  "createdAt": "2025-09-01T10:05:00Z",
  "updatedAt": "2025-09-01T10:05:03Z"
}
```

---

## OrderController

**Base Path**: `/ui/order`
**Description**: Manages order creation and tracking.

### Endpoints:

#### POST /ui/order/create
**Description**: Create order from paid cart
**Request Body**:
```json
{
  "paymentId": "pay_01HQZX8K9M7N2P3Q4R5S6T7U8V",
  "cartId": "cart_01HQZX8K9M7N2P3Q4R5S6T7U8V"
}
```

**Response**: Created order
```json
{
  "orderId": "order_01HQZX8K9M7N2P3Q4R5S6T7U8V",
  "orderNumber": "01HQZX8K9M",
  "status": "WAITING_TO_FULFILL",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop",
      "unitPrice": 1299.99,
      "qty": 1,
      "lineTotal": 1299.99
    }
  ],
  "totals": {
    "items": 1,
    "grand": 1299.99
  },
  "guestContact": { ... },
  "createdAt": "2025-09-01T10:05:05Z"
}
```

#### GET /ui/order/{orderId}
**Description**: Get order details for confirmation/tracking
**Parameters**:
- `orderId` (path): Order identifier

**Response**: Complete order data with current status

#### PATCH /ui/order/{orderId}
**Description**: Update order status (admin operations)
**Parameters**:
- `orderId` (path): Order identifier
- `transition` (required): Workflow transition name

**Request Body**:
```json
{
  "transition": "START_PICKING"
}
```

**Response**: Updated order with new status

---

## ShipmentController

**Base Path**: `/ui/shipment`
**Description**: Manages shipment tracking and updates.

### Endpoints:

#### GET /ui/shipment/order/{orderId}
**Description**: Get shipment details by order ID
**Parameters**:
- `orderId` (path): Order identifier

**Response**: Shipment details
```json
{
  "shipmentId": "ship_01HQZX8K9M7N2P3Q4R5S6T7U8V",
  "orderId": "order_01HQZX8K9M7N2P3Q4R5S6T7U8V",
  "status": "PICKING",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "qtyOrdered": 1,
      "qtyPicked": 0,
      "qtyShipped": 0
    }
  ],
  "createdAt": "2025-09-01T10:05:05Z",
  "updatedAt": "2025-09-01T10:05:05Z"
}
```

#### PATCH /ui/shipment/{shipmentId}
**Description**: Update shipment status
**Parameters**:
- `shipmentId` (path): Shipment identifier
- `transition` (required): Workflow transition name

**Request Body**:
```json
{
  "transition": "READY_FOR_DISPATCH"
}
```

**Response**: Updated shipment with new status

---

## Error Responses

All controllers return standardized error responses:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid request data",
    "details": "Product SKU is required"
  },
  "timestamp": "2025-09-01T10:00:00Z",
  "path": "/ui/cart/123/lines"
}
```

## Common HTTP Status Codes

- `200 OK`: Successful operation
- `201 Created`: Resource created successfully
- `400 Bad Request`: Invalid request data
- `404 Not Found`: Resource not found
- `409 Conflict`: Business rule violation
- `500 Internal Server Error`: Server error
