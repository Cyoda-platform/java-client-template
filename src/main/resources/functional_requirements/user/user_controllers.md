# User Controller

## Overview
UserController provides REST API endpoints for managing user accounts and their lifecycle.

## Endpoints

### POST /api/users
Create a new user account.

**Request Example:**
```json
{
  "userId": "USER-001",
  "name": "John Doe",
  "email": "john.doe@example.com",
  "role": "DEVELOPER",
  "department": "Engineering"
}
```

**Response Example:**
```json
{
  "entity": {
    "userId": "USER-001",
    "name": "John Doe",
    "email": "john.doe@example.com",
    "role": "DEVELOPER",
    "department": "Engineering",
    "isActive": false,
    "createdAt": "2024-01-10T09:00:00",
    "lastLoginAt": null
  },
  "meta": {
    "uuid": "660e8400-e29b-41d4-a716-446655440001",
    "state": "registered",
    "version": 1
  }
}
```

### PUT /api/users/{userId}
Update a user with optional state transition.

**Request Example:**
```json
{
  "name": "John Smith",
  "department": "Platform Engineering",
  "transition": "activate_user"
}
```

**Response Example:**
```json
{
  "entity": {
    "userId": "USER-001",
    "name": "John Smith",
    "email": "john.doe@example.com",
    "role": "DEVELOPER",
    "department": "Platform Engineering",
    "isActive": true,
    "createdAt": "2024-01-10T09:00:00",
    "lastLoginAt": null
  },
  "meta": {
    "uuid": "660e8400-e29b-41d4-a716-446655440001",
    "state": "active",
    "version": 2
  }
}
```

### GET /api/users/{userId}
Get a specific user by ID.

**Response Example:**
```json
{
  "entity": {
    "userId": "USER-001",
    "name": "John Doe",
    "email": "john.doe@example.com",
    "role": "DEVELOPER",
    "department": "Engineering",
    "isActive": true,
    "createdAt": "2024-01-10T09:00:00",
    "lastLoginAt": "2024-01-10T14:30:00"
  },
  "meta": {
    "uuid": "660e8400-e29b-41d4-a716-446655440001",
    "state": "active",
    "version": 1
  }
}
```

### GET /api/users
List users with optional filtering.

**Query Parameters:**
- role: Filter by user role
- isActive: Filter by active status
- department: Filter by department

**Response Example:**
```json
[
  {
    "entity": {
      "userId": "USER-001",
      "name": "John Doe",
      "email": "john.doe@example.com",
      "role": "DEVELOPER",
      "isActive": true
    },
    "meta": {
      "uuid": "660e8400-e29b-41d4-a716-446655440001",
      "state": "active",
      "version": 1
    }
  }
]
```
