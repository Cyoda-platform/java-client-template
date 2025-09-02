# Controller Requirements

## Overview
This document defines the REST API controllers for the Product Performance Analysis and Reporting System. Each entity has its own controller with CRUD operations and workflow transition endpoints.

## 1. ProductController

**Base Path**: `/api/v1/products`

### Endpoints

#### GET /api/v1/products
**Purpose**: Retrieve all products with optional filtering
**Method**: GET
**Parameters**: 
- `category` (optional): Filter by product category
- `status` (optional): Filter by performance status (high/medium/low)
- `underperforming` (optional): Filter underperforming products (true/false)
- `needsRestocking` (optional): Filter products needing restock (true/false)

**Request Example**:
```
GET /api/v1/products?category=dogs&underperforming=true
```

**Response Example**:
```json
{
  "status": "success",
  "data": [
    {
      "id": "123e4567-e89b-12d3-a456-426614174000",
      "petId": 1,
      "name": "Golden Retriever Puppy",
      "category": "dogs",
      "categoryId": 1,
      "photoUrls": ["https://example.com/photo1.jpg"],
      "tags": ["friendly", "puppy"],
      "price": 1200.00,
      "stockLevel": 5,
      "salesVolume": 15,
      "revenue": 18000.00,
      "lastSaleDate": "2024-08-25T10:30:00Z",
      "inventoryTurnoverRate": 3.0,
      "performanceScore": 0.25,
      "isUnderperforming": true,
      "needsRestocking": true,
      "extractionDate": "2024-09-02T08:00:00Z",
      "analysisDate": "2024-09-02T08:15:00Z",
      "state": "analyzed"
    }
  ],
  "pagination": {
    "page": 1,
    "size": 20,
    "total": 1
  }
}
```

#### GET /api/v1/products/{id}
**Purpose**: Retrieve specific product by ID
**Method**: GET
**Parameters**: 
- `id` (path): Product UUID

**Request Example**:
```
GET /api/v1/products/123e4567-e89b-12d3-a456-426614174000
```

**Response Example**:
```json
{
  "status": "success",
  "data": {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "petId": 1,
    "name": "Golden Retriever Puppy",
    "category": "dogs",
    "categoryId": 1,
    "photoUrls": ["https://example.com/photo1.jpg"],
    "tags": ["friendly", "puppy"],
    "price": 1200.00,
    "stockLevel": 5,
    "salesVolume": 15,
    "revenue": 18000.00,
    "lastSaleDate": "2024-08-25T10:30:00Z",
    "inventoryTurnoverRate": 3.0,
    "performanceScore": 0.25,
    "isUnderperforming": true,
    "needsRestocking": true,
    "extractionDate": "2024-09-02T08:00:00Z",
    "analysisDate": "2024-09-02T08:15:00Z",
    "state": "analyzed"
  }
}
```

#### POST /api/v1/products
**Purpose**: Create new product (manual entry)
**Method**: POST
**Parameters**: None

**Request Example**:
```json
{
  "petId": 2,
  "name": "Persian Cat",
  "category": "cats",
  "categoryId": 2,
  "photoUrls": ["https://example.com/cat1.jpg"],
  "tags": ["fluffy", "indoor"],
  "price": 800.00,
  "stockLevel": 8
}
```

**Response Example**:
```json
{
  "status": "success",
  "data": {
    "id": "456e7890-e89b-12d3-a456-426614174001",
    "petId": 2,
    "name": "Persian Cat",
    "category": "cats",
    "categoryId": 2,
    "photoUrls": ["https://example.com/cat1.jpg"],
    "tags": ["fluffy", "indoor"],
    "price": 800.00,
    "stockLevel": 8,
    "salesVolume": 0,
    "revenue": 0.00,
    "inventoryTurnoverRate": 0.0,
    "performanceScore": 0.0,
    "isUnderperforming": false,
    "needsRestocking": false,
    "extractionDate": "2024-09-02T10:00:00Z",
    "state": "extracted"
  }
}
```

#### PUT /api/v1/products/{id}
**Purpose**: Update product with optional workflow transition
**Method**: PUT
**Parameters**: 
- `id` (path): Product UUID
- `transitionName` (query, optional): Workflow transition name

