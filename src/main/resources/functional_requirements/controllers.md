# Controller Requirements

## Overview
This document defines the detailed requirements for all REST API controllers in the Cyoda OMS Backend system. Controllers expose UI-facing endpoints under `/ui/**` and handle all browser interactions without requiring authentication.

## ProductController

**Base Path:** `/ui/products`  
**Purpose:** Manages product catalog operations for the UI

### GET /ui/products
**Description:** Search and filter products with pagination  
**Parameters:**
- `search` (string, optional): Free-text search on name/description
- `category` (string, optional): Filter by product category
- `minPrice` (number, optional): Minimum price filter
- `maxPrice` (number, optional): Maximum price filter
- `page` (integer, optional, default=0): Page number for pagination
- `pageSize` (integer, optional, default=20): Number of items per page

**Request Example:**
```
GET /ui/products?search=laptop&category=electronics&minPrice=500&maxPrice=2000&page=0&pageSize=10
```

**Response Example:**
```json
{
  "products": [
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
  "pagination": {
    "page": 0,
    "pageSize": 10,
    "totalElements": 25,
    "totalPages": 3
  }
}
```

### GET /ui/products/{sku}
**Description:** Get full product details by SKU  
**Parameters:**
- `sku` (string, required): Product SKU

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

## CartController

**Base Path:** `/ui/cart`  
**Purpose:** Manages shopping cart operations for anonymous users

### POST /ui/cart
**Description:** Create new cart or return existing cart  
**Transition:** CREATE_ON_FIRST_ADD (if new cart)

**Request Example:**
```json
{
  "sku": "LAPTOP-001",
  "qty": 1
}
```

