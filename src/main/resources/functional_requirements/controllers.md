# Controller Requirements

## Overview
This document defines the detailed requirements for all REST API controllers in the Cyoda OMS Backend system. Controllers expose UI-facing endpoints under `/ui/**` and interact with Cyoda via server-side EntityService.

**Important Notes**:
- All endpoints are under `/ui/**` prefix
- No browser authentication required
- Server holds Cyoda credentials
- Always return EntityWithMetadata<T> for entity operations
- Use business IDs in URLs, technical UUIDs internally

## Controller Definitions

### 1. ProductController

**Base Path**: `/ui/products`  
**Description**: Manages product catalog operations with search and filtering capabilities.

#### Endpoints

##### GET /ui/products
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
      "description": "High-performance gaming laptop",
      "price": 1299.99,
      "quantityAvailable": 15,
      "category": "electronics",
      "imageUrl": "https://example.com/laptop.jpg"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 25,
  "totalPages": 3
}
```

##### GET /ui/products/{sku}
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
  "entity": {
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
          "description": "High-performance gaming laptop"
        }
      ]
    },
    "media": [
      {
        "type": "image",
        "url": "https://example.com/laptop.jpg",
        "alt": "Gaming Laptop Pro",
        "tags": ["hero"]
      }
    ]
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "ACTIVE"
  }
}
```

---

### 2. CartController

**Base Path**: `/ui/cart`  
**Description**: Manages shopping cart operations.

#### Endpoints

##### POST /ui/cart
**Description**: Create new cart or return existing cart  
**Request Body**: Empty or cart initialization data

**Request Example**:
```
POST /ui/cart
Content-Type: application/json

{}
```

**Response Example**:
```json
{
  "entity": {
    "cartId": "CART-123456",
    "lines": [],
    "totalItems": 0,
    "grandTotal": 0.0,
    "createdAt": "2025-09-07T10:00:00Z",
    "updatedAt": "2025-09-07T10:00:00Z"
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "state": "NEW"
  }
}
```

##### POST /ui/cart/{cartId}/lines
**Description**: Add or increment item in cart  
**Parameters**:
- `cartId` (path): Cart identifier
**Request Body**: Item to add

**Request Example**:
```
POST /ui/cart/CART-123456/lines
Content-Type: application/json

{
  "sku": "LAPTOP-001",
  "qty": 1
}
```

**Response Example**:
```json
{
  "entity": {
    "cartId": "CART-123456",
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
    "updatedAt": "2025-09-07T10:05:00Z"
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "state": "ACTIVE"
  }
}
```

##### PATCH /ui/cart/{cartId}/lines
**Description**: Update item quantity in cart (remove if qty=0)  
**Parameters**:
- `cartId` (path): Cart identifier
**Request Body**: Item to update

**Request Example**:
```
PATCH /ui/cart/CART-123456/lines
Content-Type: application/json

{
  "sku": "LAPTOP-001",
  "qty": 2
}
```

**Response Example**: Same as POST response with updated quantities

##### POST /ui/cart/{cartId}/open-checkout
**Description**: Set cart to checkout mode  
**Parameters**:
- `cartId` (path): Cart identifier

**Request Example**:
```
POST /ui/cart/CART-123456/open-checkout
```

**Response Example**:
```json
{
  "entity": {
    "cartId": "CART-123456",
    "lines": [...],
    "totalItems": 1,
    "grandTotal": 1299.99
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "state": "CHECKING_OUT"
  }
}
```

##### GET /ui/cart/{cartId}
**Description**: Get cart details  
**Parameters**:
- `cartId` (path): Cart identifier

**Request Example**:
```
GET /ui/cart/CART-123456
```

**Response Example**: Same as POST cart response

---

### 3. CheckoutController

**Base Path**: `/ui/checkout`  
**Description**: Manages anonymous checkout process.

#### Endpoints

##### POST /ui/checkout/{cartId}
**Description**: Add guest contact information to cart  
**Parameters**:
- `cartId` (path): Cart identifier
**Request Body**: Guest contact information

**Request Example**:
```
POST /ui/checkout/CART-123456
Content-Type: application/json

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
  "entity": {
    "cartId": "CART-123456",
    "lines": [...],
    "totalItems": 1,
    "grandTotal": 1299.99,
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
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "state": "CHECKING_OUT"
  }
}
```

---

### 4. PaymentController

**Base Path**: `/ui/payment`  
**Description**: Manages dummy payment processing.

#### Endpoints

##### POST /ui/payment/start
**Description**: Start dummy payment process  
**Request Body**: Payment initiation data

**Request Example**:
```
POST /ui/payment/start
Content-Type: application/json

{
  "cartId": "CART-123456"
}
```

**Response Example**:
```json
{
  "paymentId": "PAY-789012",
  "status": "INITIATED",
  "message": "Payment initiated, will auto-approve in ~3 seconds"
}
```

##### GET /ui/payment/{paymentId}
**Description**: Get payment status (for polling)  
**Parameters**:
- `paymentId` (path): Payment identifier

**Request Example**:
```
GET /ui/payment/PAY-789012
```

**Response Example**:
```json
{
  "entity": {
    "paymentId": "PAY-789012",
    "cartId": "CART-123456",
    "amount": 1299.99,
    "provider": "DUMMY",
    "createdAt": "2025-09-07T10:10:00Z",
    "updatedAt": "2025-09-07T10:10:03Z"
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440002",
    "state": "PAID"
  }
}
```

---

### 5. OrderController

**Base Path**: `/ui/order`  
**Description**: Manages order creation and tracking.

