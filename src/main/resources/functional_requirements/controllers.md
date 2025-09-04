# Controller Specifications

## Overview
This document defines the REST API controllers for the book data analysis application. Each entity has its own controller with CRUD operations and workflow transition endpoints.

## 1. BookController

**Base Path**: `/api/books`  
**Purpose**: Manages book entities and their workflow transitions

### Endpoints

#### GET /api/books
**Description**: Retrieve all books  
**Method**: GET  
**Parameters**: None  
**Response**: List of books with metadata

**Response Example**:
```json
[
  {
    "data": {
      "bookId": 1,
      "title": "Complete Guide to Programming",
      "description": "A comprehensive guide to modern programming techniques",
      "pageCount": 450,
      "excerpt": "Programming is the art of creating solutions...",
      "publishDate": "2023-05-15T00:00:00",
      "retrievedAt": "2024-09-04T10:30:00",
      "analysisScore": 0.85,
      "reportId": null
    },
    "metadata": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "state": "analyzed",
      "createdAt": "2024-09-04T10:30:00",
      "updatedAt": "2024-09-04T10:35:00"
    }
  }
]
```

#### GET /api/books/{id}
**Description**: Retrieve book by technical UUID  
**Method**: GET  
**Parameters**: 
- `id` (path): Technical UUID of the book

**Response Example**:
```json
{
  "data": {
    "bookId": 1,
    "title": "Complete Guide to Programming",
    "description": "A comprehensive guide to modern programming techniques",
    "pageCount": 450,
    "excerpt": "Programming is the art of creating solutions...",
    "publishDate": "2023-05-15T00:00:00",
    "retrievedAt": "2024-09-04T10:30:00",
    "analysisScore": 0.85,
    "reportId": null
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "analyzed",
    "createdAt": "2024-09-04T10:30:00",
    "updatedAt": "2024-09-04T10:35:00"
  }
}
```

#### GET /api/books/business/{bookId}
**Description**: Retrieve book by business ID  
**Method**: GET  
**Parameters**: 
- `bookId` (path): Business ID from external API

#### POST /api/books
**Description**: Create new book entity (triggers data extraction)  
**Method**: POST  
**Transition**: Automatically triggers `extract_book_data`

**Request Example**:
```json
{
  "bookId": 1
}
```

**Response Example**:
```json
{
  "data": {
    "bookId": 1,
    "title": null,
    "description": null,
    "pageCount": null,
    "excerpt": null,
    "publishDate": null,
    "retrievedAt": null,
    "analysisScore": null,
    "reportId": null
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "none",
    "createdAt": "2024-09-04T10:30:00",
    "updatedAt": "2024-09-04T10:30:00"
  }
}
```

#### PUT /api/books/{id}
**Description**: Update book entity with optional transition  
**Method**: PUT  
**Parameters**: 
- `id` (path): Technical UUID
- `transition` (query, optional): Transition name

**Request Example**:
```json
{
  "bookId": 1,
  "title": "Updated Complete Guide to Programming",
  "description": "An updated comprehensive guide to modern programming techniques",
  "pageCount": 500,
  "excerpt": "Programming is the art of creating elegant solutions...",
  "publishDate": "2023-05-15T00:00:00",
  "retrievedAt": "2024-09-04T10:30:00",
  "analysisScore": 0.90,
  "reportId": null
}
```

#### PUT /api/books/{id}?transition=analyze_book_data
**Description**: Update book and trigger analysis transition  
**Transition**: `analyze_book_data` (extracted → analyzed)

#### DELETE /api/books/{id}
**Description**: Delete book by technical UUID  
**Method**: DELETE

## 2. ReportController

**Base Path**: `/api/reports`  
**Purpose**: Manages report entities and their workflow transitions

### Endpoints

#### GET /api/reports
**Description**: Retrieve all reports

#### GET /api/reports/{id}
**Description**: Retrieve report by technical UUID

#### GET /api/reports/business/{reportId}
**Description**: Retrieve report by business ID

#### POST /api/reports
**Description**: Create new report entity (triggers report generation)  
**Transition**: Automatically triggers `generate_report`

**Request Example**:
```json
{
  "reportId": "REPORT-2024-W36",
  "reportType": "WEEKLY_ANALYTICS",
  "reportPeriodStart": "2024-08-28T00:00:00",
  "reportPeriodEnd": "2024-09-04T23:59:59",
  "emailRecipients": "analytics-team@company.com,manager@company.com",
  "analyticsJobId": "550e8400-e29b-41d4-a716-446655440001"
}
```

