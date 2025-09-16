# Purrfect Pets API - Controller Requirements

## Overview
This document defines the detailed requirements for REST controllers in the Purrfect Pets API application. Each entity has its own controller with CRUD operations and workflow transitions.

## 1. PetController

### Base Path: `/api/v1/pets`

#### GET /api/v1/pets
**Description**: Get all pets
**Parameters**: 
- `status` (optional): Filter by pet status (available, pending, sold)
- `categoryId` (optional): Filter by category ID
- `tags` (optional): Filter by tag names (comma-separated)

**Response Example**:
```json
[
  {
    "id": 1,
    "name": "Buddy",
    "photoUrls": ["https://example.com/buddy1.jpg"],
    "categoryId": 1,
    "tagIds": [1, 2],
    "createdDate": "2024-01-15T10:30:00",
    "lastModified": "2024-01-15T10:30:00",
    "state": "available"
  }
]
```

#### GET /api/v1/pets/{petId}
**Description**: Get pet by ID
**Parameters**: `petId` (path parameter)

**Response Example**:
```json
{
  "id": 1,
  "name": "Buddy",
  "photoUrls": ["https://example.com/buddy1.jpg", "https://example.com/buddy2.jpg"],
  "categoryId": 1,
  "tagIds": [1, 2],
  "createdDate": "2024-01-15T10:30:00",
  "lastModified": "2024-01-15T10:30:00",
  "state": "available"
}
```

#### POST /api/v1/pets
**Description**: Create a new pet
**Transition**: `create_pet` (automatic)

**Request Example**:
```json
{
  "name": "Max",
  "photoUrls": ["https://example.com/max1.jpg", "https://example.com/max2.jpg"],
  "categoryId": 2,
  "tagIds": [1, 3]
}
```

**Response Example**:
```json
{
  "id": 2,
  "name": "Max",
  "photoUrls": ["https://example.com/max1.jpg", "https://example.com/max2.jpg"],
  "categoryId": 2,
  "tagIds": [1, 3],
  "createdDate": "2024-01-16T14:20:00",
  "lastModified": "2024-01-16T14:20:00",
  "state": "available"
}
```

#### PUT /api/v1/pets/{petId}
**Description**: Update pet with optional state transition
**Parameters**: 
- `petId` (path parameter)
- `transitionName` (query parameter, optional): Workflow transition name

**Request Example** (with transition):
```json
{
  "name": "Max Updated",
  "photoUrls": ["https://example.com/max1.jpg"],
  "categoryId": 2,
  "tagIds": [1],
  "transitionName": "reserve_pet"
}
```

#### DELETE /api/v1/pets/{petId}
**Description**: Delete pet (soft delete by transitioning to inactive state)

## 2. OrderController

### Base Path: `/api/v1/orders`

#### GET /api/v1/orders
**Description**: Get all orders
**Parameters**: 
- `status` (optional): Filter by order status (placed, approved, delivered)
- `customerId` (optional): Filter by customer ID

#### GET /api/v1/orders/{orderId}
**Description**: Get order by ID

#### POST /api/v1/orders
**Description**: Create a new order
**Transition**: `place_order` (automatic)

**Request Example**:
```json
{
  "petId": 1,
  "customerId": 1,
  "quantity": 1,
  "shipDate": "2024-01-25T10:00:00"
}
```

**Response Example**:
```json
{
  "id": 1,
  "petId": 1,
  "customerId": 1,
  "quantity": 1,
  "shipDate": "2024-01-25T10:00:00",
  "orderDate": "2024-01-16T15:30:00",
  "complete": false,
  "state": "placed"
}
```

#### PUT /api/v1/orders/{orderId}
**Description**: Update order with optional state transition
**Parameters**: 
- `orderId` (path parameter)
- `transitionName` (query parameter, optional): approve_order, deliver_order, cancel_order, etc.

**Request Example** (approve order):
```json
{
  "transitionName": "approve_order"
}
```

#### DELETE /api/v1/orders/{orderId}
**Description**: Cancel order (transition to cancelled state)

## 3. UserController

### Base Path: `/api/v1/users`

#### GET /api/v1/users
**Description**: Get all users (admin only)
**Parameters**: 
- `status` (optional): Filter by user status

#### GET /api/v1/users/{userId}
**Description**: Get user by ID

#### GET /api/v1/users/username/{username}
**Description**: Get user by username

