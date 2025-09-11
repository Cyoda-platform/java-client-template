# Controllers Requirements

## MailController

### Overview
REST controller for managing Mail entities. Provides endpoints for creating, reading, updating, and managing mail entities through their workflow lifecycle.

### Base Path
`/api/mails`

### Endpoints

#### 1. Create Mail
**POST** `/api/mails`

Creates a new mail entity and starts it in the workflow.

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
  "entity": {
    "isHappy": true,
    "mailList": [
      "user1@example.com",
      "user2@example.com", 
      "user3@example.com"
    ]
  },
  "meta": {
    "uuid": "123e4567-e89b-12d3-a456-426614174000",
    "state": "PENDING",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:00Z"
  }
}
```

#### 2. Get Mail by ID
**GET** `/api/mails/{id}`

Retrieves a specific mail entity by its UUID.

**Path Parameters:**
- `id` (UUID): The unique identifier of the mail entity

**Response:**
```json
{
  "entity": {
    "isHappy": false,
    "mailList": ["admin@example.com"]
  },
  "meta": {
    "uuid": "123e4567-e89b-12d3-a456-426614174000",
    "state": "SENT",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:35:00Z"
  }
}
```

#### 3. Get All Mails
**GET** `/api/mails`

Retrieves all mail entities with optional filtering.

**Query Parameters:**
- `state` (optional): Filter by entity state (PENDING, HAPPY_PROCESSING, GLOOMY_PROCESSING, SENT, FAILED)
- `isHappy` (optional): Filter by mail type (true/false)

**Response:**
```json
[
  {
    "entity": {
      "isHappy": true,
      "mailList": ["user1@example.com"]
    },
    "meta": {
      "uuid": "123e4567-e89b-12d3-a456-426614174000",
      "state": "SENT",
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:35:00Z"
    }
  }
]
```

#### 4. Update Mail
**PUT** `/api/mails/{id}`

Updates a mail entity. Can optionally trigger a workflow transition.

**Path Parameters:**
- `id` (UUID): The unique identifier of the mail entity

**Query Parameters:**
- `transitionName` (optional): Name of the workflow transition to trigger after update

**Request Body:**
```json
{
  "isHappy": false,
  "mailList": [
    "updated@example.com",
    "newuser@example.com"
  ]
}
```

**Response:**
```json
{
  "entity": {
    "isHappy": false,
    "mailList": [
      "updated@example.com",
      "newuser@example.com"
    ]
  },
  "meta": {
    "uuid": "123e4567-e89b-12d3-a456-426614174000",
    "state": "PENDING",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:40:00Z"
  }
}
```

#### 5. Retry Failed Mail
**POST** `/api/mails/{id}/retry`

Manually triggers retry for failed mail by transitioning from FAILED to PENDING state.

**Path Parameters:**
- `id` (UUID): The unique identifier of the mail entity

**Request Body:** None

**Response:**
```json
{
  "entity": {
    "isHappy": true,
    "mailList": ["retry@example.com"]
  },
  "meta": {
    "uuid": "123e4567-e89b-12d3-a456-426614174000",
    "state": "PENDING",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:45:00Z"
  }
}
```

#### 6. Delete Mail
**DELETE** `/api/mails/{id}`

Deletes a mail entity.

**Path Parameters:**
- `id` (UUID): The unique identifier of the mail entity

**Response:** 
```json
{
  "message": "Mail entity deleted successfully"
}
```

### Error Responses

**400 Bad Request:**
```json
{
  "error": "Validation failed",
  "message": "Mail list cannot be empty",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**404 Not Found:**
```json
{
  "error": "Entity not found",
  "message": "Mail with ID 123e4567-e89b-12d3-a456-426614174000 not found",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**500 Internal Server Error:**
```json
{
  "error": "Processing failed",
  "message": "Failed to send mail to recipient",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Workflow Integration Notes
- The retry endpoint uses the manual transition from FAILED to PENDING state
- Update operations can trigger workflow transitions when `transitionName` parameter is provided
- All state transitions are handled automatically by the workflow system based on the defined criteria and processors
