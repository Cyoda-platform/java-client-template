# Purrfect Pets - Controller Requirements

## Overview
Each entity requires a dedicated REST controller to handle HTTP API operations. Controllers follow RESTful principles and integrate with the EntityService for data operations and workflow transitions.

## 1. PetController

**Package**: `com.java_template.application.controller`
**Class Name**: `PetController`
**Base Path**: `/api/pets`

### Endpoints:

#### POST /api/pets
**Purpose**: Create a new pet
**Transition**: Triggers `initialize_pet` (none → available)

**Request Body**:
```json
{
  "petId": "PET001",
  "name": "Buddy",
  "categoryId": "CAT001",
  "photoUrls": ["https://example.com/photo1.jpg"],
  "tags": ["TAG001", "TAG002"],
  "price": 299.99,
  "breed": "Golden Retriever",
  "age": 24,
  "description": "Friendly and energetic dog",
  "weight": 25.5,
  "vaccinated": true
}
```

**Response**:
```json
{
  "entity": {
    "petId": "PET001",
    "name": "Buddy",
    "categoryId": "CAT001",
    "photoUrls": ["https://example.com/photo1.jpg"],
    "tags": ["TAG001", "TAG002"],
    "price": 299.99,
    "breed": "Golden Retriever",
    "age": 24,
    "description": "Friendly and energetic dog",
    "weight": 25.5,
    "vaccinated": true,
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T10:30:00"
  },
  "metadata": {
    "id": "uuid-here",
    "state": "available",
    "version": 1
  }
}
```

#### GET /api/pets/{id}
**Purpose**: Get pet by technical UUID

**Response**: Same as POST response

#### GET /api/pets/business/{petId}
**Purpose**: Get pet by business ID

**Response**: Same as POST response

#### PUT /api/pets/{id}?transition={transitionName}
**Purpose**: Update pet with optional workflow transition
**Transitions**: 
- `reserve_pet` (available → pending)
- `complete_sale` (pending → sold)
- `cancel_reservation` (pending → available)
- `return_pet` (sold → available)

**Request Body**: Same as POST (partial updates allowed)

#### DELETE /api/pets/{id}
**Purpose**: Delete pet by technical UUID

#### GET /api/pets
**Purpose**: Get all pets (with pagination support)

#### GET /api/pets/search?status={status}&category={categoryId}&minPrice={price}&maxPrice={price}
**Purpose**: Search pets by criteria

#### POST /api/pets/search/advanced
**Purpose**: Advanced search with complex criteria

**Request Body**:
```json
{
  "name": "Buddy",
  "categoryId": "CAT001",
  "tags": ["TAG001"],
  "minPrice": 100.0,
  "maxPrice": 500.0,
  "breed": "Golden Retriever",
  "vaccinated": true,
  "minAge": 12,
  "maxAge": 36
}
```

## 2. OrderController

**Package**: `com.java_template.application.controller`
**Class Name**: `OrderController`
**Base Path**: `/api/orders`

### Endpoints:

#### POST /api/orders
**Purpose**: Create a new order
**Transition**: Triggers `place_order` (none → placed)

**Request Body**:
```json
{
  "orderId": "ORD001",
  "petId": "PET001",
  "quantity": 1,
  "customerInfo": {
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phone": "+1-555-0123"
  },
  "shippingAddress": {
    "street": "123 Main St",
    "city": "Anytown",
    "state": "CA",
    "zipCode": "12345",
    "country": "USA"
  },
  "notes": "Please handle with care"
}
```

**Response**:
```json
{
  "entity": {
    "orderId": "ORD001",
    "petId": "PET001",
    "quantity": 1,
    "orderDate": "2024-01-15T10:30:00",
    "customerInfo": {
      "firstName": "John",
      "lastName": "Doe",
      "email": "john.doe@example.com",
      "phone": "+1-555-0123"
    },
    "shippingAddress": {
      "street": "123 Main St",
      "city": "Anytown",
      "state": "CA",
      "zipCode": "12345",
      "country": "USA"
    },
    "totalAmount": 299.99,
    "notes": "Please handle with care",
    "complete": false,
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T10:30:00"
  },
  "metadata": {
    "id": "uuid-here",
    "state": "placed",
    "version": 1
  }
}
```

