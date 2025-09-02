# Controllers

## MailController

### Description
REST API controller for managing Mail entities. Provides endpoints for creating, reading, updating, and deleting mail entities, with support for workflow transitions.

### Base Path
`/api/v1/mails`

### Endpoints

#### 1. Create Mail
- **Method**: `POST`
- **Path**: `/api/v1/mails`
- **Description**: Creates a new mail entity
- **Transition**: Automatically triggers `initialize_mail` transition (none → pending)

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
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "isHappy": true,
  "mailList": [
    "user1@example.com",
    "user2@example.com",
    "user3@example.com"
  ],
  "state": "pending",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

#### 2. Get Mail by ID
- **Method**: `GET`
- **Path**: `/api/v1/mails/{id}`
- **Description**: Retrieves a specific mail entity by ID

**Response:**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "isHappy": true,
  "mailList": [
    "user1@example.com",
    "user2@example.com"
  ],
  "state": "sent",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:35:00Z"
}
```

#### 3. Get All Mails
- **Method**: `GET`
- **Path**: `/api/v1/mails`
- **Description**: Retrieves all mail entities
- **Query Parameters**: 
  - `state` (optional): Filter by state
  - `isHappy` (optional): Filter by happy/gloomy type

**Response:**
```json
[
  {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "isHappy": true,
    "mailList": ["user1@example.com"],
    "state": "sent",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:35:00Z"
  },
  {
    "id": "987fcdeb-51a2-43d7-b123-987654321000",
    "isHappy": false,
    "mailList": ["user2@example.com"],
    "state": "processing_gloomy",
    "createdAt": "2024-01-15T11:00:00Z",
    "updatedAt": "2024-01-15T11:02:00Z"
  }
]
```

#### 4. Update Mail
- **Method**: `PUT`
- **Path**: `/api/v1/mails/{id}`
- **Description**: Updates a mail entity
- **Query Parameters**: 
  - `transition` (optional): Transition name to trigger after update

**Request Body:**
```json
{
  "isHappy": false,
  "mailList": [
    "user1@example.com",
    "user4@example.com"
  ],
  "transition": null
}
```

**Response:**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "isHappy": false,
  "mailList": [
    "user1@example.com",
    "user4@example.com"
  ],
  "state": "pending",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T11:15:00Z"
}
```

#### 5. Trigger Transition
- **Method**: `POST`
- **Path**: `/api/v1/mails/{id}/transitions/{transitionName}`
- **Description**: Manually triggers a workflow transition
- **Path Parameters**:
  - `id`: Mail entity ID
  - `transitionName`: Name of transition to trigger

**Available Transitions:**
- `retry_processing` (from failed → pending)

**Request Body:** (empty)
```json
{}
```

**Response:**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "isHappy": true,
  "mailList": ["user1@example.com"],
  "state": "pending",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T11:20:00Z"
}
```

#### 6. Delete Mail
- **Method**: `DELETE`
- **Path**: `/api/v1/mails/{id}`
- **Description**: Deletes a mail entity

**Response:**
```json
{
  "message": "Mail deleted successfully",
  "id": "123e4567-e89b-12d3-a456-426614174000"
}
```

#### 7. Get Mail Status
- **Method**: `GET`
- **Path**: `/api/v1/mails/{id}/status`
- **Description**: Gets the current workflow status of a mail

**Response:**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "currentState": "processing_happy",
  "availableTransitions": [
    "happy_mail_sent",
    "happy_mail_failed"
  ],
  "lastTransition": "process_happy_mail",
  "lastTransitionAt": "2024-01-15T10:32:00Z"
}
```

### Error Responses

#### 400 Bad Request
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid email address in mailList",
  "details": {
    "field": "mailList[1]",
    "value": "invalid-email"
  }
}
```

#### 404 Not Found
```json
{
  "error": "MAIL_NOT_FOUND",
  "message": "Mail with ID 123e4567-e89b-12d3-a456-426614174000 not found"
}
```

#### 409 Conflict
```json
{
  "error": "INVALID_TRANSITION",
  "message": "Cannot execute transition 'retry_processing' from current state 'sent'",
  "details": {
    "currentState": "sent",
    "requestedTransition": "retry_processing",
    "availableTransitions": []
  }
}
```

### Business Rules

1. **State Management**: Entity state is read-only in API responses
2. **Transition Validation**: Only valid transitions for current state are allowed
3. **Email Validation**: All email addresses in mailList must be valid
4. **Required Fields**: Both isHappy and mailList are required for creation
5. **Automatic Processing**: Creating a mail automatically starts workflow processing
6. **Manual Transitions**: Only specific transitions (like retry_processing) can be manually triggered

### Notes
- All timestamps are in ISO 8601 format (UTC)
- The `state` field in responses shows the current workflow state
- Transition parameter in update requests can be null if no state change is needed
- Only the `retry_processing` transition is available for manual triggering
- Automatic transitions (like `process_happy_mail`, `process_gloomy_mail`) are triggered by the workflow engine based on criteria evaluation
