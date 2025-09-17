# UploadBatch Controllers

## Overview
The UploadBatchController provides REST API endpoints for managing bulk upload operations of HN items. It handles file uploads, tracks processing progress, and provides batch management capabilities.

## Controller: UploadBatchController

### Base Path: `/api/v1/upload-batches`

## Endpoints

### 1. Create Upload Batch
**Endpoint**: `POST /api/v1/upload-batches`
**Description**: Upload JSON file containing HN items for batch processing
**Method**: POST (multipart/form-data)
**Transition**: `auto_upload`

#### Request
```
Content-Type: multipart/form-data

file: [JSON file containing array of HN items]
uploadedBy: "user123"
```

#### Response
```json
{
  "success": true,
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "entity": {
      "batchId": "batch-12345",
      "fileName": "hn_items.json",
      "fileSize": 1048576,
      "fileMd5Hash": "d41d8cd98f00b204e9800998ecf8427e",
      "contentType": "application/json",
      "totalItemsInFile": 500,
      "itemsProcessed": 0,
      "itemsSkipped": 0,
      "itemsErrored": 0,
      "uploadedAt": "2024-01-15T10:30:00Z",
      "uploadedBy": "user123"
    },
    "meta": {
      "state": "uploaded",
      "version": 1,
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:30:00Z"
    }
  }
}
```

### 2. Get Upload Batch by ID
**Endpoint**: `GET /api/v1/upload-batches/{uuid}`
**Description**: Retrieve upload batch details by UUID
**Method**: GET

#### Response
```json
{
  "success": true,
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "entity": {
      "batchId": "batch-12345",
      "fileName": "hn_items.json",
      "fileSize": 1048576,
      "totalItemsInFile": 500,
      "itemsProcessed": 450,
      "itemsSkipped": 25,
      "itemsErrored": 25,
      "uploadedAt": "2024-01-15T10:30:00Z",
      "processingStartedAt": "2024-01-15T10:31:00Z",
      "processingCompletedAt": "2024-01-15T10:45:00Z",
      "uploadedBy": "user123",
      "processingNode": "node-1"
    },
    "meta": {
      "state": "partially_completed",
      "version": 3,
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:45:00Z"
    }
  }
}
```

### 3. Get Upload Batch Progress
**Endpoint**: `GET /api/v1/upload-batches/{uuid}/progress`
**Description**: Get real-time processing progress
**Method**: GET

#### Response
```json
{
  "success": true,
  "data": {
    "batchId": "batch-12345",
    "status": "processing",
    "progress": {
      "totalItems": 500,
      "processed": 350,
      "skipped": 20,
      "errored": 15,
      "remaining": 115,
      "percentComplete": 77.0
    },
    "timing": {
      "startedAt": "2024-01-15T10:31:00Z",
      "elapsedSeconds": 840,
      "estimatedCompletionAt": "2024-01-15T10:50:00Z",
      "processingRate": 0.42
    },
    "errors": {
      "hasErrors": true,
      "errorCount": 15,
      "lastError": "Invalid HN ID format for item at index 245"
    }
  }
}
```

### 4. List Upload Batches
**Endpoint**: `GET /api/v1/upload-batches`
**Description**: List upload batches with pagination and filters
**Method**: GET

#### Request Parameters
```
state=completed,processing
uploadedBy=user123
fromDate=2024-01-15T00:00:00Z
toDate=2024-01-15T23:59:59Z
limit=20
offset=0
sortBy=uploadedAt
sortOrder=DESC
```

#### Response
```json
{
  "success": true,
  "data": {
    "batches": [
      {
        "uuid": "550e8400-e29b-41d4-a716-446655440000",
        "batchId": "batch-12345",
        "fileName": "hn_items.json",
        "totalItemsInFile": 500,
        "itemsProcessed": 450,
        "state": "partially_completed",
        "uploadedAt": "2024-01-15T10:30:00Z",
        "uploadedBy": "user123"
      }
    ],
    "pagination": {
      "total": 1,
      "limit": 20,
      "offset": 0,
      "hasMore": false
    }
  }
}
```

### 5. Get Batch Items
**Endpoint**: `GET /api/v1/upload-batches/{uuid}/items`
**Description**: Get HN items created from this batch
**Method**: GET

#### Request Parameters
```
state=completed,failed
limit=50
offset=0
```

#### Response
```json
{
  "success": true,
  "data": {
    "batchId": "batch-12345",
    "items": [
      {
        "uuid": "550e8400-e29b-41d4-a716-446655440001",
        "hnId": 8863,
        "type": "story",
        "title": "My YC app: Dropbox",
        "state": "completed",
        "processingOrder": 1
      },
      {
        "uuid": "550e8400-e29b-41d4-a716-446655440002",
        "hnId": 8864,
        "type": "comment",
        "state": "failed",
        "processingOrder": 2,
        "errorMessage": "Invalid parent ID"
      }
    ],
    "summary": {
      "totalItems": 500,
      "completed": 450,
      "failed": 25,
      "processing": 25
    },
    "pagination": {
      "total": 500,
      "limit": 50,
      "offset": 0,
      "hasMore": true
    }
  }
}
```

