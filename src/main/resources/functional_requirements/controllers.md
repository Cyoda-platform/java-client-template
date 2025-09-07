# Purrfect Pets - Controller Requirements

## Overview
This document defines the REST API controllers for the Purrfect Pets application. Each controller provides CRUD operations and workflow transitions for their respective entities.

## 1. PetController

**Base Path**: `/ui/pet`
**Description**: Manages pet operations including CRUD and workflow transitions

### Endpoints

#### Create Pet
- **Method**: POST
- **Path**: `/ui/pet`
- **Description**: Creates a new pet (triggers initialize_pet transition)

**Request Example**:
```json
{
  "id": 123,
  "name": "Buddy",
  "category": {
    "id": 1,
    "name": "Dogs"
  },
  "photoUrls": [
    "https://example.com/photos/buddy1.jpg",
    "https://example.com/photos/buddy2.jpg"
  ],
  "tags": [
    {
      "id": 1,
      "name": "friendly"
    },
    {
      "id": 2,
      "name": "large"
    }
  ]
}
```

**Response Example**:
```json
{
  "entity": {
    "id": 123,
    "name": "Buddy",
    "category": {
      "id": 1,
      "name": "Dogs"
    },
    "photoUrls": ["https://example.com/photos/buddy1.jpg"],
    "tags": [{"id": 1, "name": "friendly"}]
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "available"
  }
}
```

#### Get Pet by ID
- **Method**: GET
- **Path**: `/ui/pet/{id}`
- **Description**: Retrieves a pet by its business ID

**Response Example**: Same as create response

#### Get Pet by Business ID
- **Method**: GET
- **Path**: `/ui/pet/business/{businessId}`
- **Description**: Retrieves a pet by its business identifier

#### Update Pet
- **Method**: PUT
- **Path**: `/ui/pet/{id}`
- **Query Parameters**: `transition` (optional)
- **Description**: Updates pet data and optionally triggers workflow transition

**Request Example** (with transition):
```json
{
  "id": 123,
  "name": "Buddy Updated",
  "category": {"id": 1, "name": "Dogs"},
  "photoUrls": ["https://example.com/photos/buddy1.jpg"],
  "tags": [{"id": 1, "name": "friendly"}]
}
```
**URL**: `/ui/pet/123?transition=reserve_pet`

#### Find Pets by Status
- **Method**: GET
- **Path**: `/ui/pet/findByStatus`
- **Query Parameters**: `status` (available, pending, sold)

#### Find Pets by Tags
- **Method**: GET
- **Path**: `/ui/pet/findByTags`
- **Query Parameters**: `tags` (comma-separated tag names)

#### Delete Pet
- **Method**: DELETE
- **Path**: `/ui/pet/{id}`

## 2. OrderController

**Base Path**: `/ui/order`
**Description**: Manages order operations and workflow transitions

### Endpoints

#### Create Order
- **Method**: POST
- **Path**: `/ui/order`
- **Description**: Creates a new order (triggers place_order transition)

**Request Example**:
```json
{
  "id": 456,
  "petId": 123,
  "userId": 789,
  "quantity": 1,
  "shipDate": "2024-01-15T10:00:00Z",
  "complete": false
}
```

**Response Example**:
```json
{
  "entity": {
    "id": 456,
    "petId": 123,
    "userId": 789,
    "quantity": 1,
    "shipDate": "2024-01-15T10:00:00Z",
    "complete": false
  },
  "metadata": {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "state": "placed"
  }
}
```

#### Get Order by ID
- **Method**: GET
- **Path**: `/ui/order/{id}`

#### Update Order
- **Method**: PUT
- **Path**: `/ui/order/{id}`
- **Query Parameters**: `transition` (approve_order, deliver_order, cancel_order)

**Request Example** (approve order):
```json
{
  "id": 456,
  "petId": 123,
  "userId": 789,
  "quantity": 1,
  "shipDate": "2024-01-15T10:00:00Z",
  "complete": false
}
```
**URL**: `/ui/order/456?transition=approve_order`

#### Get Store Inventory
- **Method**: GET
- **Path**: `/ui/order/inventory`
- **Description**: Returns inventory status by pet status

#### Delete Order
- **Method**: DELETE
- **Path**: `/ui/order/{id}`

## 3. UserController

**Base Path**: `/ui/user`
**Description**: Manages user operations and account workflow

### Endpoints

#### Create User
- **Method**: POST
- **Path**: `/ui/user`
- **Description**: Creates a new user (triggers activate_user transition)

**Request Example**:
```json
{
  "id": 789,
  "username": "johndoe",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "password": "securePassword123",
  "phone": "+1234567890",
  "userStatus": 1
}
```

