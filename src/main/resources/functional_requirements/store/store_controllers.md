# Store Controller Requirements

## Overview
StoreController manages REST endpoints for store operations and inventory management.

## Endpoints

### GET /stores
List all stores with optional filtering.

**Request Example:**
```
GET /stores?operational=true
```

**Response Example:**
```json
[
  {
    "uuid": "store-123",
    "name": "Purrfect Pets Downtown",
    "address": "789 Pet Street, Downtown, State",
    "phone": "+1555123456",
    "email": "downtown@purrfectpets.com",
    "managerName": "Alice Johnson",
    "operatingHours": "9:00 AM - 8:00 PM",
    "capacity": 50,
    "meta": {
      "state": "operational",
      "createdAt": "2024-01-01T08:00:00Z"
    }
  }
]
```

### POST /stores
Create a new store.

**Request Example:**
```json
{
  "name": "Purrfect Pets Uptown",
  "address": "321 Animal Ave, Uptown, State",
  "phone": "+1555987654",
  "email": "uptown@purrfectpets.com",
  "managerName": "Bob Wilson",
  "operatingHours": "10:00 AM - 7:00 PM",
  "capacity": 30
}
```

### PUT /stores/{id}/transition
Execute workflow transition.

**Request Example:**
```json
{
  "transitionName": "open_store",
  "data": {
    "inspectionPassed": true,
    "staffTrained": true
  }
}
```
