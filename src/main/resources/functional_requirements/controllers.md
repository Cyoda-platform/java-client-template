# Controller Requirements

## Overview
This document defines the REST API controllers for the London Houses Data Analysis application. Each entity has its own controller with CRUD operations and workflow transition endpoints.

## 1. DataSourceController

### Base Path: `/api/v1/datasources`

### Endpoints

#### GET /api/v1/datasources
**Purpose**: Get all data sources  
**Method**: GET  
**Response**:
```json
[
  {
    "id": "london_houses",
    "name": "London Houses Dataset",
    "url": "https://raw.githubusercontent.com/Cyoda-platform/cyoda-ai/refs/heads/ai-2.x/data/test-inputs/v1/connections/london_houses.csv",
    "description": "London housing market data",
    "isActive": true,
    "lastDownloadTime": "2024-01-15T10:30:00Z",
    "downloadIntervalHours": 24,
    "fileFormat": "CSV",
    "expectedColumns": ["price", "bedrooms", "bathrooms", "sqft_living", "location"],
    "state": "active"
  }
]
```

#### GET /api/v1/datasources/{id}
**Purpose**: Get specific data source by ID  
**Method**: GET  
**Path Parameters**: `id` (String) - Data source identifier  
**Response**: Single DataSource object (same structure as above)

#### POST /api/v1/datasources
**Purpose**: Create new data source  
**Method**: POST  
**Request Body**:
```json
{
  "id": "london_houses",
  "name": "London Houses Dataset",
  "url": "https://raw.githubusercontent.com/Cyoda-platform/cyoda-ai/refs/heads/ai-2.x/data/test-inputs/v1/connections/london_houses.csv",
  "description": "London housing market data",
  "isActive": true,
  "downloadIntervalHours": 24,
  "fileFormat": "CSV",
  "expectedColumns": ["price", "bedrooms", "bathrooms", "sqft_living", "location"]
}
```
**Response**: Created DataSource object with generated metadata

#### PUT /api/v1/datasources/{id}
**Purpose**: Update data source with optional state transition  
**Method**: PUT  
**Path Parameters**: `id` (String) - Data source identifier  
**Query Parameters**: `transition` (String, optional) - Transition name  
**Request Body**: Complete DataSource object  
**Response**: Updated DataSource object

**Example with transition**:
```
PUT /api/v1/datasources/london_houses?transition=start_download
```

#### DELETE /api/v1/datasources/{id}
**Purpose**: Delete data source  
**Method**: DELETE  
**Path Parameters**: `id` (String) - Data source identifier  
**Response**: 204 No Content

## 2. AnalysisJobController

### Base Path: `/api/v1/analysis-jobs`

### Endpoints

#### GET /api/v1/analysis-jobs
**Purpose**: Get all analysis jobs  
**Method**: GET  
**Response**:
```json
[
  {
    "id": "job_001",
    "dataSourceId": "london_houses",
    "jobName": "London Houses Statistical Analysis",
    "analysisType": "statistical_summary",
    "parameters": {
      "includeCharts": true,
      "aggregationLevel": "monthly"
    },
    "startTime": "2024-01-15T11:00:00Z",
    "endTime": "2024-01-15T11:05:00Z",
    "errorMessage": null,
    "resultData": "{\"mean_price\": 450000, \"median_price\": 420000}",
    "dataRowsProcessed": 1500,
    "state": "completed"
  }
]
```

#### POST /api/v1/analysis-jobs
**Purpose**: Create new analysis job  
**Method**: POST  
**Request Body**:
```json
{
  "dataSourceId": "london_houses",
  "jobName": "London Houses Statistical Analysis",
  "analysisType": "statistical_summary",
  "parameters": {
    "includeCharts": true,
    "aggregationLevel": "monthly"
  }
}
```
**Response**: Created AnalysisJob object

#### PUT /api/v1/analysis-jobs/{id}
**Purpose**: Update analysis job with optional state transition  
**Method**: PUT  
**Path Parameters**: `id` (String) - Analysis job identifier  
**Query Parameters**: `transition` (String, optional) - Transition name (e.g., "retry_analysis")  
**Request Body**: Complete AnalysisJob object  
**Response**: Updated AnalysisJob object

## 3. ReportController

### Base Path: `/api/v1/reports`

### Endpoints

