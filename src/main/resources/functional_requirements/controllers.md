# Controllers Requirements

## EggAlarmController

### Description
REST controller for managing egg alarm operations including creation, starting, cancellation, and status retrieval.

### Base Path
`/api/egg-alarms`

### Endpoints

#### 1. Create Egg Alarm
**POST** `/api/egg-alarms`

Creates a new egg alarm with the specified egg type.

**Request Body:**
```json
{
  "eggType": "SOFT_BOILED",
  "userId": "user123"
}
```

**Request Parameters:**
- `eggType` (required): String - One of "SOFT_BOILED", "MEDIUM_BOILED", "HARD_BOILED"
- `userId` (optional): String - User identifier for multi-user support

**Response Body:**
```json
{
  "entity": {
    "id": "alarm-uuid-123",
    "eggType": "SOFT_BOILED",
    "cookingTimeMinutes": 4,
    "createdAt": "2024-01-15T10:30:00",
    "startedAt": null,
    "completedAt": null,
    "userId": "user123"
  },
  "meta": {
    "uuid": "alarm-uuid-123",
    "state": "CREATED",
    "version": 1
  }
}
```

**Transition:** Automatic transition from INITIAL to CREATED via EggAlarmCreationProcessor

---

#### 2. Start Egg Alarm
**PUT** `/api/egg-alarms/{id}/start`

Starts an existing egg alarm timer.

**Path Parameters:**
- `id` (required): String - The alarm ID

**Request Body:**
```json
{
  "transitionName": "start"
}
```

**Response Body:**
```json
{
  "entity": {
    "id": "alarm-uuid-123",
    "eggType": "SOFT_BOILED",
    "cookingTimeMinutes": 4,
    "createdAt": "2024-01-15T10:30:00",
    "startedAt": "2024-01-15T10:35:00",
    "completedAt": null,
    "userId": "user123"
  },
  "meta": {
    "uuid": "alarm-uuid-123",
    "state": "ACTIVE",
    "version": 2
  }
}
```

**Transition:** Manual transition from CREATED to ACTIVE via EggAlarmStartProcessor

---

#### 3. Cancel Egg Alarm
**PUT** `/api/egg-alarms/{id}/cancel`

Cancels an egg alarm (works for both CREATED and ACTIVE states).

**Path Parameters:**
- `id` (required): String - The alarm ID

**Request Body:**
```json
{
  "transitionName": "cancel"
}
```

**Response Body:**
```json
{
  "entity": {
    "id": "alarm-uuid-123",
    "eggType": "SOFT_BOILED",
    "cookingTimeMinutes": 4,
    "createdAt": "2024-01-15T10:30:00",
    "startedAt": "2024-01-15T10:35:00",
    "completedAt": null,
    "userId": "user123"
  },
  "meta": {
    "uuid": "alarm-uuid-123",
    "state": "CANCELLED",
    "version": 3
  }
}
```

**Transition:** Manual transition from CREATED or ACTIVE to CANCELLED via EggAlarmCancellationProcessor

---

#### 4. Get Egg Alarm Status
**GET** `/api/egg-alarms/{id}`

Retrieves the current status of an egg alarm.

**Path Parameters:**
- `id` (required): String - The alarm ID

**Response Body:**
```json
{
  "entity": {
    "id": "alarm-uuid-123",
    "eggType": "SOFT_BOILED",
    "cookingTimeMinutes": 4,
    "createdAt": "2024-01-15T10:30:00",
    "startedAt": "2024-01-15T10:35:00",
    "completedAt": null,
    "userId": "user123"
  },
  "meta": {
    "uuid": "alarm-uuid-123",
    "state": "ACTIVE",
    "version": 2
  }
}
```

**Transition:** None (read-only operation)

---

#### 5. List User's Egg Alarms
**GET** `/api/egg-alarms`

Retrieves all egg alarms for a user (optional filtering).

**Query Parameters:**
- `userId` (optional): String - Filter by user ID
- `state` (optional): String - Filter by alarm state
- `limit` (optional): Integer - Maximum number of results (default: 50)
- `offset` (optional): Integer - Pagination offset (default: 0)

**Request Example:**
```
GET /api/egg-alarms?userId=user123&state=ACTIVE&limit=10&offset=0
```

**Response Body:**
```json
{
  "alarms": [
    {
      "entity": {
        "id": "alarm-uuid-123",
        "eggType": "SOFT_BOILED",
        "cookingTimeMinutes": 4,
        "createdAt": "2024-01-15T10:30:00",
        "startedAt": "2024-01-15T10:35:00",
        "completedAt": null,
        "userId": "user123"
      },
      "meta": {
        "uuid": "alarm-uuid-123",
        "state": "ACTIVE",
        "version": 2
      }
    }
  ],
  "totalCount": 1,
  "limit": 10,
  "offset": 0
}
```

**Transition:** None (read-only operation)

### Error Responses

**400 Bad Request:**
```json
{
  "error": "INVALID_EGG_TYPE",
  "message": "Egg type must be one of: SOFT_BOILED, MEDIUM_BOILED, HARD_BOILED"
}
```

**404 Not Found:**
```json
{
  "error": "ALARM_NOT_FOUND",
  "message": "Egg alarm with ID 'alarm-uuid-123' not found"
}
```

**409 Conflict:**
```json
{
  "error": "INVALID_STATE_TRANSITION",
  "message": "Cannot start alarm in COMPLETED state"
}
```