#### POST /api/v1/users
**Description**: Register a new user
**Transition**: `register_user` (automatic)

**Request Example**:
```json
{
  "username": "johndoe",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "password": "SecurePassword123!",
  "phone": "+1234567890"
}
```

**Response Example**:
```json
{
  "id": 1,
  "username": "johndoe",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "phone": "+1234567890",
  "userStatus": 0,
  "registrationDate": "2024-01-16T16:00:00",
  "state": "registered"
}
```

#### PUT /api/v1/users/{userId}
**Description**: Update user with optional state transition
**Parameters**: 
- `userId` (path parameter)
- `transitionName` (query parameter, optional): activate_user, suspend_user, etc.

#### DELETE /api/v1/users/{userId}
**Description**: Deactivate user account

#### POST /api/v1/users/login
**Description**: User login
**Request Example**:
```json
{
  "username": "johndoe",
  "password": "SecurePassword123!"
}
```

#### POST /api/v1/users/logout
**Description**: User logout

## 4. CategoryController

### Base Path: `/api/v1/categories`

#### GET /api/v1/categories
**Description**: Get all categories
**Parameters**: 
- `active` (optional): Filter by active status (true/false)

#### GET /api/v1/categories/{categoryId}
**Description**: Get category by ID

#### POST /api/v1/categories
**Description**: Create a new category
**Transition**: `create_category` (automatic)

**Request Example**:
```json
{
  "name": "Dogs",
  "description": "All dog breeds and types"
}
```

**Response Example**:
```json
{
  "id": 1,
  "name": "Dogs",
  "description": "All dog breeds and types",
  "createdDate": "2024-01-16T17:00:00",
  "active": true,
  "state": "active"
}
```

#### PUT /api/v1/categories/{categoryId}
**Description**: Update category with optional state transition
**Parameters**: 
- `categoryId` (path parameter)
- `transitionName` (query parameter, optional): deactivate_category, reactivate_category

#### DELETE /api/v1/categories/{categoryId}
**Description**: Deactivate category

## 5. TagController

### Base Path: `/api/v1/tags`

#### GET /api/v1/tags
**Description**: Get all tags
**Parameters**: 
- `active` (optional): Filter by active status (true/false)

#### GET /api/v1/tags/{tagId}
**Description**: Get tag by ID

#### POST /api/v1/tags
**Description**: Create a new tag
**Transition**: `create_tag` (automatic)

**Request Example**:
```json
{
  "name": "Friendly",
  "color": "#4CAF50"
}
```

**Response Example**:
```json
{
  "id": 1,
  "name": "Friendly",
  "color": "#4CAF50",
  "createdDate": "2024-01-16T17:30:00",
  "active": true,
  "state": "active"
}
```

#### PUT /api/v1/tags/{tagId}
**Description**: Update tag with optional state transition
**Parameters**: 
- `tagId` (path parameter)
- `transitionName` (query parameter, optional): deactivate_tag, reactivate_tag

#### DELETE /api/v1/tags/{tagId}
**Description**: Deactivate tag

## Controller Implementation Guidelines

### General Principles
1. **RESTful Design**: Follow REST conventions for HTTP methods and status codes
2. **Consistent Response Format**: All responses follow the same JSON structure
3. **Error Handling**: Proper HTTP status codes and error messages
4. **Validation**: Input validation before processing
5. **Security**: Authentication and authorization where required
6. **Workflow Integration**: Proper transition name handling

### HTTP Status Codes
- `200 OK`: Successful GET, PUT operations
- `201 Created`: Successful POST operations
- `204 No Content`: Successful DELETE operations
- `400 Bad Request`: Invalid input data
- `401 Unauthorized`: Authentication required
- `403 Forbidden`: Access denied
- `404 Not Found`: Resource not found
- `409 Conflict`: Resource conflict (e.g., duplicate username)
- `422 Unprocessable Entity`: Validation errors
- `500 Internal Server Error`: Server errors

### Transition Name Handling
- When `transitionName` is provided, validate it exists in the workflow
- Ensure current entity state allows the specified transition
- If transition fails, return appropriate error with details
- If `transitionName` is null, perform update without state change

### Security Considerations
- Implement proper authentication for all endpoints
- Use authorization to restrict access to sensitive operations
- Validate user permissions for entity modifications
- Sanitize input data to prevent injection attacks
- Log security-relevant operations
