# Purrfect Pets - Controller Requirements

## Overview
This document defines the detailed requirements for all REST controllers in the Purrfect Pets application. Each entity has its own controller that provides HTTP API endpoints.

## 1. PetController

**Package:** `com.java_template.application.controller`  
**Base Path:** `/api/v1/pets`  
**Description:** Manages pet-related operations in the pet store

### Endpoints

#### GET /api/v1/pets
**Purpose:** Retrieve all pets or filter by status  
**Parameters:**
- `status` (query, optional): Filter by pet status ("available", "pending", "sold")
- `category` (query, optional): Filter by category name
- `tags` (query, optional): Filter by tags (comma-separated)

**Request Example:**
```
GET /api/v1/pets?status=available&category=Dogs
```

**Response Example:**
```json
{
  "success": true,
  "data": [
    {
      "entity": {
        "petId": "pet-001",
        "name": "Buddy",
        "category": {
          "categoryId": "cat-001",
          "name": "Dogs"
        },
        "photoUrls": ["https://example.com/buddy1.jpg"],
        "price": 500.00,
        "breed": "Golden Retriever"
      },
      "meta": {
        "uuid": "uuid-123",
        "state": "available",
        "version": 1
      }
    }
  ]
}
```

#### GET /api/v1/pets/{petId}
**Purpose:** Retrieve a specific pet by ID  

**Request Example:**
```
GET /api/v1/pets/pet-001
```

**Response Example:**
```json
{
  "success": true,
  "data": {
    "entity": {
      "petId": "pet-001",
      "name": "Buddy",
      "category": {
        "categoryId": "cat-001",
        "name": "Dogs"
      },
      "photoUrls": ["https://example.com/buddy1.jpg"],
      "tags": [
        {
          "tagId": "tag-001",
          "name": "friendly"
        }
      ],
      "price": 500.00,
      "breed": "Golden Retriever",
      "weight": 25.5,
      "vaccinated": true
    },
    "meta": {
      "uuid": "uuid-123",
      "state": "available",
      "version": 1
    }
  }
}
```

#### POST /api/v1/pets
**Purpose:** Add a new pet to the store  

**Request Example:**
```json
{
  "petId": "pet-002",
  "name": "Fluffy",
  "category": {
    "categoryId": "cat-002",
    "name": "Cats"
  },
  "photoUrls": ["https://example.com/fluffy1.jpg", "https://example.com/fluffy2.jpg"],
  "tags": [
    {
      "tagId": "tag-002",
      "name": "playful"
    }
  ],
  "description": "A beautiful Persian cat",
  "price": 800.00,
  "breed": "Persian",
  "weight": 4.2,
  "vaccinated": true
}
```

**Response Example:**
```json
{
  "success": true,
  "data": {
    "entity": {
      "petId": "pet-002",
      "name": "Fluffy",
      "category": {
        "categoryId": "cat-002",
        "name": "Cats"
      },
      "photoUrls": ["https://example.com/fluffy1.jpg", "https://example.com/fluffy2.jpg"],
      "price": 800.00,
      "createdAt": "2024-01-15T10:30:00"
    },
    "meta": {
      "uuid": "uuid-456",
      "state": "available",
      "version": 1
    }
  }
}
```

#### PUT /api/v1/pets/{petId}
**Purpose:** Update an existing pet  
**Parameters:**
- `transitionName` (query, optional): Workflow transition to execute ("reserve_pet", "sell_pet_direct", "cancel_reservation", "complete_sale", "return_pet")

**Request Example (Update without transition):**
```
PUT /api/v1/pets/pet-001
```
```json
{
  "name": "Buddy Updated",
  "description": "Updated description",
  "price": 550.00,
  "weight": 26.0
}
```

**Request Example (Update with transition):**
```
PUT /api/v1/pets/pet-001?transitionName=reserve_pet
```
```json
{
  "customerInfo": {
    "customerId": "user-001",
    "name": "John Doe",
    "email": "john@example.com"
  }
}
```

**Response Example:**
```json
{
  "success": true,
  "data": {
    "entity": {
      "petId": "pet-001",
      "name": "Buddy Updated",
      "price": 550.00,
      "updatedAt": "2024-01-15T11:00:00"
    },
    "meta": {
      "uuid": "uuid-123",
      "state": "pending",
      "version": 2
    }
  }
}
```

#### DELETE /api/v1/pets/{petId}
**Purpose:** Remove a pet from the store  

**Request Example:**
```
DELETE /api/v1/pets/pet-001
```

**Response Example:**
```json
{
  "success": true,
  "message": "Pet removed successfully"
}
```

