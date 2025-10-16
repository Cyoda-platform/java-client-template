# Pet Controller Requirements

## Base Path
All pet endpoints are mapped to `/ui/pet/**`

## CRUD Operations

### 1. Create Pet
- **Endpoint**: `POST /ui/pet`
- **Request Body**: Pet entity
- **Response**: EntityWithMetadata<Pet> with 201 Created
- **Business Logic**: 
  - Check for duplicate petId
  - Validate required fields
  - Return 409 Conflict if pet already exists

### 2. Get Pet by Technical ID
- **Endpoint**: `GET /ui/pet/{id}`
- **Parameters**: 
  - `id` (UUID) - Technical ID
  - `pointInTime` (OffsetDateTime, optional) - Historical query
- **Response**: EntityWithMetadata<Pet> or 404 Not Found

### 3. Get Pet by Business ID
- **Endpoint**: `GET /ui/pet/business/{petId}`
- **Parameters**: 
  - `petId` (String) - Business ID
  - `pointInTime` (OffsetDateTime, optional) - Historical query
- **Response**: EntityWithMetadata<Pet> or 404 Not Found

### 4. Update Pet
- **Endpoint**: `PUT /ui/pet/{id}`
- **Parameters**: 
  - `id` (UUID) - Technical ID
  - `transition` (String, optional) - Workflow transition
- **Request Body**: Pet entity
- **Response**: EntityWithMetadata<Pet>

### 5. Delete Pet
- **Endpoint**: `DELETE /ui/pet/{id}`
- **Parameters**: `id` (UUID) - Technical ID
- **Response**: 204 No Content

### 6. Delete Pet by Business ID
- **Endpoint**: `DELETE /ui/pet/business/{petId}`
- **Parameters**: `petId` (String) - Business ID
- **Response**: 204 No Content

## Search and Filter Operations

### 7. List All Pets
- **Endpoint**: `GET /ui/pet`
- **Parameters**: 
  - Pagination: `page`, `size`
  - Filters: `status`, `category`, `breed`
  - `pointInTime` (OffsetDateTime, optional)
- **Response**: Page<EntityWithMetadata<Pet>>

### 8. Find Pets by Status
- **Endpoint**: `GET /ui/pet/findByStatus`
- **Parameters**: 
  - `status` (String) - Pet status (available, pending, sold)
  - `pointInTime` (OffsetDateTime, optional)
- **Response**: List<EntityWithMetadata<Pet>>

### 9. Find Pets by Tags
- **Endpoint**: `GET /ui/pet/findByTags`
- **Parameters**: 
  - `tags` (List<String>) - Tag names to filter by
  - `pointInTime` (OffsetDateTime, optional)
- **Response**: List<EntityWithMetadata<Pet>>

### 10. Search Pets by Name
- **Endpoint**: `GET /ui/pet/search`
- **Parameters**: 
  - `name` (String) - Pet name to search
  - `pointInTime` (OffsetDateTime, optional)
- **Response**: List<EntityWithMetadata<Pet>>

## Workflow Transition Operations

### 11. Reserve Pet
- **Endpoint**: `POST /ui/pet/{id}/reserve`
- **Parameters**: `id` (UUID) - Technical ID
- **Response**: EntityWithMetadata<Pet>
- **Business Logic**: Triggers "reserve_pet" transition

### 12. Complete Pet Sale
- **Endpoint**: `POST /ui/pet/{id}/sell`
- **Parameters**: `id` (UUID) - Technical ID
- **Response**: EntityWithMetadata<Pet>
- **Business Logic**: Triggers "complete_sale" transition

### 13. Cancel Pet Reservation
- **Endpoint**: `POST /ui/pet/{id}/cancel-reservation`
- **Parameters**: `id` (UUID) - Technical ID
- **Response**: EntityWithMetadata<Pet>
- **Business Logic**: Triggers "cancel_reservation" transition

## Additional Operations

### 14. Get Pet Change History
- **Endpoint**: `GET /ui/pet/{id}/changes`
- **Parameters**: 
  - `id` (UUID) - Technical ID
  - `pointInTime` (OffsetDateTime, optional)
- **Response**: List<EntityChangeMeta>

### 15. Advanced Pet Search
- **Endpoint**: `POST /ui/pet/search/advanced`
- **Request Body**: PetSearchRequest
- **Response**: List<EntityWithMetadata<Pet>>
- **Search Criteria**: name, category, breed, minPrice, maxPrice, age range, etc.
