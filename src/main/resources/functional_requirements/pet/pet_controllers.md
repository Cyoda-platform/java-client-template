# Pet Controller Requirements

## Overview
PetController manages REST endpoints for pet operations including CRUD and workflow transitions.

## Endpoints

### GET /pets
List all pets with optional filtering.

**Request Example:**
```
GET /pets?species=dog&available=true
```

**Response Example:**
```json
[
  {
    "uuid": "pet-123",
    "name": "Buddy",
    "species": "dog",
    "breed": "Golden Retriever",
    "age": 24,
    "price": 500.00,
    "description": "Friendly family dog",
    "imageUrl": "https://example.com/buddy.jpg",
    "healthStatus": "healthy",
    "vaccinated": true,
    "meta": {
      "state": "available",
      "createdAt": "2024-01-15T10:00:00Z"
    }
  }
]
```

### POST /pets
Create a new pet.

**Request Example:**
```json
{
  "name": "Max",
  "species": "cat",
  "breed": "Persian",
  "age": 12,
  "price": 300.00,
  "description": "Calm indoor cat",
  "healthStatus": "healthy",
  "vaccinated": true
}
```

### PUT /pets/{id}/transition
Execute workflow transition.

**Request Example:**
```json
{
  "transitionName": "reserve_pet",
  "data": {
    "reservedBy": "owner-456",
    "reservationExpiry": "2024-01-20T10:00:00Z"
  }
}
```
