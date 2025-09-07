# Purrfect Pets - Controller Requirements

## Overview
This document defines the REST API controllers for the Purrfect Pets application. Each controller provides CRUD operations and workflow transition endpoints for their respective entities.

## 1. PetController

**Base Path**: `/api/v3/pet`

### Endpoints

#### Create Pet
- **POST** `/api/v3/pet`
- **Description**: Add a new pet to the store
- **Transition**: `create_pet` (none → available)

**Request Body**:
```json
{
  "name": "Fluffy",
  "category": {
    "id": 1,
    "name": "Dogs"
  },
  "photoUrls": [
    "https://example.com/photos/fluffy1.jpg",
    "https://example.com/photos/fluffy2.jpg"
  ],
  "tags": [
    {
      "id": 1,
      "name": "friendly"
    },
    {
      "id": 2,
      "name": "small"
    }
  ]
}
```

**Response**:
```json
{
  "entity": {
    "id": 123,
    "name": "Fluffy",
    "category": {
      "id": 1,
      "name": "Dogs"
    },
    "photoUrls": [
      "https://example.com/photos/fluffy1.jpg",
      "https://example.com/photos/fluffy2.jpg"
    ],
    "tags": [
      {
        "id": 1,
        "name": "friendly"
      }
    ]
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "available"
  }
}
```

#### Get Pet by ID
- **GET** `/api/v3/pet/{petId}`
- **Description**: Find pet by ID

**Response**: Same as create response

#### Update Pet
- **PUT** `/api/v3/pet`
- **Description**: Update an existing pet
- **Transition**: null (no state change)

#### Update Pet with Transition
- **PUT** `/api/v3/pet/{petId}/transition/{transitionName}`
- **Description**: Update pet and trigger state transition
- **Transitions**: `reserve_pet`, `complete_sale`, `cancel_reservation`, `mark_available`

**Request Body** (for reserve_pet):
```json
{
  "id": 123,
  "name": "Fluffy"
}
```

#### Find Pets by Status
- **GET** `/api/v3/pet/findByStatus?status=available,pending,sold`
- **Description**: Find pets by status (maps to workflow state)

#### Find Pets by Tags
- **GET** `/api/v3/pet/findByTags?tags=friendly,small`
- **Description**: Find pets by tags

#### Delete Pet
- **DELETE** `/api/v3/pet/{petId}`
- **Description**: Delete a pet

## 2. OrderController

**Base Path**: `/api/v3/store/order`

### Endpoints

#### Place Order
- **POST** `/api/v3/store/order`
- **Description**: Place an order for a pet
- **Transition**: `place_order` (none → placed)

**Request Body**:
```json
{
  "petId": 123,
  "quantity": 1,
  "shipDate": "2024-01-15T10:00:00Z"
}
```

