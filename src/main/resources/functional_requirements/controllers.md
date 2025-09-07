# Controllers Specification - Purrfect Pets API

## Overview
This document defines the REST API controllers for the Purrfect Pets API. Each entity has its own controller providing CRUD operations and workflow state transitions.

## 1. PetController

**Base Path:** `/ui/pet`  
**Description:** Manages pet entities and their lifecycle operations

### Endpoints

#### Create Pet
- **Method:** POST
- **Path:** `/ui/pet`
- **Description:** Register a new pet
- **Transition:** Triggers `register_pet` transition (none → REGISTERED)

**Request Body:**
```json
{
  "name": "Buddy",
  "species": "Dog",
  "breed": "Golden Retriever",
  "age": 3,
  "weight": 25.5,
  "color": "Golden",
  "ownerId": "OWNER-001",
  "healthNotes": "Vaccinated, no known allergies",
  "photoUrl": "https://example.com/photos/buddy.jpg"
}
```

**Response:**
```json
{
  "entity": {
    "petId": "PET-001",
    "name": "Buddy",
    "species": "Dog",
    "breed": "Golden Retriever",
    "age": 3,
    "weight": 25.5,
    "color": "Golden",
    "ownerId": "OWNER-001",
    "healthNotes": "Vaccinated, no known allergies",
    "photoUrl": "https://example.com/photos/buddy.jpg",
    "registrationDate": "2024-01-15T10:30:00Z"
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "REGISTERED"
  }
}
```

#### Get Pet by ID
- **Method:** GET
- **Path:** `/ui/pet/{id}`
- **Description:** Retrieve pet by technical UUID

**Response:** Same as create response

#### Get Pet by Business ID
- **Method:** GET
- **Path:** `/ui/pet/business/{petId}`
- **Description:** Retrieve pet by business ID (petId)

**Response:** Same as create response

#### Get Pets by Owner
- **Method:** GET
- **Path:** `/ui/pet/owner/{ownerId}`
- **Description:** Retrieve all pets owned by a specific owner

**Response:**
```json
[
  {
    "entity": { /* pet data */ },
    "metadata": { /* metadata */ }
  }
]
```

#### Update Pet
- **Method:** PUT
- **Path:** `/ui/pet/{id}?transition={transitionName}`
- **Description:** Update pet and optionally trigger state transition

**Query Parameters:**
- `transition` (optional): Transition name (activate_pet, deactivate_pet, reactivate_pet, archive_pet)

**Request Body:**
```json
{
  "name": "Buddy",
  "species": "Dog",
  "breed": "Golden Retriever",
  "age": 4,
  "weight": 26.0,
  "color": "Golden",
  "ownerId": "OWNER-001",
  "healthNotes": "Updated health information",
  "photoUrl": "https://example.com/photos/buddy-updated.jpg"
}
```

#### Delete Pet
- **Method:** DELETE
- **Path:** `/ui/pet/{id}`
- **Description:** Delete pet by technical UUID

---

## 2. OwnerController

**Base Path:** `/ui/owner`  
**Description:** Manages owner entities and their lifecycle operations

### Endpoints

#### Create Owner
- **Method:** POST
- **Path:** `/ui/owner`
- **Description:** Register a new owner
- **Transition:** Triggers `register_owner` transition (none → PENDING)

**Request Body:**
```json
{
  "firstName": "John",
  "lastName": "Smith",
  "email": "john.smith@email.com",
  "phoneNumber": "+1-555-0123",
  "address": "123 Main Street",
  "city": "Springfield",
  "zipCode": "12345",
  "emergencyContact": "Jane Smith +1-555-0124",
  "preferredVet": "Dr. Johnson Animal Clinic"
}
```

**Response:**
```json
{
  "entity": {
    "ownerId": "OWNER-001",
    "firstName": "John",
    "lastName": "Smith",
    "email": "john.smith@email.com",
    "phoneNumber": "+1-555-0123",
    "address": "123 Main Street",
    "city": "Springfield",
    "zipCode": "12345",
    "emergencyContact": "Jane Smith +1-555-0124",
    "preferredVet": "Dr. Johnson Animal Clinic",
    "registrationDate": "2024-01-15T09:00:00Z",
    "totalPets": 0
  },
  "metadata": {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "state": "PENDING"
  }
}
```

#### Get Owner by ID
- **Method:** GET
- **Path:** `/ui/owner/{id}`
- **Description:** Retrieve owner by technical UUID

#### Get Owner by Business ID
- **Method:** GET
- **Path:** `/ui/owner/business/{ownerId}`
- **Description:** Retrieve owner by business ID (ownerId)

#### Update Owner
- **Method:** PUT
- **Path:** `/ui/owner/{id}?transition={transitionName}`
- **Description:** Update owner and optionally trigger state transition