#### Endpoints

##### POST /ui/order/create
**Description**: Create order from paid payment  
**Request Body**: Order creation data

**Request Example**:
```
POST /ui/order/create
Content-Type: application/json

{
  "paymentId": "PAY-789012",
  "cartId": "CART-123456"
}
```

**Response Example**:
```json
{
  "orderId": "ORD-345678",
  "orderNumber": "01HF7XJQM2",
  "status": "WAITING_TO_FULFILL",
  "message": "Order created successfully"
}
```

##### GET /ui/order/{orderId}
**Description**: Get order details for confirmation/status  
**Parameters**:
- `orderId` (path): Order identifier

**Request Example**:
```
GET /ui/order/ORD-345678
```

**Response Example**:
```json
{
  "entity": {
    "orderId": "ORD-345678",
    "orderNumber": "01HF7XJQM2",
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
      "phone": "+1234567890",
      "address": {
        "line1": "123 Main Street",
        "city": "New York",
        "postcode": "10001",
        "country": "USA"
      }
    },
    "createdAt": "2025-09-07T10:10:05Z",
    "updatedAt": "2025-09-07T10:10:05Z"
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440003",
    "state": "WAITING_TO_FULFILL"
  }
}
```

##### PATCH /ui/order/{orderId}/status
**Description**: Update order status (for warehouse operations)  
**Parameters**:
- `orderId` (path): Order identifier
**Request Body**: Status update with transition

**Request Example**:
```
PATCH /ui/order/ORD-345678/status
Content-Type: application/json

{
  "transition": "START_PICKING"
}
```

**Response Example**:
```json
{
  "entity": {
    "orderId": "ORD-345678",
    "orderNumber": "01HF7XJQM2",
    "updatedAt": "2025-09-07T10:15:00Z"
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440003",
    "state": "PICKING"
  }
}
```

## Product Search Implementation

### Search Condition Building
Use Cyoda's SearchConditionRequest to combine multiple filters:

```java
// Free-text search: CONTAINS on name OR description
SearchConditionRequest textCondition = SearchConditionRequest.group("OR",
    Condition.of("$.name", "CONTAINS", searchText),
    Condition.of("$.description", "CONTAINS", searchText)
);

// Category filter: EQUALS on category
SearchConditionRequest categoryCondition =
    Condition.of("$.category", "EQUALS", category);

// Price range: GREATER_OR_EQUAL and LESS_OR_EQUAL on price
SearchConditionRequest priceCondition = SearchConditionRequest.group("AND",
    Condition.of("$.price", "GREATER_OR_EQUAL", minPrice),
    Condition.of("$.price", "LESS_OR_EQUAL", maxPrice)
);

// Combine all conditions
SearchConditionRequest finalCondition = SearchConditionRequest.group("AND",
    textCondition, categoryCondition, priceCondition
);
```

### Slim DTO Mapping
Map full Product entity to slim DTO for list view:

```java
public class ProductSlimDto {
    private String sku;
    private String name;
    private String description;
    private Double price;
    private Integer quantityAvailable;
    private String category;
    private String imageUrl; // Extract from media array
}
```

## Error Handling Standards

### HTTP Status Codes
- **200 OK**: Successful GET, PATCH operations
- **201 Created**: Successful POST operations
- **400 Bad Request**: Invalid request data, validation failures
- **404 Not Found**: Entity not found by business ID
- **409 Conflict**: Business rule violations, state conflicts
- **500 Internal Server Error**: Unexpected system errors

### Error Response Format
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Cart must have at least one item",
    "details": {
      "field": "lines",
      "value": "[]"
    }
  },
  "timestamp": "2025-09-07T10:00:00Z",
  "path": "/ui/cart/CART-123456/open-checkout"
}
```

## Workflow Transition Mapping

### Cart Transitions
- Add first item: `CREATE_ON_FIRST_ADD` (automatic)
- Add item: `ADD_ITEM` (manual)
- Update item: `UPDATE_ITEM` (manual)
- Remove item: `REMOVE_ITEM` (manual)
- Open checkout: `OPEN_CHECKOUT` (manual)
- Complete checkout: `CHECKOUT` (manual)

### Payment Transitions
- Start payment: `START_DUMMY_PAYMENT` (automatic)
- Auto-approve: `AUTO_MARK_PAID` (automatic, 3s delay)

### Order Transitions
- Create order: `CREATE_ORDER_FROM_PAID` (automatic)
- Start picking: `START_PICKING` (manual)
- Ready to send: `READY_TO_SEND` (manual)
- Mark sent: `MARK_SENT` (manual)
- Mark delivered: `MARK_DELIVERED` (manual)

## Controller Implementation Notes

1. **EntityService Usage**: Use appropriate performance-optimized methods
   - `getById()` for technical UUIDs (FASTEST)
   - `findByBusinessId()` for user-facing IDs (MEDIUM)
   - `search()` for complex queries (SLOWEST)

2. **Error Handling**: Return appropriate HTTP status codes and error messages

3. **Validation**: Validate request parameters and body data before EntityService calls

4. **Business ID Mapping**: Use business IDs in URLs, technical UUIDs for EntityService operations

5. **Transition Names**: Align with workflow definitions, use null for state-only updates

6. **CORS**: Enable CORS for browser access with `@CrossOrigin(origins = "*")`

7. **Response Format**: Always return EntityWithMetadata<T> for entity operations

8. **Search Implementation**: Use Cyoda condition queries for product filtering

9. **Pagination**: Implement proper pagination for product search results

10. **State Validation**: Check entity states before allowing transitions
