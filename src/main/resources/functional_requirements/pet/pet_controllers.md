# Pet Controller Requirements

## Overview
PetController manages REST endpoints for pet operations including search, reservation, adoption, and returns.

## Endpoints

### GET /api/pets
Search and list pets with filtering options.

**Request Example:**
```
GET /api/pets?species=dog&state=available&page=0&size=10
```

**Response Example:**
```json
{
  "content": [
    {
      "entity": {
        "name": "Buddy",
        "species": "dog",
        "breed": "Golden Retriever",
        "age": 3,
        "description": "Friendly and energetic",
        "medicalHistory": "Vaccinated, neutered",
        "adoptionFee": 150.0,
        "arrivalDate": "2024-01-15"
      },
      "meta": {
        "uuid": "pet-123",
        "state": "available"
      }
    }
  ],
  "totalElements": 1
}
```

### GET /api/pets/{id}
Get specific pet details.

### POST /api/pets/{id}/reserve
Reserve a pet for adoption.

**Request Example:**
```json
{
  "transitionName": "reserve_pet",
  "reservationDetails": "Reserved for John Doe"
}
```

### POST /api/pets/{id}/adopt
Complete pet adoption.

**Request Example:**
```json
{
  "transitionName": "adopt_pet",
  "adoptionDetails": "Adopted by verified owner"
}
```

### POST /api/pets/{id}/return
Return an adopted pet.

**Request Example:**
```json
{
  "transitionName": "return_pet",
  "returnReason": "Owner relocation"
}
```