**Response**:
```json
{
  "entity": {
    "id": 456,
    "petId": 123,
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
- **GET** `/api/v3/store/order/{orderId}`
- **Description**: Find purchase order by ID

#### Update Order with Transition
- **PUT** `/api/v3/store/order/{orderId}/transition/{transitionName}`
- **Description**: Update order and trigger state transition
- **Transitions**: `approve_order`, `deliver_order`, `cancel_order`, `cancel_approved_order`

#### Delete Order
- **DELETE** `/api/v3/store/order/{orderId}`
- **Description**: Delete purchase order by ID

#### Get Store Inventory
- **GET** `/api/v3/store/inventory`
- **Description**: Returns pet inventories by status

**Response**:
```json
{
  "available": 15,
  "pending": 3,
  "sold": 42
}
```

## 3. UserController

**Base Path**: `/api/v3/user`

### Endpoints

#### Create User
- **POST** `/api/v3/user`
- **Description**: Create user
- **Transition**: `activate_user` (none → active)

**Request Body**:
```json
{
  "username": "johndoe",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "password": "securePassword123",
  "phone": "+1234567890"
}
```

**Response**:
```json
{
  "entity": {
    "id": 789,
    "username": "johndoe",
    "firstName": "John",
    "lastName": "Doe",
    "email": "john@example.com",
    "phone": "+1234567890"
  },
  "metadata": {
    "id": "770e8400-e29b-41d4-a716-446655440002",
    "state": "active"
  }
}
```

#### Get User by Username
- **GET** `/api/v3/user/{username}`
- **Description**: Get user by username

#### Update User
- **PUT** `/api/v3/user/{username}`
- **Description**: Update user
- **Transition**: null (no state change)

#### Update User with Transition
- **PUT** `/api/v3/user/{username}/transition/{transitionName}`
- **Description**: Update user and trigger state transition
- **Transitions**: `deactivate_user`, `suspend_user`, `reactivate_user`, `unsuspend_user`

#### Delete User
- **DELETE** `/api/v3/user/{username}`
- **Description**: Delete user

#### User Login
- **GET** `/api/v3/user/login?username=johndoe&password=securePassword123`
- **Description**: Logs user into the system

#### User Logout
- **GET** `/api/v3/user/logout`
- **Description**: Logs out current logged in user session

#### Create Users with List
- **POST** `/api/v3/user/createWithList`
- **Description**: Creates list of users with given input array

## 4. CategoryController

**Base Path**: `/api/v3/category`

### Endpoints

#### Create Category
- **POST** `/api/v3/category`
- **Description**: Create a new category
- **Transition**: `create_category` (none → active)

**Request Body**:
```json
{
  "name": "Birds"
}
```

**Response**:
```json
{
  "entity": {
    "id": 3,
    "name": "Birds"
  },
  "metadata": {
    "id": "880e8400-e29b-41d4-a716-446655440003",
    "state": "active"
  }
}
```

#### Get All Categories
- **GET** `/api/v3/category`
- **Description**: Get all categories

#### Get Category by ID
- **GET** `/api/v3/category/{categoryId}`
- **Description**: Get category by ID

#### Update Category
- **PUT** `/api/v3/category/{categoryId}`
- **Description**: Update category
- **Transition**: null (no state change)

#### Update Category with Transition
- **PUT** `/api/v3/category/{categoryId}/transition/{transitionName}`
- **Description**: Update category and trigger state transition
- **Transitions**: `deactivate_category`, `reactivate_category`

#### Delete Category
- **DELETE** `/api/v3/category/{categoryId}`
- **Description**: Delete category

## 5. TagController

**Base Path**: `/api/v3/tag`

### Endpoints

#### Create Tag
- **POST** `/api/v3/tag`
- **Description**: Create a new tag
- **Transition**: `create_tag` (none → active)

**Request Body**:
```json
{
  "name": "playful"
}
```

**Response**:
```json
{
  "entity": {
    "id": 5,
    "name": "playful"
  },
  "metadata": {
    "id": "990e8400-e29b-41d4-a716-446655440004",
    "state": "active"
  }
}
```

#### Get All Tags
- **GET** `/api/v3/tag`
- **Description**: Get all tags

#### Get Tag by ID
- **GET** `/api/v3/tag/{tagId}`
- **Description**: Get tag by ID

#### Update Tag
- **PUT** `/api/v3/tag/{tagId}`
- **Description**: Update tag
- **Transition**: null (no state change)

#### Update Tag with Transition
- **PUT** `/api/v3/tag/{tagId}/transition/{transitionName}`
- **Description**: Update tag and trigger state transition
- **Transitions**: `deactivate_tag`, `reactivate_tag`

#### Delete Tag
- **DELETE** `/api/v3/tag/{tagId}`
- **Description**: Delete tag

## 6. StoreController

**Base Path**: `/api/v3/store`

### Endpoints

#### Create Store
- **POST** `/api/v3/store`
- **Description**: Create a new store
- **Transition**: `create_store` (none → active)

**Request Body**:
```json
{
  "name": "Purrfect Pets Downtown",
  "address": "123 Main St, City, State 12345",
  "phone": "+1234567890",
  "email": "downtown@purrfectpets.com"
}
```

**Response**:
```json
{
  "entity": {
    "id": 1,
    "name": "Purrfect Pets Downtown",
    "address": "123 Main St, City, State 12345",
    "phone": "+1234567890",
    "email": "downtown@purrfectpets.com"
  },
  "metadata": {
    "id": "aa0e8400-e29b-41d4-a716-446655440005",
    "state": "active"
  }
}
```

#### Get All Stores
- **GET** `/api/v3/store`
- **Description**: Get all stores

#### Get Store by ID
- **GET** `/api/v3/store/{storeId}`
- **Description**: Get store by ID

#### Update Store
- **PUT** `/api/v3/store/{storeId}`
- **Description**: Update store
- **Transition**: null (no state change)

#### Update Store with Transition
- **PUT** `/api/v3/store/{storeId}/transition/{transitionName}`
- **Description**: Update store and trigger state transition
- **Transitions**: `deactivate_store`, `reactivate_store`

#### Delete Store
- **DELETE** `/api/v3/store/{storeId}`
- **Description**: Delete store

## Common Response Patterns

### Success Response Format
All successful responses follow the EntityWithMetadata pattern:
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

### Error Response Format
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Pet name cannot be empty",
    "details": {
      "field": "name",
      "value": ""
    }
  }
}
```

### HTTP Status Codes
- **200**: Success
- **201**: Created
- **400**: Bad Request (validation errors)
- **404**: Not Found
- **422**: Unprocessable Entity (business rule violations)
- **500**: Internal Server Error

## Controller Implementation Notes

### Common Patterns
1. All controllers use EntityService for data operations
2. Controllers handle HTTP-specific concerns (request/response mapping)
3. Business logic is delegated to processors and criteria
4. State transitions are explicit through transition endpoints
5. All responses use EntityWithMetadata wrapper

### Validation
1. Request validation is handled by Spring Boot validation annotations
2. Business validation is handled by criteria
3. State transition validation is handled by workflow engine

### Security
1. Authentication and authorization should be implemented
2. API keys or JWT tokens for secure endpoints
3. Rate limiting for public endpoints
4. Input sanitization for all user inputs
