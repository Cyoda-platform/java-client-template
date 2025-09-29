# Pet Controller Requirements

## Overview
PetController manages REST API endpoints for pet operations in the Purrfect Pets store.

## Endpoints

### GET /api/pets
Get all available pets
**Response**: List of Pet entities with metadata

### GET /api/pets/{petId}
Get specific pet by ID
**Response**: Pet entity with metadata

### POST /api/pets
Create new pet
**Request**: Pet entity data
**Response**: Created Pet entity with metadata

### PUT /api/pets/{petId}
Update pet with optional state transition
**Request**: 
```json
{
  "pet": { "name": "Fluffy", "species": "cat", "age": 12, "price": 150.0, "vaccinated": true, "neutered": true },
  "transitionName": "reserve_pet"
}
```
**Response**: Updated Pet entity with metadata

### DELETE /api/pets/{petId}
Mark pet as unavailable
**Request**: Empty body with transition
**Response**: Updated Pet entity with metadata

## Transition Names
- initialize_pet (automatic)
- reserve_pet
- complete_adoption  
- cancel_reservation
- mark_unavailable
- mark_available
