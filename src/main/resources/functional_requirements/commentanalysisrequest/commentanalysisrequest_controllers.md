# CommentAnalysisRequest Controllers

## CommentAnalysisRequestController

### POST /api/comment-analysis/requests
Create a new comment analysis request.

**Request Body:**
```json
{
  "postId": 1,
  "emailAddress": "user@example.com"
}
```

**Response:**
```json
{
  "entity": {
    "postId": 1,
    "emailAddress": "user@example.com",
    "requestedAt": "2024-01-15T10:30:00"
  },
  "meta": {
    "uuid": "123e4567-e89b-12d3-a456-426614174000",
    "state": "pending",
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T10:30:00"
  }
}
```

### POST /api/comment-analysis/requests/{uuid}/transitions
Trigger a workflow transition.

**Request Body:**
```json
{
  "transitionName": "start_analysis"
}
```

**Response:**
```json
{
  "entity": {
    "postId": 1,
    "emailAddress": "user@example.com",
    "requestedAt": "2024-01-15T10:30:00"
  },
  "meta": {
    "uuid": "123e4567-e89b-12d3-a456-426614174000",
    "state": "processing",
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T10:30:15"
  }
}
```

### GET /api/comment-analysis/requests/{uuid}
Get request status.

**Response:**
```json
{
  "entity": {
    "postId": 1,
    "emailAddress": "user@example.com",
    "requestedAt": "2024-01-15T10:30:00"
  },
  "meta": {
    "uuid": "123e4567-e89b-12d3-a456-426614174000",
    "state": "completed",
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T10:32:00"
  }
}
```