**Query Parameters:**
- `transition` (optional): Transition name (verify_owner, suspend_owner, reactivate_owner, close_owner_account)

#### Delete Owner
- **Method:** DELETE
- **Path:** `/ui/owner/{id}`
- **Description:** Delete owner by technical UUID

---

## 3. PetCareOrderController

**Base Path:** `/ui/order`  
**Description:** Manages pet care service orders and their lifecycle

### Endpoints

#### Create Order
- **Method:** POST
- **Path:** `/ui/order`
- **Description:** Create a new pet care service order
- **Transition:** Triggers `create_order` transition (none → PENDING)

**Request Body:**
```json
{
  "petId": "PET-001",
  "ownerId": "OWNER-001",
  "serviceType": "GROOMING",
  "serviceDescription": "Full grooming service including bath, nail trim, and haircut",
  "scheduledDate": "2024-01-20T14:00:00Z",
  "duration": 2,
  "specialInstructions": "Pet is nervous around loud noises",
  "cost": 75.00,
  "paymentMethod": "CREDIT_CARD"
}
```

**Response:**
```json
{
  "entity": {
    "orderId": "ORDER-001",
    "petId": "PET-001",
    "ownerId": "OWNER-001",
    "serviceType": "GROOMING",
    "serviceDescription": "Full grooming service including bath, nail trim, and haircut",
    "scheduledDate": "2024-01-20T14:00:00Z",
    "duration": 2,
    "specialInstructions": "Pet is nervous around loud noises",
    "cost": 75.00,
    "paymentMethod": "CREDIT_CARD",
    "orderDate": "2024-01-15T11:00:00Z"
  },
  "metadata": {
    "id": "770e8400-e29b-41d4-a716-446655440002",
    "state": "PENDING"
  }
}
```

#### Get Order by ID
- **Method:** GET
- **Path:** `/ui/order/{id}`
- **Description:** Retrieve order by technical UUID

#### Get Order by Business ID
- **Method:** GET
- **Path:** `/ui/order/business/{orderId}`
- **Description:** Retrieve order by business ID (orderId)

#### Get Orders by Pet
- **Method:** GET
- **Path:** `/ui/order/pet/{petId}`
- **Description:** Retrieve all orders for a specific pet

#### Get Orders by Owner
- **Method:** GET
- **Path:** `/ui/order/owner/{ownerId}`
- **Description:** Retrieve all orders placed by a specific owner

#### Update Order
- **Method:** PUT
- **Path:** `/ui/order/{id}?transition={transitionName}`
- **Description:** Update order and optionally trigger state transition

**Query Parameters:**
- `transition` (optional): Transition name (confirm_order, start_service, complete_service, cancel_order)

**Request Body for Service Completion:**
```json
{
  "petId": "PET-001",
  "ownerId": "OWNER-001",
  "serviceType": "GROOMING",
  "serviceDescription": "Full grooming service completed successfully",
  "scheduledDate": "2024-01-20T14:00:00Z",
  "duration": 2,
  "specialInstructions": "Pet is nervous around loud noises",
  "cost": 75.00,
  "paymentMethod": "CREDIT_CARD",
  "veterinarianName": "Dr. Sarah Wilson",
  "completionDate": "2024-01-20T16:00:00Z",
  "customerRating": 5,
  "notes": "Service completed successfully. Pet was well-behaved."
}
```

#### Delete Order
- **Method:** DELETE
- **Path:** `/ui/order/{id}`
- **Description:** Delete order by technical UUID

---

## API Response Standards

### Success Response Format
All successful responses follow the EntityWithMetadata pattern:
```json
{
  "entity": {
    // Entity-specific data
  },
  "metadata": {
    "id": "uuid",
    "state": "CURRENT_STATE",
    "createdAt": "timestamp",
    "updatedAt": "timestamp"
  }
}
```

### Error Response Format
```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable error message",
    "details": "Additional error details if available"
  }
}
```

### HTTP Status Codes
- `200 OK` - Successful GET, PUT operations
- `201 Created` - Successful POST operations
- `204 No Content` - Successful DELETE operations
- `400 Bad Request` - Invalid request data
- `404 Not Found` - Resource not found
- `409 Conflict` - State transition not allowed
- `500 Internal Server Error` - Server error

## Controller Implementation Notes

1. **Cross-Origin Support**: All controllers include `@CrossOrigin(origins = "*")` for development
2. **EntityService Integration**: Controllers use EntityService for all data operations
3. **State Transitions**: Update endpoints support optional transition parameters
4. **Business ID Support**: Separate endpoints for business ID lookups
5. **Validation**: Request validation is handled by entity validation and workflow criteria
6. **Error Handling**: Consistent error response format across all endpoints
