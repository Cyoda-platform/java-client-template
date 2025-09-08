# Controller Requirements

## Overview
This document defines the REST API controllers for the comments analysis application. Each entity has its own controller with appropriate CRUD operations and workflow transitions.

## 1. CommentAnalysisJobController

### Base Path: `/api/comment-analysis-jobs`

### Endpoints

#### POST /api/comment-analysis-jobs
**Purpose**: Create a new comment analysis job  
**Transition**: null (entity starts in PENDING state automatically)

**Request Body**:
```json
{
    "postId": 1,
    "recipientEmail": "user@example.com"
}
```

**Response Body**:
```json
{
    "entity": {
        "postId": 1,
        "recipientEmail": "user@example.com",
        "requestedAt": "2024-01-15T10:30:00Z",
        "completedAt": null,
        "totalComments": null,
        "errorMessage": null
    },
    "meta": {
        "uuid": "550e8400-e29b-41d4-a716-446655440000",
        "state": "PENDING",
        "version": 1,
        "createdAt": "2024-01-15T10:30:00Z",
        "updatedAt": "2024-01-15T10:30:00Z"
    }
}
```

#### GET /api/comment-analysis-jobs/{uuid}
**Purpose**: Get a specific comment analysis job by UUID

**Response Body**:
```json
{
    "entity": {
        "postId": 1,
        "recipientEmail": "user@example.com",
        "requestedAt": "2024-01-15T10:30:00Z",
        "completedAt": "2024-01-15T10:35:00Z",
        "totalComments": 5,
        "errorMessage": null
    },
    "meta": {
        "uuid": "550e8400-e29b-41d4-a716-446655440000",
        "state": "COMPLETED",
        "version": 3,
        "createdAt": "2024-01-15T10:30:00Z",
        "updatedAt": "2024-01-15T10:35:00Z"
    }
}
```

#### GET /api/comment-analysis-jobs
**Purpose**: List all comment analysis jobs with optional filtering

**Query Parameters**:
- `state` (optional): Filter by job state
- `postId` (optional): Filter by post ID
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 20)

**Response Body**:
```json
{
    "content": [
        {
            "entity": { /* job data */ },
            "meta": { /* metadata */ }
        }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "size": 20,
    "number": 0
}
```

#### PUT /api/comment-analysis-jobs/{uuid}
**Purpose**: Update a comment analysis job (mainly for retry operations)  
**Transition**: Varies based on current state and target state

**Request Body**:
```json
{
    "recipientEmail": "newemail@example.com",
    "transitionName": "RETRY_FROM_FAILED"
}
```

**Response Body**: Same as GET response

#### DELETE /api/comment-analysis-jobs/{uuid}
**Purpose**: Cancel/delete a comment analysis job  
**Transition**: null (soft delete or state change to CANCELLED)

## 2. CommentController

### Base Path: `/api/comments`

### Endpoints

#### GET /api/comments/{uuid}
**Purpose**: Get a specific comment by UUID

**Response Body**:
```json
{
    "entity": {
        "commentId": 1,
        "postId": 1,
        "name": "id labore ex et quam laborum",
        "email": "Eliseo@gardner.biz",
        "body": "laudantium enim quasi est quidem magnam voluptate...",
        "jobId": "550e8400-e29b-41d4-a716-446655440000",
        "ingestedAt": "2024-01-15T10:31:00Z",
        "wordCount": 25,
        "sentimentScore": 0.3
    },
    "meta": {
        "uuid": "660e8400-e29b-41d4-a716-446655440001",
        "state": "ANALYZED",
        "version": 2,
        "createdAt": "2024-01-15T10:31:00Z",
        "updatedAt": "2024-01-15T10:32:00Z"
    }
}
```

#### GET /api/comments
**Purpose**: List comments with filtering options

**Query Parameters**:
- `jobId` (optional): Filter by job UUID
- `postId` (optional): Filter by post ID
- `state` (optional): Filter by comment state
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 50)

**Response Body**:
```json
{
    "content": [
        {
            "entity": { /* comment data */ },
            "meta": { /* metadata */ }
        }
    ],
    "totalElements": 5,
    "totalPages": 1,
    "size": 50,
    "number": 0
}
```

#### PUT /api/comments/{uuid}
**Purpose**: Update a comment (mainly for reprocessing)  
**Transition**: "REANALYZE" (if moving from ANALYZED back to INGESTED for reprocessing)

**Request Body**:
```json
{
    "transitionName": "REANALYZE"
}
```

## 3. CommentAnalysisReportController

### Base Path: `/api/comment-analysis-reports`

### Endpoints

#### GET /api/comment-analysis-reports/{uuid}
**Purpose**: Get a specific analysis report by UUID

**Response Body**:
```json
{
    "entity": {
        "jobId": "550e8400-e29b-41d4-a716-446655440000",
        "postId": 1,
        "totalComments": 5,
        "averageWordCount": 28.4,
        "averageSentimentScore": 0.15,
        "positiveCommentsCount": 2,
        "negativeCommentsCount": 1,
        "neutralCommentsCount": 2,
        "topCommenters": "[{\"email\":\"user1@example.com\",\"count\":2}]",
        "commonKeywords": "[{\"word\":\"great\",\"count\":3},{\"word\":\"good\",\"count\":2}]",
        "generatedAt": "2024-01-15T10:34:00Z",
        "sentAt": "2024-01-15T10:35:00Z"
    },
    "meta": {
        "uuid": "770e8400-e29b-41d4-a716-446655440002",
        "state": "SENT",
        "version": 2,
        "createdAt": "2024-01-15T10:34:00Z",
        "updatedAt": "2024-01-15T10:35:00Z"
    }
}
```

#### GET /api/comment-analysis-reports/by-job/{jobUuid}
**Purpose**: Get analysis report by job UUID

**Response Body**: Same as above

#### GET /api/comment-analysis-reports
**Purpose**: List all analysis reports

**Query Parameters**:
- `jobId` (optional): Filter by job UUID
- `postId` (optional): Filter by post ID
- `state` (optional): Filter by report state
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 20)

## API Design Principles

### Request/Response Format
- All entities wrapped in `EntityWithMetadata<T>` format
- Consistent error handling with appropriate HTTP status codes
- Pagination for list endpoints
- Optional filtering parameters

### Transition Handling
- Update endpoints include optional `transitionName` parameter
- Transition names must match workflow definitions
- Null transition name means no state change
- Invalid transitions return 400 Bad Request

### Error Responses
```json
{
    "error": "INVALID_TRANSITION",
    "message": "Cannot transition from COMPLETED to PENDING",
    "timestamp": "2024-01-15T10:30:00Z",
    "path": "/api/comment-analysis-jobs/550e8400-e29b-41d4-a716-446655440000"
}
```

### Security Considerations
- Input validation for all request parameters
- Email format validation for recipient addresses
- Post ID validation (positive integers only)
- Rate limiting for job creation endpoints
