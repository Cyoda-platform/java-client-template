# Controllers Requirements

## Overview
REST API controllers for the Purrfect Pets API, following Petstore API conventions. Each entity has its own controller with CRUD operations and workflow transitions.

## 1. PetController

### Base Path: `/pet`

#### Create Pet
**Endpoint**: `POST /pet`  
**Description**: Add a new pet to the store  
**Transition**: `create_pet` (none → pending)

**Request Example**:
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

**Response Example**:
```json
{
  "id": 123,
  "name": "Fluffy",
  "category": {
    "id": 1,
    "name": "Dogs"
  },
  "photoUrls": ["https://example.com/photos/fluffy1.jpg"],
  "tags": [{"id": 1, "name": "friendly"}],
  "status": "pending"
}
```

#### Update Pet
**Endpoint**: `PUT /pet`  
**Description**: Update an existing pet  
**Transition**: Optional transition parameter

**Request Example**:
```json
{
  "id": 123,
  "name": "Fluffy Updated",
  "category": {
    "id": 1,
    "name": "Dogs"
  },
  "photoUrls": ["https://example.com/photos/fluffy_new.jpg"],
  "tags": [{"id": 1, "name": "friendly"}],
  "transition": "make_available"
}
```

#### Get Pet by ID
**Endpoint**: `GET /pet/{petId}`  
**Description**: Find pet by ID

**Response Example**:
```json
{
  "id": 123,
  "name": "Fluffy",
  "status": "available"
}
```

#### Find Pets by Status
**Endpoint**: `GET /pet/findByStatus?status={status}`  
**Description**: Find pets by status (available, pending, sold)

#### Delete Pet
**Endpoint**: `DELETE /pet/{petId}`  
**Description**: Delete a pet

## 2. CategoryController

### Base Path: `/category`

#### Create Category
**Endpoint**: `POST /category`  
**Transition**: `create_category` (none → active)

**Request Example**:
```json
{
  "name": "Birds"
}
```

**Response Example**:
```json
{
  "id": 5,
  "name": "Birds",
  "status": "active"
}
```

#### Update Category
**Endpoint**: `PUT /category`  
**Transition**: Optional transition parameter

**Request Example**:
```json
{
  "id": 5,
  "name": "Exotic Birds",
  "transition": "deactivate_category"
}
```

#### Get All Categories
**Endpoint**: `GET /category`

#### Get Category by ID
**Endpoint**: `GET /category/{categoryId}`

#### Delete Category
**Endpoint**: `DELETE /category/{categoryId}`

## 3. TagController

### Base Path: `/tag`

#### Create Tag
**Endpoint**: `POST /tag`  
**Transition**: `create_tag` (none → active)

**Request Example**:
```json
{
  "name": "playful"
}
```

**Response Example**:
```json
{
  "id": 10,
  "name": "playful",
  "status": "active"
}
```

#### Update Tag
**Endpoint**: `PUT /tag`  
**Transition**: Optional transition parameter

**Request Example**:
```json
{
  "id": 10,
  "name": "very playful",
  "transition": "deactivate_tag"
}
```

#### Get All Tags
**Endpoint**: `GET /tag`

#### Get Tag by ID
**Endpoint**: `GET /tag/{tagId}`

#### Delete Tag
**Endpoint**: `DELETE /tag/{tagId}`

## 4. OrderController

### Base Path: `/store/order`

#### Place Order
**Endpoint**: `POST /store/order`  
**Description**: Place an order for a pet  
**Transition**: `place_order` (none → placed)

**Request Example**:
```json
{
  "petId": 123,
  "quantity": 1,
  "shipDate": "2024-12-25T10:00:00Z",
  "complete": false
}
```

**Response Example**:
```json
{
  "id": 456,
  "petId": 123,
  "quantity": 1,
  "shipDate": "2024-12-25T10:00:00Z",
  "status": "placed",
  "complete": false
}
```

#### Update Order
**Endpoint**: `PUT /store/order`  
**Transition**: Required transition parameter for status changes

**Request Example**:
```json
{
  "id": 456,
  "petId": 123,
  "quantity": 1,
  "shipDate": "2024-12-25T10:00:00Z",
  "transition": "approve_order"
}
```

#### Get Order by ID
**Endpoint**: `GET /store/order/{orderId}`

**Response Example**:
```json
{
  "id": 456,
  "petId": 123,
  "quantity": 1,
  "status": "approved",
  "complete": false
}
```

#### Delete Order
**Endpoint**: `DELETE /store/order/{orderId}`  
**Description**: Cancel an order  
**Transition**: `cancel_order` (placed → none)

#### Get Store Inventory
**Endpoint**: `GET /store/inventory`  
**Description**: Returns pet inventories by status

**Response Example**:
```json
{
  "available": 5,
  "pending": 2,
  "sold": 8
}
```

## Common Response Patterns

### Success Response
```json
{
  "id": 123,
  "status": "success",
  "data": { /* entity data */ }
}
```

### Error Response
```json
{
  "status": "error",
  "message": "Pet not found",
  "code": "PET_NOT_FOUND"
}
```

### Validation Error Response
```json
{
  "status": "error",
  "message": "Validation failed",
  "errors": [
    {
      "field": "name",
      "message": "Pet name is required"
    }
  ]
}
```

## Controller Implementation Guidelines

### Transition Handling
- Update endpoints should accept optional `transition` parameter
- If transition is provided, validate it exists in the workflow
- If transition is null, perform update without state change
- Validate current entity state supports the requested transition

### Error Handling
- Return appropriate HTTP status codes
- Provide meaningful error messages
- Handle workflow transition errors gracefully
- Log all errors for debugging

### Validation
- Validate all input parameters
- Check entity existence before operations
- Validate business rules before state transitions
- Return validation errors with field-specific messages

### Security Considerations
- Validate user permissions for operations
- Sanitize input data
- Prevent unauthorized state transitions
- Log security-related events
