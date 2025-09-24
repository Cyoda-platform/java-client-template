# Pet Controller Requirements

## Overview
PetController manages REST API endpoints for pet operations including CRUD and workflow state transitions.

## Endpoints

### GET /api/pets
**Purpose**: Retrieve all pets with optional filtering
**Request**: `GET /api/pets?species=dog&status=available`
**Response**:
```json
[
  {
    "entity": {
      "petId": "pet-001",
      "name": "Buddy",
      "species": "dog",
      "breed": "Golden Retriever",
      "age": 3,
      "price": 250.0,
      "isVaccinated": true
    },
    "meta": {
      "uuid": "uuid-123",
      "state": "available"
    }
  }
]
```

### GET /api/pets/{id}
**Purpose**: Retrieve specific pet by ID
**Request**: `GET /api/pets/pet-001`
**Response**: Same as above but single pet object

### POST /api/pets
**Purpose**: Create new pet
**Request**:
```json
{
  "petId": "pet-002",
  "name": "Whiskers",
  "species": "cat",
  "breed": "Persian",
  "age": 2,
  "price": 300.0
}
```
**Response**: Created pet with metadata

### PUT /api/pets/{id}
**Purpose**: Update pet with optional state transition
**Request**: `PUT /api/pets/pet-001?transition=reserve_pet`
**Body**: Updated pet data
**Response**: Updated pet with new state

### DELETE /api/pets/{id}
**Purpose**: Remove pet from system
**Request**: `DELETE /api/pets/pet-001`
**Response**: 204 No Content