#### PUT /api/orders/{id}?transition={transitionName}
**Purpose**: Update order with workflow transition
**Transitions**:
- `approve_order` (placed → approved)
- `deliver_order` (approved → delivered)
- `cancel_order` (placed → none)
- `reject_order` (approved → placed)

#### GET /api/orders/{id}
**Purpose**: Get order by technical UUID

#### GET /api/orders/business/{orderId}
**Purpose**: Get order by business ID

#### GET /api/orders/customer/{email}
**Purpose**: Get orders by customer email

## 3. CategoryController

**Package**: `com.java_template.application.controller`
**Class Name**: `CategoryController`
**Base Path**: `/api/categories`

### Endpoints:

#### POST /api/categories
**Purpose**: Create a new category
**Transition**: Triggers `activate_category` (none → active)

**Request Body**:
```json
{
  "categoryId": "CAT001",
  "name": "Dogs",
  "description": "All dog breeds and puppies",
  "active": true
}
```

**Response**:
```json
{
  "entity": {
    "categoryId": "CAT001",
    "name": "Dogs",
    "description": "All dog breeds and puppies",
    "active": true,
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T10:30:00"
  },
  "metadata": {
    "id": "uuid-here",
    "state": "active",
    "version": 1
  }
}
```

#### GET /api/categories/{id}
**Purpose**: Get category by technical UUID

#### GET /api/categories/business/{categoryId}
**Purpose**: Get category by business ID

#### PUT /api/categories/{id}
**Purpose**: Update category (no transition needed)

#### GET /api/categories
**Purpose**: Get all active categories

## 4. TagController

**Package**: `com.java_template.application.controller`
**Class Name**: `TagController`
**Base Path**: `/api/tags`

### Endpoints:

#### POST /api/tags
**Purpose**: Create a new tag
**Transition**: Triggers `activate_tag` (none → active)

**Request Body**:
```json
{
  "tagId": "TAG001",
  "name": "Friendly",
  "color": "green",
  "description": "Pet is friendly with people and other animals",
  "active": true
}
```

**Response**:
```json
{
  "entity": {
    "tagId": "TAG001",
    "name": "Friendly",
    "color": "green",
    "description": "Pet is friendly with people and other animals",
    "active": true,
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T10:30:00"
  },
  "metadata": {
    "id": "uuid-here",
    "state": "active",
    "version": 1
  }
}
```

#### GET /api/tags/{id}
**Purpose**: Get tag by technical UUID

#### GET /api/tags/business/{tagId}
**Purpose**: Get tag by business ID

#### PUT /api/tags/{id}
**Purpose**: Update tag (no transition needed)

#### GET /api/tags
**Purpose**: Get all active tags

## Common Response Patterns

### Success Response (200 OK):
```json
{
  "entity": { /* entity data */ },
  "metadata": {
    "id": "uuid",
    "state": "current_state",
    "version": 1,
    "createdAt": "timestamp",
    "updatedAt": "timestamp"
  }
}
```

### Error Response (400 Bad Request):
```json
{
  "error": "Validation failed",
  "message": "Pet name is required",
  "timestamp": "2024-01-15T10:30:00"
}
```

### Not Found Response (404 Not Found):
```json
{
  "error": "Not found",
  "message": "Pet with ID PET001 not found",
  "timestamp": "2024-01-15T10:30:00"
}
```

## Controller Implementation Notes

### EntityService Integration:
- Use `entityService.create()` for POST operations
- Use `entityService.getById()` for UUID-based GET operations
- Use `entityService.findByBusinessId()` for business ID lookups
- Use `entityService.update()` with transition parameter for PUT operations
- Use `entityService.search()` for complex queries

### Validation:
- Validate required fields before EntityService calls
- Return appropriate HTTP status codes
- Include meaningful error messages

### Logging:
- Log all CRUD operations with entity IDs
- Log workflow transitions
- Log errors with full context

### Cross-Origin Support:
- Include `@CrossOrigin(origins = "*")` for development
- Configure proper CORS for production

### Request/Response DTOs:
- Create specific DTOs for complex search operations
- Use nested classes for grouped data (CustomerInfo, Address)
- Implement proper JSON serialization/deserialization

This controller design provides a complete REST API for the Purrfect Pets application while maintaining proper integration with the Cyoda workflow system.
