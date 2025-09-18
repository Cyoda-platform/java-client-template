# Adoption Controller Requirements

## Overview
AdoptionController manages REST endpoints for the adoption process including application submission, approval, and completion.

## Endpoints

### POST /api/adoptions
Submit a new adoption application.

**Request Example:**
```json
{
  "petId": "pet-123",
  "ownerId": "owner-456",
  "notes": "Excited to provide a loving home"
}
```

**Response Example:**
```json
{
  "entity": {
    "petId": "pet-123",
    "ownerId": "owner-456",
    "applicationDate": "2024-01-20",
    "adoptionFee": 150.0,
    "notes": "Excited to provide a loving home"
  },
  "meta": {
    "uuid": "adoption-789",
    "state": "pending"
  }
}
```

### GET /api/adoptions/{id}
Get adoption application details.

### POST /api/adoptions/{id}/approve
Approve an adoption application.

**Request Example:**
```json
{
  "transitionName": "approve_adoption",
  "approvalNotes": "Owner verified and pet is suitable match"
}
```

### POST /api/adoptions/{id}/complete
Complete the adoption process.

**Request Example:**
```json
{
  "transitionName": "complete_adoption",
  "completionNotes": "Payment processed, adoption certificate issued"
}
```

### POST /api/adoptions/{id}/cancel
Cancel an adoption application.

**Request Example:**
```json
{
  "transitionName": "cancel_adoption",
  "cancellationReason": "Owner changed mind"
}
```

### GET /api/adoptions
List adoption applications with filtering.

**Request Example:**
```
GET /api/adoptions?ownerId=owner-456&state=pending
```
