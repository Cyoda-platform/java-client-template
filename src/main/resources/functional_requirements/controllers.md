# Controllers Requirements

## MailController

### Overview
REST controller for managing Mail entities, providing endpoints for creating, retrieving, updating, and sending emails.

### Base Path
`/api/mails`

### Endpoints

#### 1. Create Mail
**POST** `/api/mails`

Creates a new mail entity.

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
        "state": "PENDING",
        "createdAt": "2024-01-15T10:30:00Z",
        "updatedAt": "2024-01-15T10:30:00Z"
    }
}
```

**Status Codes:**
- 201 Created - Mail successfully created
- 400 Bad Request - Invalid input data
- 500 Internal Server Error - Server error

---

#### 2. Get Mail by ID
**GET** `/api/mails/{id}`

Retrieves a specific mail entity by its UUID.

**Path Parameters:**
- `id` (UUID) - The unique identifier of the mail

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
        "state": "HAPPY_READY",
        "createdAt": "2024-01-15T10:30:00Z",
        "updatedAt": "2024-01-15T10:35:00Z"
    }
}
```

**Status Codes:**
- 200 OK - Mail found and returned
- 404 Not Found - Mail not found
- 500 Internal Server Error - Server error

---

#### 3. Get All Mails
**GET** `/api/mails`

Retrieves all mail entities with optional filtering.

**Query Parameters:**
- `state` (optional) - Filter by mail state (PENDING, HAPPY_READY, GLOOMY_READY, SENT)
- `isHappy` (optional) - Filter by happy/gloomy type (true/false)

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
            "updatedAt": "2024-01-15T10:40:00Z"
        }
    }
]
```

---

#### 4. Update Mail
**PUT** `/api/mails/{id}`

Updates an existing mail entity.

**Path Parameters:**
- `id` (UUID) - The unique identifier of the mail

**Query Parameters:**
- `transitionName` (optional) - Name of the workflow transition to trigger

**Request Body:**
```json
{
    "isHappy": false,
    "mailList": [
        "updated@example.com",
        "another@example.com"
    ]
}
```

**Response:**
```json
{
    "uuid": "123e4567-e89b-12d3-a456-426614174000",
    "entity": {
        "isHappy": false,
        "mailList": [
            "updated@example.com",
            "another@example.com"
        ]
    },
    "meta": {
        "state": "GLOOMY_READY",
        "createdAt": "2024-01-15T10:30:00Z",
        "updatedAt": "2024-01-15T10:45:00Z"
    }
}
```

---

#### 5. Send Happy Mail
**POST** `/api/mails/{id}/send-happy`

Triggers the sending of a happy mail.

**Path Parameters:**
- `id` (UUID) - The unique identifier of the mail

**Query Parameters:**
- `transitionName` = `send_happy` (required)

**Request Body:** None

**Response:**
```json
{
    "uuid": "123e4567-e89b-12d3-a456-426614174000",
    "entity": {
        "isHappy": true,
        "mailList": ["user1@example.com"]
    },
    "meta": {
        "state": "SENT",
        "createdAt": "2024-01-15T10:30:00Z",
        "updatedAt": "2024-01-15T10:50:00Z"
    }
}
```

**Preconditions:**
- Mail must be in `HAPPY_READY` state
- Mail must have `isHappy = true`

---

#### 6. Send Gloomy Mail
**POST** `/api/mails/{id}/send-gloomy`

Triggers the sending of a gloomy mail.

**Path Parameters:**
- `id` (UUID) - The unique identifier of the mail

**Query Parameters:**
- `transitionName` = `send_gloomy` (required)

**Request Body:** None

**Response:**
```json
{
    "uuid": "123e4567-e89b-12d3-a456-426614174000",
    "entity": {
        "isHappy": false,
        "mailList": ["sad@example.com"]
    },
    "meta": {
        "state": "SENT",
        "createdAt": "2024-01-15T10:30:00Z",
        "updatedAt": "2024-01-15T10:55:00Z"
    }
}
```

**Preconditions:**
- Mail must be in `GLOOMY_READY` state
- Mail must have `isHappy = false`

---

#### 7. Delete Mail
**DELETE** `/api/mails/{id}`

Deletes a mail entity.

**Path Parameters:**
- `id` (UUID) - The unique identifier of the mail

**Response:** 204 No Content

**Status Codes:**
- 204 No Content - Mail successfully deleted
- 404 Not Found - Mail not found
- 500 Internal Server Error - Server error

### Error Responses
All endpoints return error responses in the following format:
```json
{
    "error": "Error message description",
    "timestamp": "2024-01-15T10:30:00Z",
    "path": "/api/mails/123"
}
```
