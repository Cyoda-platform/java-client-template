# Owner Controller Requirements

## Controller: OwnerController

**Purpose**: REST API endpoints for managing pet owners in the Purrfect Pets system.

**Endpoints**:

### POST /owners
Create a new owner
- **Request**: Owner entity data
- **Response**: EntityWithMetadata<Owner>
- **Example Request**:
```json
{
  "ownerId": "OWN001",
  "firstName": "John",
  "lastName": "Smith",
  "email": "john.smith@email.com",
  "phone": "+1234567890",
  "address": {
    "street": "123 Main St",
    "city": "Springfield",
    "state": "IL",
    "zipCode": "62701",
    "country": "USA"
  },
  "experience": "First-time pet owner",
  "preferences": {
    "preferredSpecies": "dog",
    "preferredSize": "medium",
    "preferredAge": 2
  }
}
```

### GET /owners/{id}
Get owner by ID
- **Response**: EntityWithMetadata<Owner>

### PUT /owners/{id}
Update owner with optional transition
- **Request**: Owner data + optional transition name
- **Transitions**: "verify_owner", "approve_owner"
- **Example Request**:
```json
{
  "transition": "verify_owner",
  "entity": { "ownerId": "OWN001", "firstName": "John" }
}
```

### GET /owners
List all owners
- **Response**: List<EntityWithMetadata<Owner>>
