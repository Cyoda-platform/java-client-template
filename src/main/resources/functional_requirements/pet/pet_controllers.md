# Pet Controller Requirements

## Overview
PetController provides REST endpoints for managing pets in the Purrfect Pets store.

## Endpoints

### GET /pets
- **Purpose**: Get all pets
- **Request**: None
- **Response**: List of EntityWithMetadata<Pet>

### GET /pets/{id}
- **Purpose**: Get pet by ID
- **Request**: Path parameter id
- **Response**: EntityWithMetadata<Pet>

### POST /pets
- **Purpose**: Create new pet
- **Request**: Pet entity data
- **Response**: EntityWithMetadata<Pet>
- **Example Request**:
```json
{
  "name": "Buddy",
  "species": "Dog",
  "breed": "Golden Retriever",
  "age": 24,
  "description": "Friendly and energetic",
  "price": 500.00,
  "imageUrl": "https://example.com/buddy.jpg"
}
```

### PUT /pets/{id}
- **Purpose**: Update pet with optional state transition
- **Request**: Pet entity data + optional transition name
- **Response**: EntityWithMetadata<Pet>
- **Transitions**: reserve_pet, adopt_pet, cancel_reservation

### DELETE /pets/{id}
- **Purpose**: Delete pet
- **Request**: Path parameter id
- **Response**: Success message
