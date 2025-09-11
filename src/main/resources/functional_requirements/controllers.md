# Controller Requirements

## MailController

### Overview
The MailController provides REST API endpoints for managing Mail entities throughout their workflow lifecycle. It handles CRUD operations and workflow transitions for mail processing.

### Base Path
`/api/mail`

### Endpoints

#### 1. Create Mail
**POST** `/api/mail`

Creates a new mail entity and starts the workflow.

**Request Body:**
```json
{
  "isHappy": true,
  "mailList": [
    "user1@example.com",
    "user2@example.com",
    "user3@example.com"
  ]
}
```

**Response:**
```json
{
  "uuid": "123e4567-e89b-12d3-a456-426614174000",
  "entity": {
    "isHappy": true,
    "mailList": [
      "user1@example.com",
      "user2@example.com", 
      "user3@example.com"
    ]
  },
  "meta": {
    "state": "INITIAL",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:00Z"
  }
}
```

**Status Codes:**
- 201: Created successfully
- 400: Invalid request body or validation errors
- 500: Internal server error

#### 2. Get Mail by ID
**GET** `/api/mail/{id}`

Retrieves a specific mail entity by its UUID.

**Path Parameters:**
- `id` (UUID): The unique identifier of the mail entity

**Response:**
```json
{
  "uuid": "123e4567-e89b-12d3-a456-426614174000",
  "entity": {
    "isHappy": true,
    "mailList": [
      "user1@example.com",
      "user2@example.com"
    ]
  },
  "meta": {
    "state": "SENT",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:35:00Z"
  }
}
```

**Status Codes:**
- 200: Found successfully
- 404: Mail entity not found
- 500: Internal server error

#### 3. Get All Mails
**GET** `/api/mail`

Retrieves all mail entities with optional filtering.

**Query Parameters:**
- `state` (optional): Filter by entity state (INITIAL, PENDING, HAPPY_PROCESSING, GLOOMY_PROCESSING, SENT, FAILED)
- `isHappy` (optional): Filter by mail type (true/false)

**Example Request:**
```
GET /api/mail?state=SENT&isHappy=true
```

**Response:**
```json
[
  {
    "uuid": "123e4567-e89b-12d3-a456-426614174000",
    "entity": {
      "isHappy": true,
      "mailList": ["user1@example.com"]
    },
    "meta": {
      "state": "SENT",
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:35:00Z"
    }
  }
]
```

**Status Codes:**
- 200: Retrieved successfully (empty array if no matches)
- 400: Invalid query parameters
- 500: Internal server error

#### 4. Update Mail
**PUT** `/api/mail/{id}`

Updates a mail entity. Can optionally trigger a workflow transition.

**Path Parameters:**
- `id` (UUID): The unique identifier of the mail entity

**Query Parameters:**
- `transition` (optional): Name of the workflow transition to trigger
  - Valid values: `null` (no transition), `"retry"` (for FAILED → PENDING transition)

**Request Body:**
```json
{
  "isHappy": false,
  "mailList": [
    "user1@example.com",
    "user4@example.com"
  ]
}
```

**Example Request with Transition:**
```
PUT /api/mail/123e4567-e89b-12d3-a456-426614174000?transition=retry
```

**Response:**
```json
{
  "uuid": "123e4567-e89b-12d3-a456-426614174000",
  "entity": {
    "isHappy": false,
    "mailList": [
      "user1@example.com",
      "user4@example.com"
    ]
  },
  "meta": {
    "state": "PENDING",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T11:00:00Z"
  }
}
```

**Status Codes:**
- 200: Updated successfully
- 400: Invalid request body or invalid transition
- 404: Mail entity not found
- 409: Transition not allowed from current state
- 500: Internal server error

#### 5. Delete Mail
**DELETE** `/api/mail/{id}`

Deletes a mail entity.

**Path Parameters:**
- `id` (UUID): The unique identifier of the mail entity

**Response:**
```json
{
  "message": "Mail entity deleted successfully",
  "uuid": "123e4567-e89b-12d3-a456-426614174000"
}
```

**Status Codes:**
- 200: Deleted successfully
- 404: Mail entity not found
- 409: Cannot delete entity in current state
- 500: Internal server error

### Workflow Transition Details

#### Available Transitions
- **retry**: Manual transition from FAILED state back to PENDING state
  - Only available when current state is FAILED
  - Allows reprocessing of failed mail entities

#### Transition Validation
- Transitions are validated against the current entity state
- Invalid transitions return 409 Conflict status
- Only manual transitions can be triggered via API

### Validation Rules

#### Create/Update Validation
1. **isHappy**: Must be boolean (true or false), cannot be null
2. **mailList**: 
   - Must not be null or empty
   - Each email must be valid email format
   - Maximum 100 recipients per mail

#### State-Based Restrictions
- Entities in HAPPY_PROCESSING or GLOOMY_PROCESSING states cannot be updated
- Entities in SENT state can only be deleted, not updated
- Only FAILED entities can use the "retry" transition

### Error Response Format
```json
{
  "error": "Validation Error",
  "message": "Invalid email format in mailList",
  "timestamp": "2024-01-15T10:30:00Z",
  "path": "/api/mail"
}
```

### Security Considerations
- All endpoints require appropriate authentication
- Input validation to prevent injection attacks
- Rate limiting for mail creation to prevent spam
- Email address validation to ensure proper format
