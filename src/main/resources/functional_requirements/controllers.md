# Purrfect Pets - Controller Requirements

## Overview
This document defines the REST API controller requirements for the Purrfect Pets system. Each entity has its own controller with CRUD operations and workflow transition endpoints.

## 1. PetController

**Base Path**: `/api/pets`  
**Entity**: Pet  
**Purpose**: Manage pet inventory and availability

### Endpoints

#### GET /api/pets
**Purpose**: Get all pets with optional filtering  
**Parameters**: 
- `status` (optional): Filter by pet status (available, pending, sold)
- `categoryId` (optional): Filter by category ID
- `page` (optional, default=0): Page number
- `size` (optional, default=20): Page size

**Response Example**:
```json
{
  "content": [
    {
      "uuid": "123e4567-e89b-12d3-a456-426614174000",
      "entity": {
        "petId": "PET001",
        "name": "Fluffy",
        "categoryId": "CAT001",
        "price": 299.99,
        "breed": "Persian",
        "age": 12
      },
      "meta": {
        "state": "available",
        "version": 1
      }
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

#### GET /api/pets/{petId}
**Purpose**: Get pet by ID  
**Parameters**: `petId` (path parameter)

**Response Example**:
```json
{
  "uuid": "123e4567-e89b-12d3-a456-426614174000",
  "entity": {
    "petId": "PET001",
    "name": "Fluffy",
    "categoryId": "CAT001",
    "photoUrls": ["https://example.com/fluffy1.jpg"],
    "tags": [{"id": "TAG001", "name": "friendly"}],
    "price": 299.99,
    "breed": "Persian",
    "age": 12,
    "description": "Beautiful Persian cat",
    "weight": 4.5,
    "color": "white",
    "vaccinated": true
  },
  "meta": {
    "state": "available",
    "version": 1
  }
}
```

#### POST /api/pets
**Purpose**: Create new pet  
**Request Example**:
```json
{
  "petId": "PET002",
  "name": "Buddy",
  "categoryId": "DOG001",
  "photoUrls": ["https://example.com/buddy1.jpg"],
  "tags": [{"id": "TAG002", "name": "playful"}],
  "price": 450.00,
  "breed": "Golden Retriever",
  "age": 8,
  "description": "Friendly Golden Retriever puppy",
  "weight": 3.2,
  "color": "golden",
  "vaccinated": true
}
```

#### PUT /api/pets/{petId}
**Purpose**: Update pet  
**Parameters**: 
- `petId` (path parameter)
- `transitionName` (optional query parameter): Workflow transition to execute

**Request Example**:
```json
{
  "name": "Buddy Updated",
  "price": 475.00,
  "description": "Updated description"
}
```

#### PUT /api/pets/{petId}/reserve
**Purpose**: Reserve pet (transition to pending)  
**Parameters**: `petId` (path parameter)  
**Transition**: `reserve_pet`

**Request Example**:
```json
{
  "customerId": "USER001",
  "reservationNotes": "Customer interested, holding for 24 hours"
}
```

#### PUT /api/pets/{petId}/cancel-reservation
**Purpose**: Cancel pet reservation (transition to available)  
**Parameters**: `petId` (path parameter)  
**Transition**: `cancel_reservation`

#### DELETE /api/pets/{petId}
**Purpose**: Delete pet (soft delete)

## 2. CategoryController

**Base Path**: `/api/categories`  
**Entity**: Category  
**Purpose**: Manage pet categories

### Endpoints

#### GET /api/categories
**Purpose**: Get all categories  
**Parameters**: 
- `status` (optional): Filter by status (active, inactive)

**Response Example**:
```json
[
  {
    "uuid": "456e7890-e89b-12d3-a456-426614174001",
    "entity": {
      "categoryId": "CAT001",
      "name": "Cats",
      "description": "Feline companions",
      "imageUrl": "https://example.com/cats.jpg"
    },
    "meta": {
      "state": "active",
      "version": 1
    }
  }
]
```

#### GET /api/categories/{categoryId}
**Purpose**: Get category by ID

#### POST /api/categories
**Purpose**: Create new category  
**Request Example**:
```json
{
  "categoryId": "BIRD001",
  "name": "Birds",
  "description": "Feathered friends",
  "imageUrl": "https://example.com/birds.jpg"
}
```

#### PUT /api/categories/{categoryId}
**Purpose**: Update category  
**Parameters**: 
- `categoryId` (path parameter)
- `transitionName` (optional query parameter)

#### PUT /api/categories/{categoryId}/deactivate
**Purpose**: Deactivate category  
**Transition**: `deactivate_category`

#### PUT /api/categories/{categoryId}/activate
**Purpose**: Activate category  
**Transition**: `reactivate_category`

## 3. OrderController

**Base Path**: `/api/orders`  
**Entity**: Order  
**Purpose**: Manage customer orders

### Endpoints

#### GET /api/orders
**Purpose**: Get all orders with filtering  
**Parameters**: 
- `userId` (optional): Filter by user ID
- `status` (optional): Filter by status (placed, approved, shipped, delivered, cancelled)
- `page` (optional, default=0): Page number
- `size` (optional, default=20): Page size

#### GET /api/orders/{orderId}
**Purpose**: Get order by ID

**Response Example**:
```json
{
  "uuid": "789e0123-e89b-12d3-a456-426614174002",
  "entity": {
    "orderId": "ORD001",
    "userId": "USER001",
    "items": [
      {
        "petId": "PET001",
        "petName": "Fluffy",
        "quantity": 1,
        "unitPrice": 299.99,
        "totalPrice": 299.99
      }
    ],
    "totalAmount": 299.99,
    "orderDate": "2024-01-15T10:30:00",
    "shippingAddress": {
      "street": "123 Main St",
      "city": "Anytown",
      "state": "CA",
      "zipCode": "12345",
      "country": "USA"
    },
    "paymentMethod": "credit_card"
  },
  "meta": {
    "state": "placed",
    "version": 1
  }
}
```

#### POST /api/orders
**Purpose**: Create new order  
**Request Example**:
```json
{
  "orderId": "ORD002",
  "userId": "USER001",
  "items": [
    {
      "petId": "PET002",
      "petName": "Buddy",
      "quantity": 1,
      "unitPrice": 450.00,
      "totalPrice": 450.00
    }
  ],
  "totalAmount": 450.00,
  "shippingAddress": {
    "street": "456 Oak Ave",
    "city": "Somewhere",
    "state": "NY",
    "zipCode": "67890",
    "country": "USA"
  },
  "paymentMethod": "debit_card",
  "notes": "Please handle with care"
}
```

#### PUT /api/orders/{orderId}/approve
**Purpose**: Approve order  
**Transition**: `approve_order`

#### PUT /api/orders/{orderId}/ship
**Purpose**: Ship order  
**Transition**: `ship_order`  
**Request Example**:
```json
{
  "trackingNumber": "TRK123456789",
  "carrier": "FedEx",
  "estimatedDelivery": "2024-01-20T15:00:00"
}
```

#### PUT /api/orders/{orderId}/cancel
**Purpose**: Cancel order  
**Transition**: `cancel_order` or `cancel_approved_order` (based on current state)  
**Request Example**:
```json
{
  "reason": "Customer requested cancellation",
  "refundAmount": 450.00
}
```

#### PUT /api/orders/{orderId}/confirm-delivery
**Purpose**: Confirm delivery  
**Transition**: `confirm_delivery`

## 4. UserController

**Base Path**: `/api/users`  
**Entity**: User  
**Purpose**: Manage user accounts

### Endpoints

#### GET /api/users
**Purpose**: Get all users (admin only)  
**Parameters**: 
- `status` (optional): Filter by status (active, inactive, suspended)

#### GET /api/users/{userId}
**Purpose**: Get user by ID

**Response Example**:
```json
{
  "uuid": "abc1234-e89b-12d3-a456-426614174003",
  "entity": {
    "userId": "USER001",
    "username": "john_doe",
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phone": "+1-555-123-4567",
    "addresses": [
      {
        "street": "123 Main St",
        "city": "Anytown",
        "state": "CA",
        "zipCode": "12345",
        "country": "USA",
        "isDefault": true
      }
    ],
    "preferences": {
      "favoriteCategories": ["CAT001", "DOG001"],
      "newsletter": true,
      "notifications": true
    }
  },
  "meta": {
    "state": "active",
    "version": 1
  }
}
```

#### POST /api/users
**Purpose**: Create new user  
**Request Example**:
```json
{
  "userId": "USER002",
  "username": "jane_smith",
  "firstName": "Jane",
  "lastName": "Smith",
  "email": "jane.smith@example.com",
  "phone": "+1-555-987-6543",
  "addresses": [
    {
      "street": "789 Pine St",
      "city": "Elsewhere",
      "state": "TX",
      "zipCode": "54321",
      "country": "USA",
      "isDefault": true
    }
  ],
  "dateOfBirth": "1990-05-15",
  "preferences": {
    "favoriteCategories": ["BIRD001"],
    "newsletter": false,
    "notifications": true
  }
}
```

#### PUT /api/users/{userId}
**Purpose**: Update user  
**Parameters**: 
- `userId` (path parameter)
- `transitionName` (optional query parameter)

#### PUT /api/users/{userId}/suspend
**Purpose**: Suspend user account  
**Transition**: `suspend_user`  
**Request Example**:
```json
{
  "reason": "Policy violation",
  "suspensionDuration": "30 days"
}
```

#### PUT /api/users/{userId}/unsuspend
**Purpose**: Remove suspension  
**Transition**: `unsuspend_user`

#### PUT /api/users/{userId}/deactivate
**Purpose**: Deactivate user account  
**Transition**: `deactivate_user`

## Common Response Patterns

### Success Response
- Status: 200 OK
- Body: EntityWithMetadata object or array

### Error Responses
- 400 Bad Request: Invalid input data
- 404 Not Found: Entity not found
- 409 Conflict: Invalid state transition
- 500 Internal Server Error: Server error

### Pagination Response
```json
{
  "content": [...],
  "totalElements": 100,
  "totalPages": 5,
  "size": 20,
  "number": 0
}
```

## Implementation Notes

1. **Transition Parameters**: Update endpoints should accept optional `transitionName` query parameter
2. **Validation**: All endpoints should validate input data before processing
3. **Error Handling**: Return appropriate HTTP status codes and error messages
4. **Security**: Implement proper authentication and authorization
5. **Logging**: Log all API requests and responses for audit purposes
6. **CORS**: Enable CORS for frontend integration
7. **Swagger**: Document all endpoints with Swagger/OpenAPI annotations
