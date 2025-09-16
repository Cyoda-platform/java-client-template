# Purrfect Pets API - Controller Requirements

## Overview
This document defines the detailed requirements for all controllers in the Purrfect Pets API application. Controllers provide REST API endpoints that match the Petstore API structure.

## 1. PetController

**Base Path**: `/pet`

### Endpoints

#### 1.1 Add Pet
- **Method**: POST
- **Path**: `/pet`
- **Description**: Add a new pet to the store
- **Transition**: `create_pet` (none → draft)

**Request Example**:
```json
{
  "name": "Fluffy",
  "category": {
    "id": 1,
    "name": "Cats"
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
      "name": "indoor"
    }
  ]
}
```

**Response Example**:
```json
{
  "id": 123,
  "name": "Fluffy",
  "category": {
    "id": 1,
    "name": "Cats"
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
  ],
  "status": "draft"
}
```

#### 1.2 Update Pet
- **Method**: PUT
- **Path**: `/pet`
- **Description**: Update an existing pet
- **Transition**: `null` (no state change) or specific transition based on request

**Request Example**:
```json
{
  "id": 123,
  "name": "Fluffy Updated",
  "category": {
    "id": 1,
    "name": "Cats"
  },
  "photoUrls": [
    "https://example.com/photos/fluffy1.jpg"
  ],
  "tags": [
    {
      "id": 1,
      "name": "friendly"
    }
  ],
  "transitionName": "make_available"
}
```

#### 1.3 Find Pets by Status
- **Method**: GET
- **Path**: `/pet/findByStatus`
- **Description**: Find pets by status (maps to workflow states)
- **Query Parameters**: `status` (available, pending, sold)

**Request Example**:
```
GET /pet/findByStatus?status=available&status=pending
```

**Response Example**:
```json
[
  {
    "id": 123,
    "name": "Fluffy",
    "status": "available"
  }
]
```

#### 1.4 Find Pets by Tags
- **Method**: GET
- **Path**: `/pet/findByTags`
- **Description**: Find pets by tags
- **Query Parameters**: `tags` (comma-separated tag names)

#### 1.5 Get Pet by ID
- **Method**: GET
- **Path**: `/pet/{petId}`
- **Description**: Find pet by ID

#### 1.6 Update Pet with Form Data
- **Method**: POST
- **Path**: `/pet/{petId}`
- **Description**: Update pet with form data
- **Form Parameters**: `name`, `status`, `transitionName`

#### 1.7 Delete Pet
- **Method**: DELETE
- **Path**: `/pet/{petId}`
- **Description**: Delete a pet

#### 1.8 Upload Pet Image
- **Method**: POST
- **Path**: `/pet/{petId}/uploadImage`
- **Description**: Upload an image for a pet
- **Content-Type**: multipart/form-data

## 2. CategoryController

**Base Path**: `/category`

### Endpoints

#### 2.1 Create Category
- **Method**: POST
- **Path**: `/category`
- **Description**: Create a new category
- **Transition**: `create_category` (none → active)

**Request Example**:
```json
{
  "name": "Dogs"
}
```

**Response Example**:
```json
{
  "id": 1,
  "name": "Dogs"
}
```

#### 2.2 Get All Categories
- **Method**: GET
- **Path**: `/category`
- **Description**: Get all categories

#### 2.3 Get Category by ID
- **Method**: GET
- **Path**: `/category/{categoryId}`
- **Description**: Get category by ID

#### 2.4 Update Category
- **Method**: PUT
- **Path**: `/category/{categoryId}`
- **Description**: Update category
- **Transition**: `null` or `deactivate_category`/`reactivate_category`

**Request Example**:
```json
{
  "id": 1,
  "name": "Dogs Updated",
  "transitionName": "deactivate_category"
}
```

#### 2.5 Delete Category
- **Method**: DELETE
- **Path**: `/category/{categoryId}`
- **Description**: Delete category

## 3. TagController

**Base Path**: `/tag`

### Endpoints

#### 3.1 Create Tag
- **Method**: POST
- **Path**: `/tag`
- **Description**: Create a new tag
- **Transition**: `create_tag` (none → active)

**Request Example**:
```json
{
  "name": "friendly"
}
```

#### 3.2 Get All Tags
- **Method**: GET
- **Path**: `/tag`
- **Description**: Get all tags

#### 3.3 Get Tag by ID
- **Method**: GET
- **Path**: `/tag/{tagId}`
- **Description**: Get tag by ID

