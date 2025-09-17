# Pet Controller Requirements

## Controller: PetController

**Purpose**: REST API endpoints for managing pets in the Purrfect Pets system.

**Endpoints**:

### POST /pets
Create a new pet
- **Request**: Pet entity data
- **Response**: EntityWithMetadata<Pet>
- **Example Request**:
```json
{
  "petId": "PET001",
  "name": "Buddy",
  "species": "dog",
  "breed": "Golden Retriever",
  "age": 3,
  "color": "golden",
  "size": "large",
  "healthStatus": "healthy",
  "description": "Friendly and energetic dog",
  "arrivalDate": "2024-01-15T10:00:00"
}
```

### GET /pets/{id}
Get pet by ID
- **Response**: EntityWithMetadata<Pet>

### PUT /pets/{id}
Update pet with optional transition
- **Request**: Pet data + optional transition name
- **Transitions**: "reserve_pet", "cancel_reservation", "complete_adoption"
- **Example Request**:
```json
{
  "transition": "reserve_pet",
  "entity": { "petId": "PET001", "name": "Buddy" }
}
```

### GET /pets
List all pets with optional filtering
- **Query Parameters**: species, size, healthStatus
- **Response**: List<EntityWithMetadata<Pet>>
