# Owner Controller Requirements

## Overview
OwnerController manages REST endpoints for customer registration and account management.

## Endpoints

### GET /owners
List all owners with optional filtering.

**Request Example:**
```
GET /owners?verified=true
```

**Response Example:**
```json
[
  {
    "uuid": "owner-123",
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phone": "+1234567890",
    "address": "123 Main St, City, State",
    "dateOfBirth": "1990-05-15",
    "verified": true,
    "meta": {
      "state": "active",
      "createdAt": "2024-01-10T09:00:00Z"
    }
  }
]
```

### POST /owners
Register a new owner.

**Request Example:**
```json
{
  "firstName": "Jane",
  "lastName": "Smith",
  "email": "jane.smith@example.com",
  "phone": "+1987654321",
  "address": "456 Oak Ave, City, State",
  "dateOfBirth": "1985-08-20"
}
```

### PUT /owners/{id}/transition
Execute workflow transition.

**Request Example:**
```json
{
  "transitionName": "verify_owner",
  "data": {
    "verificationDocuments": ["id-scan.pdf", "address-proof.pdf"]
  }
}
```