#### GET /api/v1/reports
**Purpose**: Get all reports  
**Method**: GET  
**Response**:
```json
[
  {
    "id": "report_001",
    "analysisJobId": "job_001",
    "reportTitle": "London Houses Analysis Report - January 2024",
    "reportType": "summary",
    "content": "<html><body><h1>Analysis Report</h1>...</body></html>",
    "generatedTime": "2024-01-15T11:10:00Z",
    "format": "HTML",
    "summary": "Average house price increased by 5% compared to last month",
    "attachments": [],
    "state": "sent"
  }
]
```

#### GET /api/v1/reports/{id}
**Purpose**: Get specific report by ID  
**Method**: GET  
**Path Parameters**: `id` (String) - Report identifier  
**Response**: Single Report object

#### GET /api/v1/reports/{id}/content
**Purpose**: Get report content for viewing  
**Method**: GET  
**Path Parameters**: `id` (String) - Report identifier  
**Response**: HTML content (Content-Type: text/html)

#### POST /api/v1/reports
**Purpose**: Create new report (typically triggered by system)  
**Method**: POST  
**Request Body**:
```json
{
  "analysisJobId": "job_001",
  "reportTitle": "London Houses Analysis Report - January 2024",
  "reportType": "summary",
  "format": "HTML"
}
```
**Response**: Created Report object

#### PUT /api/v1/reports/{id}
**Purpose**: Update report with optional state transition  
**Method**: PUT  
**Path Parameters**: `id` (String) - Report identifier  
**Query Parameters**: `transition` (String, optional) - Transition name (e.g., "retry_sending")  
**Request Body**: Complete Report object  
**Response**: Updated Report object

## 4. SubscriberController

### Base Path: `/api/v1/subscribers`

### Endpoints

#### GET /api/v1/subscribers
**Purpose**: Get all subscribers  
**Method**: GET  
**Response**:
```json
[
  {
    "id": "sub_001",
    "email": "john.doe@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "subscriptionDate": "2024-01-10T09:00:00Z",
    "isActive": true,
    "preferences": {
      "reportTypes": ["summary", "detailed"],
      "frequency": "weekly"
    },
    "lastEmailSent": "2024-01-15T11:15:00Z",
    "emailDeliveryFailures": 0,
    "state": "active"
  }
]
```

#### POST /api/v1/subscribers
**Purpose**: Subscribe new user  
**Method**: POST  
**Request Body**:
```json
{
  "email": "jane.smith@example.com",
  "firstName": "Jane",
  "lastName": "Smith",
  "preferences": {
    "reportTypes": ["summary"],
    "frequency": "monthly"
  }
}
```
**Response**: Created Subscriber object

#### PUT /api/v1/subscribers/{id}
**Purpose**: Update subscriber with optional state transition  
**Method**: PUT  
**Path Parameters**: `id` (String) - Subscriber identifier  
**Query Parameters**: `transition` (String, optional) - Transition name (e.g., "unsubscribe", "resubscribe")  
**Request Body**: Complete Subscriber object  
**Response**: Updated Subscriber object

**Example unsubscribe**:
```
PUT /api/v1/subscribers/sub_001?transition=unsubscribe
```

#### DELETE /api/v1/subscribers/{id}
**Purpose**: Delete subscriber  
**Method**: DELETE  
**Path Parameters**: `id` (String) - Subscriber identifier  
**Response**: 204 No Content

## Common Response Patterns

### Success Response
All successful operations return the entity object with metadata:
```json
{
  "id": "entity_id",
  "...entity_fields...",
  "state": "current_state",
  "createdAt": "2024-01-15T10:00:00Z",
  "updatedAt": "2024-01-15T11:00:00Z"
}
```

### Error Response
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid email address format",
    "timestamp": "2024-01-15T11:00:00Z"
  }
}
```

### Validation Rules
- All IDs must be non-empty strings
- Email addresses must be valid format
- URLs must be valid HTTP/HTTPS
- Transition names must match workflow definitions
- Required fields cannot be null or empty

## Workflow Integration Notes

1. **Transition Parameters**: When using transition query parameters, ensure the current entity state supports the specified transition
2. **Automatic Transitions**: Some transitions are automatic and don't require manual triggering via API
3. **State Validation**: Controllers should validate that requested transitions are valid for the current entity state
4. **Error Handling**: Invalid transitions should return 400 Bad Request with appropriate error message