### 6. Get Batch Errors
**Endpoint**: `GET /api/v1/upload-batches/{uuid}/errors`
**Description**: Get detailed error information for failed items
**Method**: GET

#### Response
```json
{
  "success": true,
  "data": {
    "batchId": "batch-12345",
    "errorSummary": "25 items failed processing due to validation errors",
    "errors": [
      {
        "itemIndex": 245,
        "hnId": null,
        "errorType": "VALIDATION",
        "errorMessage": "Invalid HN ID format: 'abc123'",
        "occurredAt": "2024-01-15T10:35:00Z"
      },
      {
        "itemIndex": 312,
        "hnId": 8999,
        "errorType": "DUPLICATE",
        "errorMessage": "Item with HN ID 8999 already exists",
        "occurredAt": "2024-01-15T10:38:00Z"
      }
    ],
    "errorStats": {
      "validation": 15,
      "duplicate": 8,
      "processing": 2
    }
  }
}
```

### 7. Retry Failed Items
**Endpoint**: `POST /api/v1/upload-batches/{uuid}/retry`
**Description**: Retry processing failed items in a batch
**Method**: POST
**Transition**: `retry_failed_items`

#### Request
```json
{
  "retryAll": true,
  "specificItems": []
}
```

#### Response
```json
{
  "success": true,
  "data": {
    "batchId": "batch-12345",
    "retryInitiated": true,
    "itemsToRetry": 25,
    "previousState": "partially_completed",
    "newState": "processing",
    "retryStartedAt": "2024-01-15T11:00:00Z"
  }
}
```

### 8. Retry Entire Batch
**Endpoint**: `POST /api/v1/upload-batches/{uuid}/retry-batch`
**Description**: Retry entire batch processing from the beginning
**Method**: POST
**Transition**: `retry_batch`

#### Request
```json
{
  "clearPreviousResults": true
}
```

#### Response
```json
{
  "success": true,
  "data": {
    "batchId": "batch-12345",
    "batchRetryInitiated": true,
    "previousState": "failed",
    "newState": "uploaded",
    "retryStartedAt": "2024-01-15T11:00:00Z",
    "retryCount": 2
  }
}
```

### 9. Cancel Batch Processing
**Endpoint**: `POST /api/v1/upload-batches/{uuid}/cancel`
**Description**: Cancel ongoing batch processing
**Method**: POST
**Transition**: `mark_failed`

#### Response
```json
{
  "success": true,
  "data": {
    "batchId": "batch-12345",
    "cancelled": true,
    "previousState": "processing",
    "newState": "failed",
    "cancelledAt": "2024-01-15T11:00:00Z",
    "itemsProcessedBeforeCancel": 350
  }
}
```

### 10. Download Batch Results
**Endpoint**: `GET /api/v1/upload-batches/{uuid}/download`
**Description**: Download processing results as JSON file
**Method**: GET

#### Request Parameters
```
format=json  // or "csv"
includeErrors=true
includeSuccessful=true
```

#### Response
```
Content-Type: application/json
Content-Disposition: attachment; filename="batch-12345-results.json"

{
  "batchInfo": {
    "batchId": "batch-12345",
    "fileName": "hn_items.json",
    "processedAt": "2024-01-15T10:45:00Z"
  },
  "summary": {
    "totalItems": 500,
    "successful": 450,
    "failed": 25,
    "skipped": 25
  },
  "results": [
    {
      "itemIndex": 1,
      "hnId": 8863,
      "status": "successful",
      "uuid": "550e8400-e29b-41d4-a716-446655440001"
    },
    {
      "itemIndex": 245,
      "hnId": null,
      "status": "failed",
      "error": "Invalid HN ID format"
    }
  ]
}
```

## Error Responses

### File Upload Error
```json
{
  "success": false,
  "error": {
    "code": "UPLOAD_ERROR",
    "message": "File upload failed",
    "details": {
      "reason": "File size exceeds maximum limit of 10MB",
      "maxSize": 10485760,
      "actualSize": 15728640
    }
  }
}
```

### Invalid File Format Error
```json
{
  "success": false,
  "error": {
    "code": "INVALID_FILE_FORMAT",
    "message": "File is not valid JSON",
    "details": {
      "fileName": "invalid_file.txt",
      "contentType": "text/plain",
      "expectedType": "application/json"
    }
  }
}
```

### Batch Not Found Error
```json
{
  "success": false,
  "error": {
    "code": "BATCH_NOT_FOUND",
    "message": "Upload batch not found",
    "details": {
      "uuid": "550e8400-e29b-41d4-a716-446655440000"
    }
  }
}
```