## 2. OrderController

**Package:** `com.java_template.application.controller`  
**Base Path:** `/api/v1/orders`  
**Description:** Manages order operations in the pet store

### Endpoints

#### GET /api/v1/orders
**Purpose:** Retrieve orders (optionally filtered by customer)  
**Parameters:**
- `customerId` (query, optional): Filter by customer ID
- `status` (query, optional): Filter by order status ("placed", "approved", "delivered", "cancelled", "returned")

**Request Example:**
```
GET /api/v1/orders?customerId=user-001&status=placed
```

**Response Example:**
```json
{
  "success": true,
  "data": [
    {
      "entity": {
        "orderId": "order-001",
        "petId": "pet-001",
        "quantity": 1,
        "customerInfo": {
          "customerId": "user-001",
          "firstName": "John",
          "lastName": "Doe",
          "email": "john@example.com"
        },
        "totalAmount": 500.00,
        "createdAt": "2024-01-15T10:00:00"
      },
      "meta": {
        "uuid": "order-uuid-123",
        "state": "placed",
        "version": 1
      }
    }
  ]
}
```

#### GET /api/v1/orders/{orderId}
**Purpose:** Retrieve a specific order by ID  

**Request Example:**
```
GET /api/v1/orders/order-001
```

**Response Example:**
```json
{
  "success": true,
  "data": {
    "entity": {
      "orderId": "order-001",
      "petId": "pet-001",
      "quantity": 1,
      "customerInfo": {
        "customerId": "user-001",
        "firstName": "John",
        "lastName": "Doe",
        "email": "john@example.com",
        "phone": "+1234567890"
      },
      "totalAmount": 500.00,
      "paymentMethod": "credit_card",
      "shippingAddress": {
        "line1": "123 Main St",
        "city": "Anytown",
        "state": "CA",
        "postcode": "12345",
        "country": "USA"
      },
      "createdAt": "2024-01-15T10:00:00"
    },
    "meta": {
      "uuid": "order-uuid-123",
      "state": "placed",
      "version": 1
    }
  }
}
```

#### POST /api/v1/orders
**Purpose:** Place a new order  

**Request Example:**
```json
{
  "petId": "pet-001",
  "quantity": 1,
  "customerInfo": {
    "customerId": "user-001",
    "firstName": "John",
    "lastName": "Doe",
    "email": "john@example.com",
    "phone": "+1234567890"
  },
  "paymentMethod": "credit_card",
  "shippingAddress": {
    "line1": "123 Main St",
    "city": "Anytown",
    "state": "CA",
    "postcode": "12345",
    "country": "USA"
  },
  "orderNotes": "Please handle with care"
}
```

**Response Example:**
```json
{
  "success": true,
  "data": {
    "entity": {
      "orderId": "order-002",
      "petId": "pet-001",
      "quantity": 1,
      "totalAmount": 500.00,
      "createdAt": "2024-01-15T11:00:00"
    },
    "meta": {
      "uuid": "order-uuid-456",
      "state": "placed",
      "version": 1
    }
  }
}
```

#### PUT /api/v1/orders/{orderId}
**Purpose:** Update an existing order  
**Parameters:**
- `transitionName` (query, optional): Workflow transition to execute ("approve_order", "cancel_order", "deliver_order", "cancel_approved_order", "return_order")

**Request Example (Update with transition):**
```
PUT /api/v1/orders/order-001?transitionName=approve_order
```
```json
{
  "approvalNotes": "Order approved by manager",
  "estimatedDelivery": "2024-01-20T10:00:00"
}
```

**Response Example:**
```json
{
  "success": true,
  "data": {
    "entity": {
      "orderId": "order-001",
      "approvalNotes": "Order approved by manager",
      "updatedAt": "2024-01-15T12:00:00"
    },
    "meta": {
      "uuid": "order-uuid-123",
      "state": "approved",
      "version": 2
    }
  }
}
```

#### DELETE /api/v1/orders/{orderId}
**Purpose:** Cancel/delete an order  

**Request Example:**
```
DELETE /api/v1/orders/order-001
```

**Response Example:**
```json
{
  "success": true,
  "message": "Order cancelled successfully"
}
```

## 3. UserController

**Package:** `com.java_template.application.controller`  
**Base Path:** `/api/v1/users`  
**Description:** Manages user account operations

### Endpoints

#### GET /api/v1/users/{username}
**Purpose:** Get user by username  

**Request Example:**
```
GET /api/v1/users/johndoe
```

