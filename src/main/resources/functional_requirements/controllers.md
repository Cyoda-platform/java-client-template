# Controller Requirements

## Overview
This document defines the REST API requirements for controllers in the Comments Analysis Application. Each entity has its own controller with CRUD operations and workflow-specific endpoints.

## 1. CommentController

**Base Path**: `/api/comments`
**Purpose**: Manage individual comments from JSONPlaceholder API

### Endpoints

#### POST /api/comments/ingest/{postId}
**Purpose**: Ingest comments for a specific post from JSONPlaceholder API
**Method**: POST
**Transition**: Triggers `ingest_comment` transition (none → ingested)

**Request Example**:
```http
POST /api/comments/ingest/1
Content-Type: application/json

{
  "apiUrl": "https://jsonplaceholder.typicode.com/posts/1/comments"
}
```

**Response Example**:
```json
{
  "success": true,
  "message": "Successfully ingested 5 comments for post 1",
  "ingestedComments": [
    {
      "entity": {
        "commentId": "1",
        "postId": 1,
        "name": "id labore ex et quam laborum",
        "email": "Eliseo@gardner.biz",
        "body": "laudantium enim quasi est quidem magnam voluptate...",
        "ingestedAt": "2024-01-15T10:30:00",
        "wordCount": 25,
        "characterCount": 142
      },
      "metadata": {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "state": "ingested",
        "createdAt": "2024-01-15T10:30:00"
      }
    }
  ]
}
```

#### GET /api/comments/{id}
**Purpose**: Get comment by technical UUID
**Method**: GET

**Request Example**:
```http
GET /api/comments/550e8400-e29b-41d4-a716-446655440000
```

**Response Example**:
```json
{
  "entity": {
    "commentId": "1",
    "postId": 1,
    "name": "id labore ex et quam laborum",
    "email": "Eliseo@gardner.biz",
    "body": "laudantium enim quasi est quidem magnam voluptate...",
    "ingestedAt": "2024-01-15T10:30:00",
    "wordCount": 25,
    "characterCount": 142
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "ingested",
    "createdAt": "2024-01-15T10:30:00"
  }
}
```

#### GET /api/comments/post/{postId}
**Purpose**: Get all comments for a specific post
**Method**: GET

**Request Example**:
```http
GET /api/comments/post/1
```

**Response Example**:
```json
[
  {
    "entity": {
      "commentId": "1",
      "postId": 1,
      "name": "id labore ex et quam laborum",
      "email": "Eliseo@gardner.biz",
      "body": "laudantium enim quasi est quidem magnam voluptate...",
      "wordCount": 25,
      "characterCount": 142
    },
    "metadata": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "state": "analyzed"
    }
  }
]
```

#### PUT /api/comments/{id}
**Purpose**: Update comment with optional transition
**Method**: PUT
**Transition**: Optional transition parameter (e.g., `analyze_comment`)

**Request Example**:
```http
PUT /api/comments/550e8400-e29b-41d4-a716-446655440000?transition=analyze_comment
Content-Type: application/json

{
  "commentId": "1",
  "postId": 1,
  "name": "id labore ex et quam laborum",
  "email": "Eliseo@gardner.biz",
  "body": "laudantium enim quasi est quidem magnam voluptate...",
  "wordCount": 25,
  "characterCount": 142
}
```

## 2. CommentAnalysisController

**Base Path**: `/api/analysis`
**Purpose**: Manage comment analysis for posts

### Endpoints

#### POST /api/analysis/start/{postId}
**Purpose**: Start analysis for a specific post
**Method**: POST
**Transition**: Triggers `start_analysis` transition (none → collecting)

**Request Example**:
```http
POST /api/analysis/start/1
Content-Type: application/json

{
  "recipientEmail": "admin@example.com"
}
```

**Response Example**:
```json
{
  "entity": {
    "analysisId": "analysis-550e8400-e29b-41d4-a716-446655440001",
    "postId": 1,
    "totalComments": 0,
    "emailSent": false
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "state": "collecting",
    "createdAt": "2024-01-15T10:35:00"
  }
}
```

#### PUT /api/analysis/{id}/process
**Purpose**: Begin processing collected comments
**Method**: PUT
**Transition**: Triggers `begin_processing` transition (collecting → processing)

**Request Example**:
```http
PUT /api/analysis/550e8400-e29b-41d4-a716-446655440001/process?transition=begin_processing
```