**Request Example**:
```
PUT /api/v1/products/123e4567-e89b-12d3-a456-426614174000?transitionName=reanalyze_product

{
  "stockLevel": 15,
  "price": 1300.00
}
```

**Response Example**:
```json
{
  "status": "success",
  "data": {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "petId": 1,
    "name": "Golden Retriever Puppy",
    "stockLevel": 15,
    "price": 1300.00,
    "needsRestocking": false,
    "state": "analyzed"
  }
}
```

#### DELETE /api/v1/products/{id}
**Purpose**: Delete product
**Method**: DELETE
**Parameters**: 
- `id` (path): Product UUID

**Request Example**:
```
DELETE /api/v1/products/123e4567-e89b-12d3-a456-426614174000
```

**Response Example**:
```json
{
  "status": "success",
  "message": "Product deleted successfully"
}
```

## 2. DataExtractionController

**Base Path**: `/api/v1/extractions`

### Endpoints

#### GET /api/v1/extractions
**Purpose**: Retrieve all data extractions with filtering
**Method**: GET
**Parameters**: 
- `status` (optional): Filter by extraction status
- `type` (optional): Filter by extraction type
- `startDate` (optional): Filter extractions after date
- `endDate` (optional): Filter extractions before date

**Request Example**:
```
GET /api/v1/extractions?status=completed&startDate=2024-08-01
```

**Response Example**:
```json
{
  "status": "success",
  "data": [
    {
      "id": "789e0123-e89b-12d3-a456-426614174002",
      "extractionId": "EXT-20240902-001",
      "scheduledTime": "2024-09-02T08:00:00Z",
      "startTime": "2024-09-02T08:00:05Z",
      "endTime": "2024-09-02T08:05:30Z",
      "extractionType": "SCHEDULED",
      "totalProductsExtracted": 25,
      "totalInventoryRecords": 25,
      "extractionFormat": "JSON",
      "apiEndpoint": "https://petstore.swagger.io/v2",
      "dataQualityScore": 0.95,
      "retryCount": 0,
      "state": "completed"
    }
  ]
}
```

#### POST /api/v1/extractions
**Purpose**: Create manual data extraction
**Method**: POST

**Request Example**:
```json
{
  "extractionType": "MANUAL",
  "extractionFormat": "JSON",
  "scheduledTime": "2024-09-02T14:00:00Z"
}
```

**Response Example**:
```json
{
  "status": "success",
  "data": {
    "id": "abc1234-e89b-12d3-a456-426614174003",
    "extractionId": "EXT-20240902-002",
    "extractionType": "MANUAL",
    "extractionFormat": "JSON",
    "scheduledTime": "2024-09-02T14:00:00Z",
    "state": "scheduled"
  }
}
```

#### PUT /api/v1/extractions/{id}
**Purpose**: Update extraction with optional transition
**Method**: PUT
**Parameters**: 
- `id` (path): Extraction UUID
- `transitionName` (query, optional): Workflow transition name

**Request Example**:
```
PUT /api/v1/extractions/789e0123-e89b-12d3-a456-426614174002?transitionName=retry_extraction

{
  "scheduledTime": "2024-09-02T16:00:00Z"
}
```

**Response Example**:
```json
{
  "status": "success",
  "data": {
    "id": "789e0123-e89b-12d3-a456-426614174002",
    "extractionId": "EXT-20240902-001",
    "scheduledTime": "2024-09-02T16:00:00Z",
    "retryCount": 1,
    "state": "scheduled"
  }
}
```

## 3. ReportController

**Base Path**: `/api/v1/reports`

### Endpoints

#### GET /api/v1/reports
**Purpose**: Retrieve all reports with filtering
**Method**: GET
**Parameters**:
- `type` (optional): Filter by report type
- `status` (optional): Filter by report status
- `startDate` (optional): Filter reports after date
- `endDate` (optional): Filter reports before date

**Request Example**:
```
GET /api/v1/reports?type=WEEKLY_SUMMARY&status=emailed
```

