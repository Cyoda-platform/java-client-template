# BulkUpload Controller Specification

## Overview
The BulkUploadController provides REST API endpoints for managing bulk upload operations of HN items from JSON files.

## Controller Details
- **Controller Name**: BulkUploadController
- **Base Path**: `/api/bulkupload`
- **Package**: `com.java_template.application.controller`

## Endpoints

### 1. Upload JSON File
**POST** `/api/bulkupload`

Uploads a JSON file containing HN items for bulk processing.

**Request:** Multipart form data
- `file`: JSON file containing array of HN items
- `uploadedBy` (optional): User identifier

**Request Example:**
```
Content-Type: multipart/form-data
file: hn_items.json (containing array of HN items)
uploadedBy: admin_user
```

**Response:**
```json
{
  "entity": {
    "uploadId": "upload-550e8400-e29b-41d4-a716-446655440000",
    "fileName": "hn_items.json",
    "fileSize": 1024000,
    "uploadedAt": "2025-09-17T10:30:00",
    "uploadedBy": "admin_user",
    "totalItems": 0,
    "processedItems": 0,
    "failedItems": 0,
    "errorMessages": [],
    "createdAt": "2025-09-17T10:30:00",
    "updatedAt": "2025-09-17T10:30:00"
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "uploaded",
    "version": 1,
    "createdAt": "2025-09-17T10:30:00",
    "updatedAt": "2025-09-17T10:30:00"
  }
}
```

### 2. Get Upload Status
**GET** `/api/bulkupload/{id}`

Retrieves the status of a bulk upload operation.

**Response:**
```json
{
  "entity": {
    "uploadId": "upload-550e8400-e29b-41d4-a716-446655440000",
    "fileName": "hn_items.json",
    "fileSize": 1024000,
    "uploadedAt": "2025-09-17T10:30:00",
    "uploadedBy": "admin_user",
    "totalItems": 100,
    "processedItems": 85,
    "failedItems": 15,
    "errorMessages": [
      "Item 8863: Validation failed - missing required field 'type'",
      "Item 8864: Duplicate item ID already exists"
    ],
    "completedAt": "2025-09-17T10:35:00",
    "createdAt": "2025-09-17T10:30:00",
    "updatedAt": "2025-09-17T10:35:00"
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "completed_with_errors",
    "version": 1,
    "createdAt": "2025-09-17T10:30:00",
    "updatedAt": "2025-09-17T10:35:00"
  }
}
```

### 3. Get Upload by Upload ID
**GET** `/api/bulkupload/upload/{uploadId}`

Retrieves a bulk upload by its business identifier.

**Response:** Same structure as above

### 4. Start Processing
**POST** `/api/bulkupload/{id}/process?transition=start_processing`

Manually triggers processing of an uploaded file.

**Response:**
```json
{
  "entity": {
    "uploadId": "upload-550e8400-e29b-41d4-a716-446655440000",
    "fileName": "hn_items.json",
    "totalItems": 100,
    "processedItems": 0,
    "failedItems": 0,
    "updatedAt": "2025-09-17T10:31:00"
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "processing",
    "version": 2,
    "updatedAt": "2025-09-17T10:31:00"
  }
}
```

### 5. Retry Failed Upload
**POST** `/api/bulkupload/{id}/retry?transition=retry_upload`

Retries a failed bulk upload operation.

**Response:**
```json
{
  "entity": {
    "uploadId": "upload-550e8400-e29b-41d4-a716-446655440000",
    "fileName": "hn_items.json",
    "processedItems": 0,
    "failedItems": 0,
    "errorMessages": [],
    "completedAt": null,
    "updatedAt": "2025-09-17T10:40:00"
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "uploaded",
    "version": 3,
    "updatedAt": "2025-09-17T10:40:00"
  }
}
```

### 6. Reprocess Failed Items
**POST** `/api/bulkupload/{id}/reprocess?transition=reprocess_failed`

Reprocesses only the failed items from a completed upload with errors.

**Response:**
```json
{
  "entity": {
    "uploadId": "upload-550e8400-e29b-41d4-a716-446655440000",
    "fileName": "hn_items.json",
    "totalItems": 100,
    "processedItems": 85,
    "failedItems": 0,
    "errorMessages": [],
    "completedAt": null,
    "updatedAt": "2025-09-17T10:45:00"
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "processing",
    "version": 4,
    "updatedAt": "2025-09-17T10:45:00"
  }
}
```

### 7. Get All Uploads
**GET** `/api/bulkupload`

Gets all bulk upload operations (paginated).

**Query Parameters:**
- `status` (optional): Filter by state (uploaded, processing, completed, completed_with_errors, failed)
- `uploadedBy` (optional): Filter by uploader
- `limit` (optional): Maximum results (default: 50, max: 200)
- `offset` (optional): Pagination offset (default: 0)

**Response:**
```json
{
  "uploads": [
    {
      "entity": {
        "uploadId": "upload-550e8400-e29b-41d4-a716-446655440000",
        "fileName": "hn_items.json",
        "uploadedAt": "2025-09-17T10:30:00",
        "totalItems": 100,
        "processedItems": 100,
        "failedItems": 0,
        "completedAt": "2025-09-17T10:35:00"
      },
      "metadata": {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "state": "completed"
      }
    }
  ],
  "pagination": {
    "total": 1,
    "limit": 50,
    "offset": 0,
    "hasMore": false
  }
}
```

### 8. Delete Upload
**DELETE** `/api/bulkupload/{id}`

Deletes a bulk upload record (does not affect processed HN items).

**Response:** 204 No Content

### 9. Get Upload Statistics
**GET** `/api/bulkupload/stats`

Gets statistics about bulk upload operations.

**Query Parameters:**
- `from` (optional): Start date (ISO format)
- `to` (optional): End date (ISO format)

**Response:**
```json
{
  "totalUploads": 25,
  "completedUploads": 20,
  "failedUploads": 3,
  "uploadsWithErrors": 2,
  "totalItemsProcessed": 5000,
  "totalItemsFailed": 150,
  "averageProcessingTime": "00:02:30",
  "periodStart": "2025-09-01T00:00:00",
  "periodEnd": "2025-09-17T23:59:59"
}
```

### 10. Download Error Report
**GET** `/api/bulkupload/{id}/errors`

Downloads a detailed error report for a bulk upload.

**Response:**
```json
{
  "uploadId": "upload-550e8400-e29b-41d4-a716-446655440000",
  "fileName": "hn_items.json",
  "totalErrors": 15,
  "errors": [
    {
      "itemIndex": 5,
      "itemId": 8863,
      "error": "Validation failed: missing required field 'type'",
      "timestamp": "2025-09-17T10:32:15"
    },
    {
      "itemIndex": 12,
      "itemId": 8864,
      "error": "Duplicate item ID already exists",
      "timestamp": "2025-09-17T10:32:18"
    }
  ],
  "generatedAt": "2025-09-17T10:50:00"
}
```
