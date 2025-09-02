# Controllers

## 1. CommentAnalysisRequestController

**Base Path**: `/api/comment-analysis-requests`

### Endpoints

#### POST /api/comment-analysis-requests
**Description**: Create a new comment analysis request  
**Transition**: `initialize_request`

**Request Body**:
```json
{
  "postId": 1,
  "recipientEmail": "user@example.com"
}
```

**Response**:
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "postId": 1,
  "recipientEmail": "user@example.com",
  "requestedAt": "2024-01-15T10:30:00Z",
  "state": "pending"
}
```

#### GET /api/comment-analysis-requests/{requestId}
**Description**: Get comment analysis request by ID  
**Transition**: null

**Response**:
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "postId": 1,
  "recipientEmail": "user@example.com",
  "requestedAt": "2024-01-15T10:30:00Z",
  "state": "analyzing"
}
```

#### GET /api/comment-analysis-requests
**Description**: Get all comment analysis requests  
**Transition**: null

**Response**:
```json
[
  {
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "postId": 1,
    "recipientEmail": "user@example.com",
    "requestedAt": "2024-01-15T10:30:00Z",
    "state": "completed"
  }
]
```

#### PUT /api/comment-analysis-requests/{requestId}
**Description**: Update comment analysis request  
**Transition**: Specified in request body

**Request Body**:
```json
{
  "recipientEmail": "newemail@example.com",
  "transitionName": null
}
```

**Response**:
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "postId": 1,
  "recipientEmail": "newemail@example.com",
  "requestedAt": "2024-01-15T10:30:00Z",
  "state": "pending"
}
```

#### POST /api/comment-analysis-requests/{requestId}/retry
**Description**: Retry a failed comment analysis request  
**Transition**: `initialize_request`

**Response**:
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "postId": 1,
  "recipientEmail": "user@example.com",
  "requestedAt": "2024-01-15T10:30:00Z",
  "state": "pending"
}
```

---

## 2. CommentController

**Base Path**: `/api/comments`

### Endpoints

#### GET /api/comments
**Description**: Get all comments  
**Transition**: null

**Query Parameters**:
- `requestId` (optional): Filter by request ID

**Response**:
```json
[
  {
    "commentId": 1,
    "postId": 1,
    "name": "id labore ex et quam laborum",
    "email": "Eliseo@gardner.biz",
    "body": "laudantium enim quasi est quidem magnam voluptate...",
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "fetchedAt": "2024-01-15T10:31:00Z",
    "state": "processed"
  }
]
```

#### GET /api/comments/{commentId}
**Description**: Get comment by ID  
**Transition**: null

**Response**:
```json
{
  "commentId": 1,
  "postId": 1,
  "name": "id labore ex et quam laborum",
  "email": "Eliseo@gardner.biz",
  "body": "laudantium enim quasi est quidem magnam voluptate...",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "fetchedAt": "2024-01-15T10:31:00Z",
  "state": "processed"
}
```

#### GET /api/comments/by-request/{requestId}
**Description**: Get all comments for a specific request  
**Transition**: null

**Response**:
```json
[
  {
    "commentId": 1,
    "postId": 1,
    "name": "id labore ex et quam laborum",
    "email": "Eliseo@gardner.biz",
    "body": "laudantium enim quasi est quidem magnam voluptate...",
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "fetchedAt": "2024-01-15T10:31:00Z",
    "state": "processed"
  }
]
```

---

## 3. CommentAnalysisController

**Base Path**: `/api/comment-analyses`

### Endpoints

#### GET /api/comment-analyses/{analysisId}
**Description**: Get comment analysis by ID  
**Transition**: null

**Response**:
```json
{
  "analysisId": "660e8400-e29b-41d4-a716-446655440001",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "totalComments": 5,
  "averageCommentLength": 156.4,
  "uniqueAuthors": 5,
  "topKeywords": "{\"voluptate\": 3, \"enim\": 2, \"quasi\": 2}",
  "sentimentSummary": "Positive: 2, Negative: 1, Neutral: 2",
  "analysisCompletedAt": "2024-01-15T10:32:00Z",
  "state": "completed"
}
```

#### GET /api/comment-analyses/by-request/{requestId}
**Description**: Get comment analysis by request ID  
**Transition**: null

**Response**:
```json
{
  "analysisId": "660e8400-e29b-41d4-a716-446655440001",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "totalComments": 5,
  "averageCommentLength": 156.4,
  "uniqueAuthors": 5,
  "topKeywords": "{\"voluptate\": 3, \"enim\": 2, \"quasi\": 2}",
  "sentimentSummary": "Positive: 2, Negative: 1, Neutral: 2",
  "analysisCompletedAt": "2024-01-15T10:32:00Z",
  "state": "completed"
}
```

#### GET /api/comment-analyses
**Description**: Get all comment analyses  
**Transition**: null

**Response**:
```json
[
  {
    "analysisId": "660e8400-e29b-41d4-a716-446655440001",
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "totalComments": 5,
    "averageCommentLength": 156.4,
    "uniqueAuthors": 5,
    "topKeywords": "{\"voluptate\": 3, \"enim\": 2, \"quasi\": 2}",
    "sentimentSummary": "Positive: 2, Negative: 1, Neutral: 2",
    "analysisCompletedAt": "2024-01-15T10:32:00Z",
    "state": "completed"
  }
]
```

---

## 4. EmailReportController

**Base Path**: `/api/email-reports`

### Endpoints

#### GET /api/email-reports/{reportId}
**Description**: Get email report by ID  
**Transition**: null

**Response**:
```json
{
  "reportId": "770e8400-e29b-41d4-a716-446655440002",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "recipientEmail": "user@example.com",
  "subject": "Comment Analysis Report for Post 1",
  "emailStatus": "SENT",
  "sentAt": "2024-01-15T10:33:00Z",
  "state": "sent"
}
```

#### GET /api/email-reports/by-request/{requestId}
**Description**: Get email report by request ID  
**Transition**: null

**Response**:
```json
{
  "reportId": "770e8400-e29b-41d4-a716-446655440002",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "recipientEmail": "user@example.com",
  "subject": "Comment Analysis Report for Post 1",
  "emailStatus": "SENT",
  "sentAt": "2024-01-15T10:33:00Z",
  "state": "sent"
}
```

#### GET /api/email-reports
**Description**: Get all email reports  
**Transition**: null

**Response**:
```json
[
  {
    "reportId": "770e8400-e29b-41d4-a716-446655440002",
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "recipientEmail": "user@example.com",
    "subject": "Comment Analysis Report for Post 1",
    "emailStatus": "SENT",
    "sentAt": "2024-01-15T10:33:00Z",
    "state": "sent"
  }
]
```

#### POST /api/email-reports/{reportId}/resend
**Description**: Resend a failed email report  
**Transition**: `send_email`

**Response**:
```json
{
  "reportId": "770e8400-e29b-41d4-a716-446655440002",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "recipientEmail": "user@example.com",
  "subject": "Comment Analysis Report for Post 1",
  "emailStatus": "SENDING",
  "state": "sending"
}
```

---

## Error Responses

All controllers return standard error responses:

```json
{
  "error": "ENTITY_NOT_FOUND",
  "message": "CommentAnalysisRequest with ID 550e8400-e29b-41d4-a716-446655440000 not found",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

## Notes

- All endpoints return appropriate HTTP status codes (200, 201, 404, 400, 500)
- Request validation is performed on all input parameters
- Entity states are included in responses for tracking workflow progress
- Transition names are used for state changes where applicable
- All timestamps are in ISO 8601 format
- Business IDs (requestId, analysisId, reportId) are used for external references