**Response Example**:
```json
{
  "status": "success",
  "data": [
    {
      "id": "def5678-e89b-12d3-a456-426614174004",
      "reportId": "RPT-20240902-001",
      "reportType": "WEEKLY_SUMMARY",
      "generationDate": "2024-09-02T08:10:00Z",
      "reportPeriodStart": "2024-08-26T00:00:00Z",
      "reportPeriodEnd": "2024-09-01T23:59:59Z",
      "totalProductsAnalyzed": 25,
      "topPerformingProductCount": 5,
      "underperformingProductCount": 8,
      "restockingRequiredCount": 12,
      "totalRevenue": 45000.00,
      "averageInventoryTurnover": 2.5,
      "reportFilePath": "/reports/RPT-20240902-001.pdf",
      "reportFormat": "PDF",
      "emailSent": true,
      "emailSentDate": "2024-09-02T08:15:00Z",
      "recipientEmail": "victoria.sagdieva@cyoda.com",
      "reportSummary": "Weekly performance shows 8 underperforming products requiring attention.",
      "state": "emailed"
    }
  ]
}
```

#### GET /api/v1/reports/{id}
**Purpose**: Retrieve specific report by ID
**Method**: GET
**Parameters**:
- `id` (path): Report UUID

**Request Example**:
```
GET /api/v1/reports/def5678-e89b-12d3-a456-426614174004
```

**Response Example**:
```json
{
  "status": "success",
  "data": {
    "id": "def5678-e89b-12d3-a456-426614174004",
    "reportId": "RPT-20240902-001",
    "reportType": "WEEKLY_SUMMARY",
    "generationDate": "2024-09-02T08:10:00Z",
    "reportPeriodStart": "2024-08-26T00:00:00Z",
    "reportPeriodEnd": "2024-09-01T23:59:59Z",
    "totalProductsAnalyzed": 25,
    "topPerformingProductCount": 5,
    "underperformingProductCount": 8,
    "restockingRequiredCount": 12,
    "totalRevenue": 45000.00,
    "averageInventoryTurnover": 2.5,
    "reportFilePath": "/reports/RPT-20240902-001.pdf",
    "reportFormat": "PDF",
    "emailSent": true,
    "emailSentDate": "2024-09-02T08:15:00Z",
    "recipientEmail": "victoria.sagdieva@cyoda.com",
    "reportSummary": "Weekly performance shows 8 underperforming products requiring attention.",
    "state": "emailed"
  }
}
```

#### POST /api/v1/reports
**Purpose**: Create manual report generation
**Method**: POST

**Request Example**:
```json
{
  "reportType": "PERFORMANCE_ANALYSIS",
  "reportPeriodStart": "2024-08-01T00:00:00Z",
  "reportPeriodEnd": "2024-08-31T23:59:59Z",
  "recipientEmail": "victoria.sagdieva@cyoda.com",
  "reportFormat": "PDF"
}
```

**Response Example**:
```json
{
  "status": "success",
  "data": {
    "id": "ghi9012-e89b-12d3-a456-426614174005",
    "reportId": "RPT-20240902-002",
    "reportType": "PERFORMANCE_ANALYSIS",
    "generationDate": "2024-09-02T14:00:00Z",
    "reportPeriodStart": "2024-08-01T00:00:00Z",
    "reportPeriodEnd": "2024-08-31T23:59:59Z",
    "recipientEmail": "victoria.sagdieva@cyoda.com",
    "reportFormat": "PDF",
    "emailSent": false,
    "state": "generating"
  }
}
```

#### PUT /api/v1/reports/{id}
**Purpose**: Update report with optional transition
**Method**: PUT
**Parameters**:
- `id` (path): Report UUID
- `transitionName` (query, optional): Workflow transition name

**Request Example**:
```
PUT /api/v1/reports/def5678-e89b-12d3-a456-426614174004?transitionName=retry_email

{
  "recipientEmail": "victoria.sagdieva@cyoda.com"
}
```

**Response Example**:
```json
{
  "status": "success",
  "data": {
    "id": "def5678-e89b-12d3-a456-426614174004",
    "reportId": "RPT-20240902-001",
    "recipientEmail": "victoria.sagdieva@cyoda.com",
    "emailSent": false,
    "state": "generated"
  }
}
```

#### GET /api/v1/reports/{id}/download
**Purpose**: Download report file
**Method**: GET
**Parameters**:
- `id` (path): Report UUID

**Request Example**:
```
GET /api/v1/reports/def5678-e89b-12d3-a456-426614174004/download
```