**Response Example**:
```json
{
  "entity": {
    "id": 789,
    "username": "johndoe",
    "firstName": "John",
    "lastName": "Doe",
    "email": "john@example.com",
    "phone": "+1234567890",
    "userStatus": 1
  },
  "metadata": {
    "id": "770e8400-e29b-41d4-a716-446655440002",
    "state": "active"
  }
}
```

#### Get User by Username
- **Method**: GET
- **Path**: `/ui/user/{username}`

#### Update User
- **Method**: PUT
- **Path**: `/ui/user/{username}`
- **Query Parameters**: `transition` (deactivate_user, reactivate_user, suspend_user, unsuspend_user)

#### User Login
- **Method**: GET
- **Path**: `/ui/user/login`
- **Query Parameters**: `username`, `password`

#### User Logout
- **Method**: GET
- **Path**: `/ui/user/logout`

#### Delete User
- **Method**: DELETE
- **Path**: `/ui/user/{username}`

## 4. CategoryController

**Base Path**: `/ui/category`
**Description**: Manages pet categories

### Endpoints

#### Create Category
- **Method**: POST
- **Path**: `/ui/category`

**Request Example**:
```json
{
  "id": 1,
  "name": "Dogs"
}
```

#### Get Category by ID
- **Method**: GET
- **Path**: `/ui/category/{id}`

#### Get All Categories
- **Method**: GET
- **Path**: `/ui/category`

#### Update Category
- **Method**: PUT
- **Path**: `/ui/category/{id}`
- **Query Parameters**: `transition` (deactivate_category, reactivate_category)

#### Delete Category
- **Method**: DELETE
- **Path**: `/ui/category/{id}`

## 5. TagController

**Base Path**: `/ui/tag`
**Description**: Manages pet tags

### Endpoints

#### Create Tag
- **Method**: POST
- **Path**: `/ui/tag`

**Request Example**:
```json
{
  "id": 1,
  "name": "friendly"
}
```

#### Get Tag by ID
- **Method**: GET
- **Path**: `/ui/tag/{id}`

#### Get All Tags
- **Method**: GET
- **Path**: `/ui/tag`

#### Update Tag
- **Method**: PUT
- **Path**: `/ui/tag/{id}`
- **Query Parameters**: `transition` (deactivate_tag, reactivate_tag)

#### Delete Tag
- **Method**: DELETE
- **Path**: `/ui/tag/{id}`

## 6. StoreController

**Base Path**: `/ui/store`
**Description**: Manages store operations

### Endpoints

#### Create Store
- **Method**: POST
- **Path**: `/ui/store`

**Request Example**:
```json
{
  "id": 1,
  "name": "Purrfect Pets Downtown",
  "address": "123 Main St, City, State 12345",
  "phone": "+1234567890",
  "email": "store@purrfectpets.com",
  "operatingHours": "Mon-Fri 9AM-6PM, Sat-Sun 10AM-4PM"
}
```

#### Get Store by ID
- **Method**: GET
- **Path**: `/ui/store/{id}`

#### Update Store
- **Method**: PUT
- **Path**: `/ui/store/{id}`
- **Query Parameters**: `transition` (close_store, reopen_store, start_maintenance, end_maintenance)

#### Delete Store
- **Method**: DELETE
- **Path**: `/ui/store/{id}`

## Common Response Patterns

### Success Response
All successful operations return EntityWithMetadata structure:
```json
{
  "entity": { /* entity data */ },
  "metadata": {
    "id": "uuid",
    "state": "current_state",
    "createdAt": "2024-01-01T10:00:00Z",
    "updatedAt": "2024-01-01T10:00:00Z"
  }
}
```

### Error Responses

#### 400 Bad Request
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid input data",
  "details": ["Field 'name' is required"]
}
```

#### 404 Not Found
```json
{
  "error": "ENTITY_NOT_FOUND",
  "message": "Pet with ID 123 not found"
}
```

#### 422 Unprocessable Entity
```json
{
  "error": "WORKFLOW_TRANSITION_ERROR",
  "message": "Cannot transition from 'sold' to 'available'"
}
```

## Workflow Transition Guidelines

1. **Transition Parameter**: Use `?transition=transition_name` query parameter
2. **Valid Transitions**: Only transitions defined in workflow are allowed
3. **State Validation**: Current entity state must allow the requested transition
4. **Null Transitions**: Pass `null` or omit parameter for updates without state change
5. **Error Handling**: Return 422 for invalid transitions

## Controller Implementation Notes

1. **EntityService Usage**: Use EntityService for all entity operations
2. **ModelSpec**: Create ModelSpec with entity name and version for operations
3. **Business ID vs Technical ID**: Use business IDs in URLs, technical IDs for EntityService
4. **Cross-Origin**: Enable CORS with `@CrossOrigin(origins = "*")`
5. **Validation**: Validate request data before processing
6. **Error Handling**: Return appropriate HTTP status codes and error messages
7. **Response Format**: Always return EntityWithMetadata for consistency