**Response Example:**
```json
{
  "cartId": "cart-12345",
  "status": "ACTIVE",
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

### POST /ui/cart/{cartId}/lines
**Description:** Add or increment item in cart  
**Transition:** ADD_ITEM  
**Parameters:**
- `cartId` (string, required): Cart identifier

**Request Example:**
```json
{
  "sku": "LAPTOP-001",
  "qty": 2
}
```

**Response Example:**
```json
{
  "cartId": "cart-12345",
  "status": "ACTIVE",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "name": "Gaming Laptop Pro",
      "price": 1299.99,
      "qty": 3
    }
  ],
  "totalItems": 3,
  "grandTotal": 3899.97
}
```

### PATCH /ui/cart/{cartId}/lines
**Description:** Update item quantity in cart (remove if qty=0)  
**Transition:** UPDATE_ITEM or REMOVE_ITEM  
**Parameters:**
- `cartId` (string, required): Cart identifier

**Request Example:**
```json
{
  "sku": "LAPTOP-001",
  "qty": 1
}
```

**Response Example:**
```json
{
  "cartId": "cart-12345",
  "status": "ACTIVE",
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

### POST /ui/cart/{cartId}/open-checkout
**Description:** Initiate checkout process  
**Transition:** OPEN_CHECKOUT  
**Parameters:**
- `cartId` (string, required): Cart identifier

**Request Example:**
```
POST /ui/cart/cart-12345/open-checkout
```

**Response Example:**
```json
{
  "cartId": "cart-12345",
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
**Description:** Get cart details  
**Parameters:**
- `cartId` (string, required): Cart identifier

**Request Example:**
```
GET /ui/cart/cart-12345
```

**Response Example:**
```json
{
  "cartId": "cart-12345",
  "status": "ACTIVE",
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

## CheckoutController

**Base Path:** `/ui/checkout`  
**Purpose:** Manages anonymous checkout process

### POST /ui/checkout/{cartId}
**Description:** Attach guest contact information to cart  
**Transition:** null (updates cart without state change)  
**Parameters:**
- `cartId` (string, required): Cart identifier

**Request Example:**
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

**Response Example:**
```json
{
  "cartId": "cart-12345",
  "status": "CHECKING_OUT",
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

## PaymentController

**Base Path:** `/ui/payment`  
**Purpose:** Manages dummy payment processing

### POST /ui/payment/start
**Description:** Start dummy payment process  
**Transition:** START_DUMMY_PAYMENT

**Request Example:**
```json
{
  "cartId": "cart-12345"
}
```

**Response Example:**
```json
{
  "paymentId": "pay-67890",
  "status": "INITIATED",
  "amount": 1299.99,
  "provider": "DUMMY"
}
```

### GET /ui/payment/{paymentId}
**Description:** Poll payment status  
**Parameters:**
- `paymentId` (string, required): Payment identifier

**Request Example:**
```
GET /ui/payment/pay-67890
```

**Response Example:**
```json
{
  "paymentId": "pay-67890",
  "cartId": "cart-12345",
  "status": "PAID",
  "amount": 1299.99,
  "provider": "DUMMY"
}
```

## OrderController

**Base Path:** `/ui/order`
**Purpose:** Manages order creation and tracking

### POST /ui/order/create
**Description:** Create order from paid cart
**Transition:** CREATE_ORDER_FROM_PAID

**Request Example:**
```json
{
  "paymentId": "pay-67890",
  "cartId": "cart-12345"
}
```

**Response Example:**
```json
{
  "orderId": "order-abc123",
  "orderNumber": "01HZXK2M3N4P5Q",
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
  }
}
```

### GET /ui/order/{orderId}
**Description:** Get order details for confirmation/status
**Parameters:**
- `orderId` (string, required): Order identifier

**Request Example:**
```
GET /ui/order/order-abc123
```

**Response Example:**
```json
{
  "orderId": "order-abc123",
  "orderNumber": "01HZXK2M3N4P5Q",
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
    "phone": "+44 20 1234 5678",
    "address": {
      "line1": "123 Main Street",
      "city": "London",
      "postcode": "SW1A 1AA",
      "country": "UK"
    }
  },
  "createdAt": "2025-09-02T10:30:00Z",
  "updatedAt": "2025-09-02T11:15:00Z"
}
```

### POST /ui/order/{orderId}/transition
**Description:** Manually trigger order state transitions (for demo/admin purposes)
**Transition:** START_PICKING, READY_TO_SEND, MARK_SENT, MARK_DELIVERED
**Parameters:**
- `orderId` (string, required): Order identifier

**Request Example:**
```json
{
  "transitionName": "START_PICKING"
}
```

**Response Example:**
```json
{
  "orderId": "order-abc123",
  "status": "PICKING",
  "message": "Order transition successful"
}
```

## ShipmentController

**Base Path:** `/ui/shipment`
**Purpose:** Manages shipment tracking and updates

### GET /ui/shipment/by-order/{orderId}
**Description:** Get shipment details by order ID
**Parameters:**
- `orderId` (string, required): Order identifier

**Request Example:**
```
GET /ui/shipment/by-order/order-abc123
```

**Response Example:**
```json
{
  "shipmentId": "ship-xyz789",
  "orderId": "order-abc123",
  "status": "PICKING",
  "lines": [
    {
      "sku": "LAPTOP-001",
      "qtyOrdered": 1,
      "qtyPicked": 0,
      "qtyShipped": 0
    }
  ],
  "createdAt": "2025-09-02T10:30:00Z",
  "updatedAt": "2025-09-02T10:30:00Z"
}
```

### POST /ui/shipment/{shipmentId}/transition
**Description:** Manually trigger shipment state transitions (for demo/admin purposes)
**Transition:** READY_TO_SEND, MARK_SENT, MARK_DELIVERED
**Parameters:**
- `shipmentId` (string, required): Shipment identifier

**Request Example:**
```json
{
  "transitionName": "READY_TO_SEND"
}
```

**Response Example:**
```json
{
  "shipmentId": "ship-xyz789",
  "status": "WAITING_TO_SEND",
  "message": "Shipment transition successful"
}
```

## Error Handling

All controllers should implement consistent error handling:

### Standard Error Response Format
```json
{
  "error": {
    "code": "ENTITY_NOT_FOUND",
    "message": "Cart with ID cart-12345 not found",
    "timestamp": "2025-09-02T10:30:00Z"
  }
}
```

### Common Error Codes
- `ENTITY_NOT_FOUND`: Requested entity does not exist
- `INVALID_STATE`: Entity is not in correct state for operation
- `VALIDATION_ERROR`: Request data validation failed
- `INSUFFICIENT_STOCK`: Product stock is insufficient
- `PAYMENT_NOT_PAID`: Payment is not in PAID state
- `BUSINESS_RULE_VIOLATION`: Business rule validation failed

## Security and Validation

### Input Validation
- Validate all request parameters and body content
- Sanitize string inputs to prevent injection attacks
- Validate numeric ranges and formats
- Check required fields are present

### Business Rule Validation
- Verify entity states before operations
- Check stock availability before cart operations
- Validate payment status before order creation
- Ensure data consistency across operations

### Rate Limiting
- Implement appropriate rate limiting for public endpoints
- Consider higher limits for cart operations vs. order creation
- Log suspicious activity patterns

## Implementation Notes

### Transition Management
- Controllers should specify transition names when updating entity states
- Use null transition when updating entity data without state change
- Validate current state allows the requested transition

### Response Consistency
- Always return updated entity state after operations
- Include relevant metadata (timestamps, totals, etc.)
- Use consistent field naming across all endpoints

### Async Operations
- Payment auto-approval runs asynchronously
- Order creation may involve multiple entity updates
- Provide appropriate status polling endpoints

### Caching Considerations
- Product catalog data can be cached with appropriate TTL
- Cart data should not be cached due to frequent updates
- Order/shipment data can be cached once created

### API Alignment with User Requirements
- All endpoints exactly match the specified API requirements
- Product search supports category, free-text, and price range filters
- Cart operations support anonymous users without authentication
- Payment uses dummy provider with 3-second auto-approval
- Order creation follows the specified workflow with stock decrement
- Single shipment per order as per demo requirements
