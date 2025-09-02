# Controllers

## MailController

### Description
REST API controller for managing Mail entities and their workflow transitions.

### Base Path
`/api/mails`

### Endpoints

#### 1. Create Mail
**POST** `/api/mails`

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
  "technicalId": "123e4567-e89b-12d3-a456-426614174000",
  "isHappy": true,
  "mailList": [
    "user1@example.com",
    "user2@example.com",
    "user3@example.com"
  ],
  "state": "pending",
  "creationDate": "2024-01-15T10:30:00Z"
}
```

**Status Codes:**
- 201 Created: Mail created successfully
- 400 Bad Request: Invalid request data

---

#### 2. Get Mail by ID
**GET** `/api/mails/{technicalId}`

Retrieves a specific mail entity by its technical ID.

**Path Parameters:**
- `technicalId` (UUID): The technical ID of the mail

**Response:**
```json
{
  "technicalId": "123e4567-e89b-12d3-a456-426614174000",
  "isHappy": true,
  "mailList": [
    "user1@example.com",
    "user2@example.com"
  ],
  "state": "happy_sent",
  "creationDate": "2024-01-15T10:30:00Z",
  "lastUpdateTime": "2024-01-15T10:31:00Z"
}
```

**Status Codes:**
- 200 OK: Mail found
- 404 Not Found: Mail not found

---

#### 3. Get All Mails
**GET** `/api/mails`

Retrieves all mail entities.

**Response:**
```json
[
  {
    "technicalId": "123e4567-e89b-12d3-a456-426614174000",
    "isHappy": true,
    "mailList": ["user1@example.com"],
    "state": "happy_sent",
    "creationDate": "2024-01-15T10:30:00Z"
  },
  {
    "technicalId": "456e7890-e89b-12d3-a456-426614174001",
    "isHappy": false,
    "mailList": ["user2@example.com"],
    "state": "gloomy_sent",
    "creationDate": "2024-01-15T11:00:00Z"
  }
]
```

**Status Codes:**
- 200 OK: Mails retrieved successfully

---

#### 4. Update Mail
**PUT** `/api/mails/{technicalId}`

Updates a mail entity. Can optionally trigger a workflow transition.

**Path Parameters:**
- `technicalId` (UUID): The technical ID of the mail

**Query Parameters:**
- `transitionName` (String, optional): Name of the workflow transition to trigger
  - `null`: Update without state transition
  - `"retry_sending"`: Retry failed mail sending (only valid from "failed" state)

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

**Response:**
```json
{
  "technicalId": "123e4567-e89b-12d3-a456-426614174000",
  "isHappy": false,
  "mailList": [
    "user1@example.com",
    "user4@example.com"
  ],
  "state": "pending",
  "lastUpdateTime": "2024-01-15T12:00:00Z"
}
```

**Status Codes:**
- 200 OK: Mail updated successfully
- 400 Bad Request: Invalid request data or invalid transition
- 404 Not Found: Mail not found

---

#### 5. Delete Mail
**DELETE** `/api/mails/{technicalId}`

Deletes a specific mail entity.

**Path Parameters:**
- `technicalId` (UUID): The technical ID of the mail

**Response:**
```json
{
  "technicalId": "123e4567-e89b-12d3-a456-426614174000",
  "deleted": true
}
```

**Status Codes:**
- 200 OK: Mail deleted successfully
- 404 Not Found: Mail not found

---

#### 6. Retry Failed Mail
**POST** `/api/mails/{technicalId}/retry`

Convenience endpoint to retry sending a failed mail.

**Path Parameters:**
- `technicalId` (UUID): The technical ID of the mail

**Response:**
```json
{
  "technicalId": "123e4567-e89b-12d3-a456-426614174000",
  "isHappy": true,
  "mailList": ["user1@example.com"],
  "state": "pending",
  "lastUpdateTime": "2024-01-15T13:00:00Z"
}
```

**Status Codes:**
- 200 OK: Retry initiated successfully
- 400 Bad Request: Mail is not in failed state
- 404 Not Found: Mail not found

---

## Workflow Transition Mapping

| Endpoint | Transition Name | Valid From State | Target State |
|----------|----------------|------------------|--------------|
| POST /api/mails | initialize_mail | none | pending |
| PUT /api/mails/{id}?transitionName=retry_sending | retry_sending | failed | pending |
| POST /api/mails/{id}/retry | retry_sending | failed | pending |

## Validation Rules

### Mail Entity Validation
- `isHappy`: Required, must be boolean
- `mailList`: Required, must be non-empty array of valid email addresses
- Email validation: Standard RFC 5322 email format

### Transition Validation
- `retry_sending`: Only allowed when current state is "failed"
- Invalid transitions return 400 Bad Request with error message

## Error Responses

**400 Bad Request:**
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid email address format",
  "details": ["Invalid email: invalid-email"]
}
```

**404 Not Found:**
```json
{
  "error": "NOT_FOUND",
  "message": "Mail not found",
  "technicalId": "123e4567-e89b-12d3-a456-426614174000"
}
```
