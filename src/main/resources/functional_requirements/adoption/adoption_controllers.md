# Adoption Controller Requirements

## Overview
AdoptionController manages adoption entities and their workflow transitions in the Purrfect Pets system.

## Endpoints

### POST /adoptions
Create a new adoption application
**Request**:
```json
{
  "adoptionId": "ADO001",
  "petId": "PET001",
  "ownerId": "OWN001",
  "applicationNotes": "I have experience with large dogs",
  "homeVisitRequired": true
}
```
**Response**:
```json
{
  "entity": { /* adoption data */ },
  "meta": {
    "uuid": "uuid-789",
    "state": "applied"
  }
}
```

### PUT /adoptions/{uuid}
Update adoption with optional transition
**Request**:
```json
{
  "entity": { /* updated adoption data */ },
  "transitionName": "start_review"
}
```

### GET /adoptions/{uuid}
Get adoption by UUID
**Response**: EntityWithMetadata<Adoption>

### GET /adoptions
List all adoptions with optional filters
**Response**: List<EntityWithMetadata<Adoption>>

### POST /adoptions/{uuid}/transitions/{transitionName}
Execute specific transition
**Request**: Optional transition parameters
**Response**: Updated EntityWithMetadata<Adoption>
