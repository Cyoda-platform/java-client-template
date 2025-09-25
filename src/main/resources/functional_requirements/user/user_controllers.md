# User Controllers

## UserController

### Endpoints

#### POST /api/users/register
Register a new user account.

**Request Example:**
```json
{
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "organization": "Research Institute",
  "role": "EXTERNAL_SUBMITTER"
}
```

**Response Example:**
```json
{
  "entity": {
    "email": "john.doe@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "organization": "Research Institute",
    "role": "EXTERNAL_SUBMITTER",
    "isActive": false,
    "registrationDate": "2024-01-15T10:30:00Z"
  },
  "meta": {
    "uuid": "123e4567-e89b-12d3-a456-426614174000",
    "state": "registered",
    "version": 1
  }
}
```

#### PUT /api/users/{uuid}/activate
Activate user account (transition: activate_user).

**Request Example:**
```json
{
  "transitionName": "activate_user"
}
```

**Response Example:**
```json
{
  "entity": {
    "email": "john.doe@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "organization": "Research Institute",
    "role": "EXTERNAL_SUBMITTER",
    "isActive": true,
    "registrationDate": "2024-01-15T10:30:00Z"
  },
  "meta": {
    "uuid": "123e4567-e89b-12d3-a456-426614174000",
    "state": "active",
    "version": 2
  }
}
```

#### GET /api/users/{uuid}
Get user by UUID.

#### GET /api/users
List all users (admin only).
