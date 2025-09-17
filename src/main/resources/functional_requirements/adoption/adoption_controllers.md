# Adoption Controller Requirements

## Controller: AdoptionController

**Purpose**: REST API endpoints for managing pet adoptions in the Purrfect Pets system.

**Endpoints**:

### POST /adoptions
Create a new adoption application
- **Request**: Adoption entity data
- **Response**: EntityWithMetadata<Adoption>
- **Example Request**:
```json
{
  "adoptionId": "ADO001",
  "petId": "PET001",
  "ownerId": "OWN001",
  "applicationDate": "2024-01-20T14:30:00",
  "notes": "Interested in adopting Buddy",
  "fee": 150.00
}
```

### GET /adoptions/{id}
Get adoption by ID
- **Response**: EntityWithMetadata<Adoption>

### PUT /adoptions/{id}
Update adoption with optional transition
- **Request**: Adoption data + optional transition name
- **Transitions**: "start_review", "approve_adoption", "complete_adoption"
- **Example Request**:
```json
{
  "transition": "start_review",
  "entity": { "adoptionId": "ADO001", "notes": "Application looks good" }
}
```

### GET /adoptions
List all adoptions with optional filtering
- **Query Parameters**: petId, ownerId, status
- **Response**: List<EntityWithMetadata<Adoption>>

### GET /adoptions/by-pet/{petId}
Get adoptions for specific pet
- **Response**: List<EntityWithMetadata<Adoption>>

### GET /adoptions/by-owner/{ownerId}
Get adoptions for specific owner
- **Response**: List<EntityWithMetadata<Adoption>>