**Response Example**:
```json
{
  "data": {
    "reportId": "REPORT-2024-W36",
    "reportType": "WEEKLY_ANALYTICS",
    "generatedAt": null,
    "reportPeriodStart": "2024-08-28T00:00:00",
    "reportPeriodEnd": "2024-09-04T23:59:59",
    "totalBooksAnalyzed": 0,
    "totalPageCount": 0,
    "averagePageCount": 0.0,
    "popularTitles": null,
    "publicationDateInsights": null,
    "reportSummary": null,
    "emailRecipients": "analytics-team@company.com,manager@company.com",
    "emailSentAt": null,
    "analyticsJobId": "550e8400-e29b-41d4-a716-446655440001"
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440002",
    "state": "none",
    "createdAt": "2024-09-04T10:30:00",
    "updatedAt": "2024-09-04T10:30:00"
  }
}
```

#### PUT /api/reports/{id}?transition=send_report_email
**Description**: Update report and trigger email sending  
**Transition**: `send_report_email` (generated → email_sending)

#### PUT /api/reports/{id}?transition=retry_email_sending
**Description**: Retry email sending for failed reports  
**Transition**: `retry_email_sending` (email_sending → email_sending) - Manual transition

## 3. AnalyticsJobController

**Base Path**: `/api/analytics-jobs`  
**Purpose**: Manages analytics job entities and their workflow transitions

### Endpoints

#### GET /api/analytics-jobs
**Description**: Retrieve all analytics jobs

#### GET /api/analytics-jobs/{id}
**Description**: Retrieve analytics job by technical UUID

#### GET /api/analytics-jobs/business/{jobId}
**Description**: Retrieve analytics job by business ID

#### POST /api/analytics-jobs
**Description**: Create new analytics job (triggers scheduling)  
**Transition**: Automatically triggers `schedule_job`

**Request Example**:
```json
{
  "jobType": "WEEKLY_DATA_EXTRACTION",
  "scheduledFor": "2024-09-11T09:00:00"
}
```

**Response Example**:
```json
{
  "data": {
    "jobId": null,
    "jobType": "WEEKLY_DATA_EXTRACTION",
    "scheduledFor": "2024-09-11T09:00:00",
    "startedAt": null,
    "completedAt": null,
    "booksProcessed": 0,
    "reportsGenerated": 0,
    "errorMessage": null,
    "nextJobId": null,
    "configurationData": null
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440003",
    "state": "none",
    "createdAt": "2024-09-04T10:30:00",
    "updatedAt": "2024-09-04T10:30:00"
  }
}
```

#### PUT /api/analytics-jobs/{id}?transition=start_job_execution
**Description**: Manually trigger job execution  
**Transition**: `start_job_execution` (scheduled → running)

#### PUT /api/analytics-jobs/{id}?transition=retry_failed_job
**Description**: Retry failed job  
**Transition**: `retry_failed_job` (failed → scheduled) - Manual transition

**Request Example**:
```json
{
  "jobId": "JOB-2024-W36-WED",
  "jobType": "WEEKLY_DATA_EXTRACTION",
  "scheduledFor": "2024-09-11T09:00:00",
  "startedAt": null,
  "completedAt": null,
  "booksProcessed": 0,
  "reportsGenerated": 0,
  "errorMessage": null,
  "nextJobId": "JOB-2024-W37-WED",
  "configurationData": "{\"apiUrl\":\"https://fakerestapi.azurewebsites.net/api/v1/Books\",\"emailRecipients\":\"analytics-team@company.com\",\"reportType\":\"WEEKLY_ANALYTICS\",\"maxRetries\":3}"
}
```

## Common Response Patterns

### Success Response Structure
All successful responses follow the EntityResponse pattern:
```json
{
  "data": { /* entity data */ },
  "metadata": {
    "id": "technical-uuid",
    "state": "current-workflow-state",
    "createdAt": "timestamp",
    "updatedAt": "timestamp"
  }
}
```

### Error Response Structure
```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable error message",
    "details": "Additional error details if available"
  }
}
```

### List Response Structure
```json
[
  { /* EntityResponse 1 */ },
  { /* EntityResponse 2 */ },
  /* ... */
]
```

## Transition Parameter Usage

### Automatic Transitions
- **POST** operations automatically trigger the first transition from `none` state
- No transition parameter needed for automatic transitions

### Manual Transitions
- Use `?transition=transition_name` query parameter
- Only available for manual transitions defined in workflows
- Current entity state must have the specified transition available

### Transition Validation
- Controllers validate that the requested transition exists in the workflow
- Controllers check that the current entity state allows the transition
- Invalid transitions return HTTP 400 Bad Request

## Error Handling

### HTTP Status Codes
- **200 OK**: Successful GET, PUT operations
- **201 Created**: Successful POST operations
- **400 Bad Request**: Invalid request data or transition
- **404 Not Found**: Entity not found
- **409 Conflict**: Transition not allowed from current state
- **500 Internal Server Error**: Server processing errors

### Workflow State Errors
- Attempting invalid transitions returns 409 Conflict
- Missing required fields for transitions returns 400 Bad Request
- Processor/criterion failures return 500 Internal Server Error with details
