# Owner Controller Requirements

## Overview
OwnerController manages REST endpoints for owner registration, verification, and profile management.

## Endpoints

### POST /api/owners
Register a new owner.

**Request Example:**
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "phone": "+1234567890",
  "address": "123 Main St, City, State",
  "experienceWithPets": "Had dogs for 10 years",
  "housingType": "house"
}
```

**Response Example:**
```json
{
  "entity": {
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phone": "+1234567890",
    "address": "123 Main St, City, State",
    "experienceWithPets": "Had dogs for 10 years",
    "housingType": "house",
    "registrationDate": "2024-01-20"
  },
  "meta": {
    "uuid": "owner-456",
    "state": "registered"
  }
}
```

### GET /api/owners/{id}
Get owner profile details.

### POST /api/owners/{id}/verify
Verify owner for pet adoption.

**Request Example:**
```json
{
  "transitionName": "verify_owner",
  "verificationDocuments": "ID and address proof submitted"
}
```

### POST /api/owners/{id}/suspend
Suspend owner account.

**Request Example:**
```json
{
  "transitionName": "suspend_owner",
  "suspensionReason": "Policy violation"
}
```