**Response Example**:
```json
{
  "entity": {
    "analysisId": "analysis-550e8400-e29b-41d4-a716-446655440001",
    "postId": 1,
    "totalComments": 5,
    "averageWordCount": 23.4,
    "averageCharacterCount": 134.2,
    "mostActiveCommenter": "Eliseo@gardner.biz",
    "uniqueCommenters": 5,
    "longestComment": {
      "commentId": "3",
      "email": "Nikita@garfield.biz",
      "wordCount": 35,
      "characterCount": 198,
      "bodyPreview": "quia molestiae reprehenderit quasi aspernatur aut expedita occaecati aliquam eveniet laudantium..."
    },
    "shortestComment": {
      "commentId": "4",
      "email": "Lew@alysha.tv",
      "wordCount": 15,
      "characterCount": 89,
      "bodyPreview": "non et atque occaecati deserunt quas accusantium unde odit nobis qui voluptatem..."
    },
    "analysisCompletedAt": "2024-01-15T10:40:00"
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "state": "completed"
  }
}
```

#### PUT /api/analysis/{id}/report
**Purpose**: Generate email report
**Method**: PUT
**Transition**: Triggers `generate_report` transition (completed → reported)

**Request Example**:
```http
PUT /api/analysis/550e8400-e29b-41d4-a716-446655440001/report?transition=generate_report
```

**Response Example**:
```json
{
  "entity": {
    "analysisId": "analysis-550e8400-e29b-41d4-a716-446655440001",
    "postId": 1,
    "totalComments": 5,
    "emailSent": false
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "state": "reported"
  }
}
```

#### GET /api/analysis/{id}
**Purpose**: Get analysis by technical UUID
**Method**: GET

#### GET /api/analysis/post/{postId}
**Purpose**: Get analysis for specific post
**Method**: GET

## 3. EmailReportController

**Base Path**: `/api/email-reports`
**Purpose**: Manage email report generation and delivery

### Endpoints

#### GET /api/email-reports/{id}
**Purpose**: Get email report by technical UUID
**Method**: GET

**Request Example**:
```http
GET /api/email-reports/550e8400-e29b-41d4-a716-446655440002
```

**Response Example**:
```json
{
  "entity": {
    "reportId": "report-550e8400-e29b-41d4-a716-446655440002",
    "analysisId": "analysis-550e8400-e29b-41d4-a716-446655440001",
    "postId": 1,
    "recipientEmail": "admin@example.com",
    "subject": "Comment Analysis Report for Post 1",
    "sentAt": "2024-01-15T10:45:00",
    "deliveryStatus": "SENT",
    "retryCount": 0
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440002",
    "state": "sent"
  }
}
```

#### PUT /api/email-reports/{id}/send
**Purpose**: Send prepared email report
**Method**: PUT
**Transition**: Triggers `send_email` transition (prepared → sending)

**Request Example**:
```http
PUT /api/email-reports/550e8400-e29b-41d4-a716-446655440002/send?transition=send_email
```

#### PUT /api/email-reports/{id}/retry
**Purpose**: Retry failed email delivery
**Method**: PUT
**Transition**: Triggers `retry_email` transition (failed → retry)

**Request Example**:
```http
PUT /api/email-reports/550e8400-e29b-41d4-a716-446655440002/retry?transition=retry_email
```

#### GET /api/email-reports/analysis/{analysisId}
**Purpose**: Get email report by analysis ID
**Method**: GET

## API Integration Flow

### Complete Workflow Example:

1. **Ingest Comments**:
   ```http
   POST /api/comments/ingest/1
   ```

2. **Start Analysis**:
   ```http
   POST /api/analysis/start/1
   ```

3. **Process Analysis**:
   ```http
   PUT /api/analysis/{analysisId}/process?transition=begin_processing
   ```

4. **Generate Report**:
   ```http
   PUT /api/analysis/{analysisId}/report?transition=generate_report
   ```

5. **Send Email**:
   ```http
   PUT /api/email-reports/{reportId}/send?transition=send_email
   ```

### Error Responses:
All endpoints return standard HTTP status codes:
- `200 OK` - Success
- `400 Bad Request` - Invalid request data
- `404 Not Found` - Resource not found
- `500 Internal Server Error` - Server error

**Error Response Example**:
```json
{
  "error": "Invalid post ID",
  "message": "Post ID must be a positive integer",
  "timestamp": "2024-01-15T10:30:00"
}
```

### Notes:
- All transition parameters align with workflow definitions
- Request/response examples include complete entity data
- Controllers handle EntityWithMetadata pattern consistently
- Error handling follows REST best practices
- API supports the complete comment analysis workflow
