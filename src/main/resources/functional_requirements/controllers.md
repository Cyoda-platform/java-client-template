# Controllers Requirements for Purrfect Pets API

## Overview
The Purrfect Pets API provides REST endpoints for managing pets, categories, tags, and orders. Each entity has its own controller with full CRUD operations and workflow transition support.

## 1. PetController

### Base Path: `/api/v1/pets`

### Endpoints

#### GET /api/v1/pets
**Description**: Get all pets with optional filtering  
**Parameters**: 
- `status` (optional): Filter by pet status (available, reserved, adopted, etc.)
- `category` (optional): Filter by category name
- `tags` (optional): Filter by tag names (comma-separated)
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 20)

**Response Example**:
```json
{
  "content": [
    {
      "id": 1,
      "name": "Buddy",
      "category": {
        "id": 1,
        "name": "Dogs"
      },
      "photoUrls": ["https://example.com/buddy1.jpg"],
      "tags": [
        {
          "id": 1,
          "name": "friendly"
        }
      ],
      "description": "A friendly golden retriever",
      "price": 500.00,
      "birthDate": "2022-03-15",
      "breed": "Golden Retriever",
      "color": "Golden",
      "weight": 25.5,
      "vaccinated": true,
      "neutered": false,
      "microchipped": true,
      "specialNeeds": null,
      "state": "available"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

#### GET /api/v1/pets/{id}
**Description**: Get pet by ID  
**Parameters**: 
- `id` (path): Pet ID

**Response Example**:
```json
{
  "id": 1,
  "name": "Buddy",
  "category": {
    "id": 1,
    "name": "Dogs",
    "description": "Domestic dogs",
    "imageUrl": "https://example.com/dogs.jpg",
    "active": true
  },
  "photoUrls": ["https://example.com/buddy1.jpg", "https://example.com/buddy2.jpg"],
  "tags": [
    {
      "id": 1,
      "name": "friendly",
      "color": "#4CAF50",
      "description": "Pet is friendly with people",
      "active": true
    }
  ],
  "description": "A friendly golden retriever looking for a loving home",
  "price": 500.00,
  "birthDate": "2022-03-15",
  "breed": "Golden Retriever",
  "color": "Golden",
  "weight": 25.5,
  "vaccinated": true,
  "neutered": false,
  "microchipped": true,
  "specialNeeds": null,
  "state": "available"
}
```

#### POST /api/v1/pets
**Description**: Create a new pet  
**Transition**: Triggers `initialize_pet` transition automatically

**Request Example**:
```json
{
  "name": "Max",
  "category": {
    "id": 1
  },
  "photoUrls": ["https://example.com/max1.jpg"],
  "tags": [
    {
      "id": 1
    },
    {
      "id": 2
    }
  ],
  "description": "A playful labrador puppy",
  "price": 800.00,
  "birthDate": "2023-06-10",
  "breed": "Labrador",
  "color": "Black",
  "weight": 15.0,
  "vaccinated": true,
  "neutered": false,
  "microchipped": true,
  "specialNeeds": null
}
```

**Response Example**:
```json
{
  "id": 2,
  "name": "Max",
  "state": "draft",
  "message": "Pet created successfully"
}
```

#### PUT /api/v1/pets/{id}
**Description**: Update pet with optional state transition  
**Parameters**: 
- `id` (path): Pet ID
- `transition` (query, optional): Transition name for state change

**Request Example** (with transition):
```json
{
  "name": "Max Updated",
  "category": {
    "id": 1
  },
  "photoUrls": ["https://example.com/max1.jpg", "https://example.com/max2.jpg"],
  "tags": [
    {
      "id": 1
    }
  ],
  "description": "An updated playful labrador puppy",
  "price": 850.00,
  "birthDate": "2023-06-10",
  "breed": "Labrador",
  "color": "Black",
  "weight": 16.0,
  "vaccinated": true,
  "neutered": true,
  "microchipped": true,
  "specialNeeds": null
}
```

**URL**: `PUT /api/v1/pets/2?transition=submit_for_review`

**Response Example**:
```json
{
  "id": 2,
  "name": "Max Updated",
  "state": "pending_review",
  "message": "Pet updated and submitted for review"
}
```

#### DELETE /api/v1/pets/{id}
**Description**: Delete pet (archive)  
**Parameters**: 
- `id` (path): Pet ID
- `transition`: Uses `archive_pet` transition

**Response Example**:
```json
{
  "id": 2,
  "message": "Pet archived successfully"
}
```

#### POST /api/v1/pets/{id}/reserve
**Description**: Reserve a pet for a customer  
**Parameters**: 
- `id` (path): Pet ID
- `transition`: Uses `reserve_pet` transition

**Request Example**:
```json
{
  "customerName": "John Doe",
  "customerEmail": "john.doe@example.com",
  "customerPhone": "+1-555-123-4567",
  "customerAddress": "123 Main St, Anytown, ST 12345"
}
```

**Response Example**:
```json
{
  "id": 1,
  "state": "reserved",
  "message": "Pet reserved successfully",
  "reservationExpiry": "2024-01-16T10:00:00Z"
}
```

## 2. CategoryController

### Base Path: `/api/v1/categories`

### Endpoints

#### GET /api/v1/categories
**Description**: Get all categories  
**Parameters**: 
- `active` (optional): Filter by active status (true/false)

**Response Example**:
```json
[
  {
    "id": 1,
    "name": "Dogs",
    "description": "Domestic dogs of all breeds",
    "imageUrl": "https://example.com/dogs.jpg",
    "active": true,
    "state": "active"
  },
  {
    "id": 2,
    "name": "Cats",
    "description": "Domestic cats of all breeds",
    "imageUrl": "https://example.com/cats.jpg",
    "active": true,
    "state": "active"
  }
]
```

#### GET /api/v1/categories/{id}
**Description**: Get category by ID  
**Parameters**: 
- `id` (path): Category ID

**Response Example**:
```json
{
  "id": 1,
  "name": "Dogs",
  "description": "Domestic dogs of all breeds and sizes",
  "imageUrl": "https://example.com/dogs.jpg",
  "active": true,
  "state": "active"
}
```

#### POST /api/v1/categories
**Description**: Create a new category  
**Transition**: Triggers `activate_category` transition automatically

**Request Example**:
```json
{
  "name": "Birds",
  "description": "Various bird species",
  "imageUrl": "https://example.com/birds.jpg"
}
```

**Response Example**:
```json
{
  "id": 3,
  "name": "Birds",
  "state": "active",
  "message": "Category created and activated successfully"
}
```

#### PUT /api/v1/categories/{id}
**Description**: Update category with optional state transition  
**Parameters**: 
- `id` (path): Category ID
- `transition` (query, optional): Transition name

**Request Example**:
```json
{
  "name": "Birds Updated",
  "description": "Various bird species including parrots and canaries",
  "imageUrl": "https://example.com/birds-updated.jpg"
}
```

**URL**: `PUT /api/v1/categories/3?transition=deactivate_category`

**Response Example**:
```json
{
  "id": 3,
  "name": "Birds Updated",
  "state": "inactive",
  "message": "Category updated and deactivated"
}
```

#### DELETE /api/v1/categories/{id}
**Description**: Delete category (archive)  
**Parameters**: 
- `id` (path): Category ID
- `transition`: Uses `archive_category` transition

**Response Example**:
```json
{
  "id": 3,
  "message": "Category archived successfully"
}
```

## 3. TagController

### Base Path: `/api/v1/tags`

### Endpoints

#### GET /api/v1/tags
**Description**: Get all tags  
**Parameters**: 
- `active` (optional): Filter by active status (true/false)

**Response Example**:
```json
[
  {
    "id": 1,
    "name": "friendly",
    "color": "#4CAF50",
    "description": "Pet is friendly with people",
    "active": true,
    "state": "active"
  },
  {
    "id": 2,
    "name": "trained",
    "color": "#2196F3",
    "description": "Pet is house trained",
    "active": true,
    "state": "active"
  }
]
```

#### POST /api/v1/tags
**Description**: Create a new tag  
**Transition**: Triggers `activate_tag` transition automatically

**Request Example**:
```json
{
  "name": "hypoallergenic",
  "color": "#FF9800",
  "description": "Pet is suitable for people with allergies"
}
```

**Response Example**:
```json
{
  "id": 3,
  "name": "hypoallergenic",
  "state": "active",
  "message": "Tag created and activated successfully"
}
```

#### PUT /api/v1/tags/{id}
**Description**: Update tag with optional state transition  
**Parameters**: 
- `id` (path): Tag ID
- `transition` (query, optional): Transition name

**Request Example**:
```json
{
  "name": "hypoallergenic-updated",
  "color": "#FF5722",
  "description": "Pet is suitable for people with severe allergies"
}
```

**URL**: `PUT /api/v1/tags/3?transition=deactivate_tag`

**Response Example**:
```json
{
  "id": 3,
  "name": "hypoallergenic-updated",
  "state": "inactive",
  "message": "Tag updated and deactivated"
}
```

#### DELETE /api/v1/tags/{id}
**Description**: Delete tag (archive)
**Parameters**:
- `id` (path): Tag ID
- `transition`: Uses `archive_tag` transition

**Response Example**:
```json
{
  "id": 3,
  "message": "Tag archived successfully"
}
```

## 4. OrderController

### Base Path: `/api/v1/orders`

### Endpoints

#### GET /api/v1/orders
**Description**: Get all orders with optional filtering
**Parameters**:
- `status` (optional): Filter by order status (pending, confirmed, shipped, etc.)
- `customerEmail` (optional): Filter by customer email
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 20)

**Response Example**:
```json
{
  "content": [
    {
      "id": 1,
      "petId": 1,
      "customerName": "John Doe",
      "customerEmail": "john.doe@example.com",
      "customerPhone": "+1-555-123-4567",
      "customerAddress": "123 Main St, Anytown, ST 12345",
      "quantity": 1,
      "orderDate": "2024-01-15T10:00:00Z",
      "shipDate": null,
      "totalAmount": 500.00,
      "paymentMethod": "credit_card",
      "paymentStatus": "pending",
      "shippingMethod": "pickup",
      "trackingNumber": null,
      "notes": "Customer prefers morning pickup",
      "complete": false,
      "state": "pending"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

#### GET /api/v1/orders/{id}
**Description**: Get order by ID
**Parameters**:
- `id` (path): Order ID

**Response Example**:
```json
{
  "id": 1,
  "petId": 1,
  "customerName": "John Doe",
  "customerEmail": "john.doe@example.com",
  "customerPhone": "+1-555-123-4567",
  "customerAddress": "123 Main St, Anytown, ST 12345",
  "quantity": 1,
  "orderDate": "2024-01-15T10:00:00Z",
  "shipDate": null,
  "totalAmount": 500.00,
  "paymentMethod": "credit_card",
  "paymentStatus": "pending",
  "shippingMethod": "pickup",
  "trackingNumber": null,
  "notes": "Customer prefers morning pickup",
  "complete": false,
  "state": "pending"
}
```

#### POST /api/v1/orders
**Description**: Create a new order
**Transition**: Triggers `create_order` transition automatically

**Request Example**:
```json
{
  "petId": 1,
  "customerName": "Jane Smith",
  "customerEmail": "jane.smith@example.com",
  "customerPhone": "+1-555-987-6543",
  "customerAddress": "456 Oak Ave, Another City, ST 67890",
  "quantity": 1,
  "paymentMethod": "debit_card",
  "shippingMethod": "delivery",
  "notes": "Please call before delivery"
}
```

**Response Example**:
```json
{
  "id": 2,
  "petId": 1,
  "customerName": "Jane Smith",
  "state": "pending",
  "totalAmount": 500.00,
  "message": "Order created successfully"
}
```

#### PUT /api/v1/orders/{id}
**Description**: Update order with optional state transition
**Parameters**:
- `id` (path): Order ID
- `transition` (query, optional): Transition name

**Request Example** (confirming order):
```json
{
  "customerName": "Jane Smith",
  "customerEmail": "jane.smith@example.com",
  "customerPhone": "+1-555-987-6543",
  "customerAddress": "456 Oak Ave, Another City, ST 67890",
  "quantity": 1,
  "paymentMethod": "debit_card",
  "paymentStatus": "confirmed",
  "shippingMethod": "delivery",
  "notes": "Please call before delivery - payment confirmed"
}
```

**URL**: `PUT /api/v1/orders/2?transition=confirm_order`

**Response Example**:
```json
{
  "id": 2,
  "state": "confirmed",
  "message": "Order confirmed successfully"
}
```

#### DELETE /api/v1/orders/{id}
**Description**: Cancel order
**Parameters**:
- `id` (path): Order ID
- `transition`: Uses `cancel_order` transition

**Response Example**:
```json
{
  "id": 2,
  "state": "cancelled",
  "message": "Order cancelled successfully"
}
```

#### POST /api/v1/orders/{id}/ship
**Description**: Ship an order
**Parameters**:
- `id` (path): Order ID
- `transition`: Uses `ship_order` transition

**Request Example**:
```json
{
  "shippingMethod": "express_delivery",
  "trackingNumber": "TRK123456789"
}
```

**Response Example**:
```json
{
  "id": 2,
  "state": "shipped",
  "trackingNumber": "TRK123456789",
  "shipDate": "2024-01-16T14:30:00Z",
  "message": "Order shipped successfully"
}
```

#### POST /api/v1/orders/{id}/deliver
**Description**: Mark order as delivered
**Parameters**:
- `id` (path): Order ID
- `transition`: Uses `deliver_order` transition

**Response Example**:
```json
{
  "id": 2,
  "state": "delivered",
  "complete": true,
  "message": "Order delivered successfully"
}
```

#### POST /api/v1/orders/{id}/refund
**Description**: Process order refund
**Parameters**:
- `id` (path): Order ID
- `transition`: Uses `refund_order` transition

**Request Example**:
```json
{
  "refundReason": "Customer changed mind",
  "refundAmount": 500.00
}
```

**Response Example**:
```json
{
  "id": 2,
  "state": "refunded",
  "paymentStatus": "refunded",
  "message": "Order refunded successfully"
}
```

## API Standards and Guidelines

### HTTP Status Codes
- `200 OK`: Successful GET, PUT operations
- `201 Created`: Successful POST operations
- `204 No Content`: Successful DELETE operations
- `400 Bad Request`: Invalid request data or validation errors
- `404 Not Found`: Resource not found
- `409 Conflict`: Business rule violations or state conflicts
- `500 Internal Server Error`: Server errors

### Error Response Format
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Pet name is required",
    "details": [
      {
        "field": "name",
        "message": "Name cannot be empty"
      }
    ],
    "timestamp": "2024-01-15T10:00:00Z"
  }
}
```

### Pagination Format
All list endpoints support pagination with the following parameters:
- `page`: Page number (0-based, default: 0)
- `size`: Page size (default: 20, max: 100)
- `sort`: Sort criteria (e.g., "name,asc" or "createdDate,desc")

### Authentication and Authorization
- All endpoints require valid authentication tokens
- Role-based access control for different operations
- Admin roles required for archival operations
- Customer roles can only access their own orders

### Rate Limiting
- 100 requests per minute per API key
- 1000 requests per hour per API key
- Burst allowance of 20 requests

### Content Types
- Request Content-Type: `application/json`
- Response Content-Type: `application/json`
- Character encoding: UTF-8