**Response Example:**
```json
{
  "success": true,
  "data": {
    "entity": {
      "userId": "user-001",
      "username": "johndoe",
      "firstName": "John",
      "lastName": "Doe",
      "email": "john@example.com",
      "phone": "+1234567890",
      "registrationDate": "2024-01-10T09:00:00",
      "isActive": true
    },
    "meta": {
      "uuid": "user-uuid-123",
      "state": "active",
      "version": 1
    }
  }
}
```

#### POST /api/v1/users
**Purpose:** Create a new user  

**Request Example:**
```json
{
  "username": "janedoe",
  "firstName": "Jane",
  "lastName": "Doe",
  "email": "jane@example.com",
  "phone": "+1234567891",
  "password": "securePassword123",
  "addresses": [
    {
      "line1": "456 Oak St",
      "city": "Somewhere",
      "state": "NY",
      "postcode": "54321",
      "country": "USA",
      "isDefault": true
    }
  ],
  "preferences": {
    "preferredCategories": ["Dogs", "Cats"],
    "emailNotifications": true,
    "smsNotifications": false,
    "preferredContactMethod": "email"
  }
}
```

**Response Example:**
```json
{
  "success": true,
  "data": {
    "entity": {
      "userId": "user-002",
      "username": "janedoe",
      "firstName": "Jane",
      "lastName": "Doe",
      "email": "jane@example.com",
      "registrationDate": "2024-01-15T13:00:00"
    },
    "meta": {
      "uuid": "user-uuid-456",
      "state": "registered",
      "version": 1
    }
  }
}
```

#### PUT /api/v1/users/{username}
**Purpose:** Update user information  
**Parameters:**
- `transitionName` (query, optional): Workflow transition to execute ("activate_user", "suspend_user", "deactivate_user", "reactivate_user", "deactivate_unverified", "deactivate_suspended", "reactivate_inactive")

**Request Example (Update without transition):**
```
PUT /api/v1/users/johndoe
```
```json
{
  "firstName": "John Updated",
  "phone": "+1234567899",
  "preferences": {
    "emailNotifications": false,
    "smsNotifications": true
  }
}
```

**Request Example (Update with transition):**
```
PUT /api/v1/users/johndoe?transitionName=activate_user
```
```json
{
  "verificationToken": "abc123xyz",
  "activationNotes": "Email verified successfully"
}
```

**Response Example:**
```json
{
  "success": true,
  "data": {
    "entity": {
      "userId": "user-001",
      "username": "johndoe",
      "firstName": "John Updated",
      "lastLoginDate": "2024-01-15T14:00:00"
    },
    "meta": {
      "uuid": "user-uuid-123",
      "state": "active",
      "version": 2
    }
  }
}
```

#### DELETE /api/v1/users/{username}
**Purpose:** Delete/deactivate a user account  

**Request Example:**
```
DELETE /api/v1/users/johndoe
```

**Response Example:**
```json
{
  "success": true,
  "message": "User account deactivated successfully"
}
```

#### POST /api/v1/users/login
**Purpose:** User login  

**Request Example:**
```json
{
  "username": "johndoe",
  "password": "userPassword123"
}
```

**Response Example:**
```json
{
  "success": true,
  "data": {
    "token": "jwt-token-here",
    "user": {
      "userId": "user-001",
      "username": "johndoe",
      "firstName": "John",
      "lastName": "Doe"
    }
  }
}
```

#### POST /api/v1/users/logout
**Purpose:** User logout  

**Request Example:**
```
POST /api/v1/users/logout
Authorization: Bearer jwt-token-here
```

**Response Example:**
```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

## Controller Implementation Notes

### Common Response Format
All controllers should use a consistent response format:
```json
{
  "success": boolean,
  "data": object | array,
  "message": string,
  "errors": array
}
```

### Error Handling
- **400 Bad Request:** Invalid input data or validation errors
- **401 Unauthorized:** Authentication required
- **403 Forbidden:** Insufficient permissions
- **404 Not Found:** Resource not found
- **409 Conflict:** State transition not allowed
- **500 Internal Server Error:** Server-side errors

### Security Considerations
1. **Authentication:** Secure endpoints require valid JWT tokens
2. **Authorization:** Users can only access their own data unless admin
3. **Input Validation:** Validate all input data before processing
4. **Rate Limiting:** Implement rate limiting for API endpoints

### Workflow Integration
1. **Transition Parameters:** Use `transitionName` query parameter for state changes
2. **State Validation:** Ensure current entity state allows the requested transition
3. **Error Handling:** Return appropriate errors for invalid transitions
4. **Audit Logging:** Log all state transitions for audit purposes
