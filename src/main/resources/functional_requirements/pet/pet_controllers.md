# Pet Controller Requirements

## Overview
PetController manages pet entities and their workflow transitions in the Purrfect Pets system.

## Endpoints

### POST /pets
Create a new pet
**Request**:
```json
{
  "petId": "PET001",
  "name": "Buddy",
  "species": "Dog",
  "breed": "Golden Retriever",
  "age": 3,
  "gender": "Male",
  "size": "Large",
  "color": "Golden",
  "description": "Friendly and energetic dog",
  "healthStatus": "Healthy",
  "vaccinated": true,
  "spayedNeutered": true,
  "adoptionFee": 250.0
}
```
**Response**:
```json
{
  "entity": { /* pet data */ },
  "meta": {
    "uuid": "uuid-123",
    "state": "available"
  }
}
```

### PUT /pets/{uuid}
Update pet with optional transition
**Request**:
```json
{
  "entity": { /* updated pet data */ },
  "transitionName": "reserve_pet"
}
```

### GET /pets/{uuid}
Get pet by UUID
**Response**: EntityWithMetadata<Pet>

### GET /pets
List all pets with optional filters
**Response**: List<EntityWithMetadata<Pet>>

### POST /pets/{uuid}/transitions/{transitionName}
Execute specific transition
**Request**: Optional transition parameters
**Response**: Updated EntityWithMetadata<Pet>