**Response**: Binary PDF file with appropriate headers

## 4. EmailNotificationController

**Base Path**: `/api/v1/notifications`

### Endpoints

#### GET /api/v1/notifications
**Purpose**: Retrieve all email notifications
**Method**: GET
**Parameters**:
- `status` (optional): Filter by delivery status
- `recipient` (optional): Filter by recipient email
- `startDate` (optional): Filter notifications after date

**Request Example**:
```
GET /api/v1/notifications?status=delivered&recipient=victoria.sagdieva@cyoda.com
```

**Response Example**:
```json
{
  "status": "success",
  "data": [
    {
      "id": "jkl3456-e89b-12d3-a456-426614174006",
      "notificationId": "EMAIL-20240902-001",
      "recipientEmail": "victoria.sagdieva@cyoda.com",
      "subject": "Weekly Product Performance Report - September 2, 2024",
      "sentDate": "2024-09-02T08:15:00Z",
      "deliveryStatus": "DELIVERED",
      "attachmentPath": "/reports/RPT-20240902-001.pdf",
      "retryCount": 0,
      "state": "delivered"
    }
  ]
}
```

#### POST /api/v1/notifications
**Purpose**: Create manual email notification
**Method**: POST

**Request Example**:
```json
{
  "recipientEmail": "victoria.sagdieva@cyoda.com",
  "subject": "Manual Report Notification",
  "emailBody": "Please find the attached performance report.",
  "attachmentPath": "/reports/RPT-20240902-002.pdf"
}
```

**Response Example**:
```json
{
  "status": "success",
  "data": {
    "id": "mno7890-e89b-12d3-a456-426614174007",
    "notificationId": "EMAIL-20240902-002",
    "recipientEmail": "victoria.sagdieva@cyoda.com",
    "subject": "Manual Report Notification",
    "emailBody": "Please find the attached performance report.",
    "attachmentPath": "/reports/RPT-20240902-002.pdf",
    "deliveryStatus": "SENDING",
    "retryCount": 0,
    "state": "sending"
  }
}
```

#### PUT /api/v1/notifications/{id}
**Purpose**: Update notification with optional transition
**Method**: PUT
**Parameters**:
- `id` (path): Notification UUID
- `transitionName` (query, optional): Workflow transition name

**Request Example**:
```
PUT /api/v1/notifications/jkl3456-e89b-12d3-a456-426614174006?transitionName=retry_email_send

{
  "recipientEmail": "victoria.sagdieva@cyoda.com"
}
```

**Response Example**:
```json
{
  "status": "success",
  "data": {
    "id": "jkl3456-e89b-12d3-a456-426614174006",
    "notificationId": "EMAIL-20240902-001",
    "recipientEmail": "victoria.sagdieva@cyoda.com",
    "retryCount": 1,
    "state": "sending"
  }
}
```

## Common Response Formats

### Success Response
```json
{
  "status": "success",
  "data": { /* entity data */ },
  "message": "Optional success message"
}
```

### Error Response
```json
{
  "status": "error",
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid input data",
    "details": {
      "field": "email",
      "reason": "Invalid email format"
    }
  }
}
```

### Validation Error Response
```json
{
  "status": "error",
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": [
      {
        "field": "name",
        "message": "Name is required"
      },
      {
        "field": "stockLevel",
        "message": "Stock level cannot be negative"
      }
    ]
  }
}
```

## HTTP Status Codes

- **200 OK**: Successful GET, PUT requests
- **201 Created**: Successful POST requests
- **204 No Content**: Successful DELETE requests
- **400 Bad Request**: Invalid request data or parameters
- **401 Unauthorized**: Authentication required
- **403 Forbidden**: Insufficient permissions
- **404 Not Found**: Resource not found
- **409 Conflict**: Resource conflict (e.g., duplicate ID)
- **422 Unprocessable Entity**: Validation errors
- **500 Internal Server Error**: Server-side errors

## Authentication and Authorization

All endpoints require authentication via JWT token in Authorization header:
```
Authorization: Bearer <jwt-token>
```

Role-based access control:
- **ADMIN**: Full access to all endpoints
- **USER**: Read access to all endpoints, limited write access
- **SYSTEM**: Internal system access for automated processes