#### 3.4 Update Tag
- **Method**: PUT
- **Path**: `/tag/{tagId}`
- **Description**: Update tag
- **Transition**: `null` or `deactivate_tag`/`reactivate_tag`

#### 3.5 Delete Tag
- **Method**: DELETE
- **Path**: `/tag/{tagId}`
- **Description**: Delete tag

## 4. OrderController

**Base Path**: `/store`

### Endpoints

#### 4.1 Place Order
- **Method**: POST
- **Path**: `/store/order`
- **Description**: Place an order for a pet
- **Transition**: `place_order` (none → placed)

**Request Example**:
```json
{
  "petId": 123,
  "quantity": 1,
  "shipDate": "2024-01-15T10:00:00Z",
  "userId": 456
}
```

**Response Example**:
```json
{
  "id": 789,
  "petId": 123,
  "quantity": 1,
  "shipDate": "2024-01-15T10:00:00Z",
  "status": "placed",
  "complete": false
}
```

#### 4.2 Get Order by ID
- **Method**: GET
- **Path**: `/store/order/{orderId}`
- **Description**: Find purchase order by ID

#### 4.3 Update Order
- **Method**: PUT
- **Path**: `/store/order/{orderId}`
- **Description**: Update order status
- **Transition**: `approve_order`, `ship_order`, `deliver_order`, `cancel_order`

**Request Example**:
```json
{
  "id": 789,
  "petId": 123,
  "quantity": 1,
  "shipDate": "2024-01-15T10:00:00Z",
  "status": "approved",
  "complete": false,
  "transitionName": "approve_order"
}
```

#### 4.4 Delete Order
- **Method**: DELETE
- **Path**: `/store/order/{orderId}`
- **Description**: Delete purchase order by ID
- **Transition**: `cancel_order` or `cancel_approved_order`

#### 4.5 Get Inventory
- **Method**: GET
- **Path**: `/store/inventory`
- **Description**: Returns pet inventories by status

**Response Example**:
```json
{
  "available": 5,
  "pending": 2,
  "sold": 10
}
```

## 5. UserController

**Base Path**: `/user`

### Endpoints

#### 5.1 Create User
- **Method**: POST
- **Path**: `/user`
- **Description**: Create user
- **Transition**: `register_user` (none → registered)

**Request Example**:
```json
{
  "username": "john_doe",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "password": "SecurePass123",
  "phone": "+1234567890",
  "userStatus": 0
}
```

**Response Example**:
```json
{
  "id": 456,
  "username": "john_doe",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "phone": "+1234567890",
  "userStatus": 0
}
```

#### 5.2 Create Users with Array
- **Method**: POST
- **Path**: `/user/createWithArray`
- **Description**: Create list of users with given input array

#### 5.3 Create Users with List
- **Method**: POST
- **Path**: `/user/createWithList`
- **Description**: Create list of users with given input list

#### 5.4 Login User
- **Method**: GET
- **Path**: `/user/login`
- **Description**: Logs user into the system
- **Query Parameters**: `username`, `password`

#### 5.5 Logout User
- **Method**: GET
- **Path**: `/user/logout`
- **Description**: Logs out current logged in user session

#### 5.6 Get User by Username
- **Method**: GET
- **Path**: `/user/{username}`
- **Description**: Get user by user name

#### 5.7 Update User
- **Method**: PUT
- **Path**: `/user/{username}`
- **Description**: Update user
- **Transition**: `activate_user`, `suspend_user`, `reactivate_user`

**Request Example**:
```json
{
  "username": "john_doe",
  "firstName": "John Updated",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "password": "NewSecurePass123",
  "phone": "+1234567890",
  "userStatus": 1,
  "transitionName": "activate_user"
}
```

#### 5.8 Delete User
- **Method**: DELETE
- **Path**: `/user/{username}`
- **Description**: Delete user
- **Transition**: `delete_user` or `delete_suspended_user`

## Controller Implementation Notes

1. **Error Handling**: All endpoints should return appropriate HTTP status codes and error messages.
2. **Validation**: Input validation should be performed before calling processors.
3. **Authentication**: Implement appropriate authentication and authorization.
4. **Logging**: Log all API requests and responses for audit purposes.
5. **Rate Limiting**: Implement rate limiting to prevent abuse.
6. **CORS**: Configure CORS settings for web client access.
7. **Swagger Documentation**: All endpoints should be documented with Swagger annotations.
8. **Transition Names**: When updating entities, the `transitionName` parameter determines which workflow transition to trigger.
9. **State Mapping**: The `status` field in responses should map to the current workflow state of the entity.
