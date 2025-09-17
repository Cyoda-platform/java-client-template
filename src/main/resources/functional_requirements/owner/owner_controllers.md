# Owner Controller Requirements

## Overview
OwnerController manages owner entities and their workflow transitions in the Purrfect Pets system.

## Endpoints

### POST /owners
Create a new owner
**Request**:
```json
{
  "ownerId": "OWN001",
  "firstName": "John",
  "lastName": "Smith",
  "email": "john.smith@email.com",
  "phone": "555-1234",
  "address": {
    "line1": "123 Main St",
    "city": "Anytown",
    "state": "CA",
    "zipCode": "12345",
    "country": "USA"
  },
  "housingType": "House",
  "hasYard": true,
  "petPreferences": {
    "preferredSpecies": "Dog",
    "preferredSize": "Medium",
    "maxAdoptionFee": 300.0
  }
}
```
**Response**:
```json
{
  "entity": { /* owner data */ },
  "meta": {
    "uuid": "uuid-456",
    "state": "registered"
  }
}
```

### PUT /owners/{uuid}
Update owner with optional transition
**Request**:
```json
{
  "entity": { /* updated owner data */ },
  "transitionName": "verify_owner"
}
```

### GET /owners/{uuid}
Get owner by UUID
**Response**: EntityWithMetadata<Owner>

### GET /owners
List all owners
**Response**: List<EntityWithMetadata<Owner>>
