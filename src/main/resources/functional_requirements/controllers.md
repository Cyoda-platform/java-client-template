# Controllers Requirements

## Overview
This document defines the REST API controllers for the Product Performance Analysis and Reporting System. Each controller provides endpoints for managing entities and triggering workflow transitions.

## 1. ProductController

**Base Path**: `/api/v1/products`

### Endpoints

#### GET /api/v1/products
**Purpose**: Get all products with optional filtering  
**Parameters**: 
- `status` (optional): Filter by product status (available, pending, sold)
- `category` (optional): Filter by product category
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 20)

**Request Example**:
```
GET /api/v1/products?status=available&category=Dogs&page=0&size=10
```

**Response Example**:
```json
{
  "content": [
    {
      "id": 1,
      "name": "Golden Retriever Puppy",
      "category": "Dogs",
      "categoryId": 1,
      "photoUrls": ["https://example.com/photo1.jpg"],
      "tags": ["puppy", "friendly"],
      "price": 1200.00,
      "stockQuantity": 5,
      "salesVolume": 15,
      "revenue": 18000.00,
      "lastSaleDate": "2024-01-15T10:30:00",
      "state": "available",
      "createdAt": "2024-01-01T09:00:00",
      "updatedAt": "2024-01-15T10:30:00"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 10,
  "number": 0
}
```

#### GET /api/v1/products/{id}
**Purpose**: Get product by ID

**Request Example**:
```
GET /api/v1/products/1
```

**Response Example**:
```json
{
  "id": 1,
  "name": "Golden Retriever Puppy",
  "category": "Dogs",
  "categoryId": 1,
  "photoUrls": ["https://example.com/photo1.jpg"],
  "tags": ["puppy", "friendly"],
  "price": 1200.00,
  "stockQuantity": 5,
  "salesVolume": 15,
  "revenue": 18000.00,
  "lastSaleDate": "2024-01-15T10:30:00",
  "state": "available",
  "createdAt": "2024-01-01T09:00:00",
  "updatedAt": "2024-01-15T10:30:00"
}
```

#### POST /api/v1/products
**Purpose**: Create new product (triggers extract_product transition)

**Request Example**:
```json
{
  "name": "Siamese Cat",
  "category": "Cats",
  "categoryId": 2,
  "photoUrls": ["https://example.com/cat1.jpg"],
  "tags": ["cat", "indoor"],
  "price": 800.00,
  "stockQuantity": 3
}
```

**Response Example**:
```json
{
  "id": 2,
  "name": "Siamese Cat",
  "category": "Cats",
  "categoryId": 2,
  "photoUrls": ["https://example.com/cat1.jpg"],
  "tags": ["cat", "indoor"],
  "price": 800.00,
  "stockQuantity": 3,
  "salesVolume": 0,
  "revenue": 0.00,
  "lastSaleDate": null,
  "state": "extracted",
  "createdAt": "2024-01-16T14:20:00",
  "updatedAt": "2024-01-16T14:20:00"
}
```

#### PUT /api/v1/products/{id}
**Purpose**: Update product with optional workflow transition  
**Parameters**: 
- `transition` (optional): Workflow transition name

**Request Example** (with transition):
```
PUT /api/v1/products/1?transition=start_analysis

{
  "name": "Golden Retriever Puppy - Premium",
  "price": 1500.00,
  "stockQuantity": 3
}
```

**Response Example**:
```json
{
  "id": 1,
  "name": "Golden Retriever Puppy - Premium",
  "category": "Dogs",
  "categoryId": 1,
  "photoUrls": ["https://example.com/photo1.jpg"],
  "tags": ["puppy", "friendly"],
  "price": 1500.00,
  "stockQuantity": 3,
  "salesVolume": 15,
  "revenue": 18000.00,
  "lastSaleDate": "2024-01-15T10:30:00",
  "state": "analyzing",
  "createdAt": "2024-01-01T09:00:00",
  "updatedAt": "2024-01-16T15:00:00"
}
```

#### DELETE /api/v1/products/{id}
**Purpose**: Delete product (triggers archive_product transition)

**Request Example**:
```
DELETE /api/v1/products/1
```

**Response Example**:
```json
{
  "message": "Product archived successfully",
  "id": 1,
  "state": "archived"
}
```

## 2. PerformanceMetricController

**Base Path**: `/api/v1/metrics`

### Endpoints

#### GET /api/v1/metrics
**Purpose**: Get all performance metrics with filtering  
**Parameters**: 
- `productId` (optional): Filter by product ID
- `metricType` (optional): Filter by metric type
- `periodStart` (optional): Filter by period start date
- `periodEnd` (optional): Filter by period end date

**Request Example**:
```
GET /api/v1/metrics?productId=1&metricType=SALES_VOLUME&periodStart=2024-01-01&periodEnd=2024-01-31
```

**Response Example**:
```json
{
  "content": [
    {
      "id": 1,
      "productId": 1,
      "metricType": "SALES_VOLUME",
      "metricValue": 15.00,
      "calculationPeriod": "WEEKLY",
      "periodStart": "2024-01-08",
      "periodEnd": "2024-01-14",
      "calculatedAt": "2024-01-15T09:00:00",
      "isOutlier": false,
      "state": "published"
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

#### POST /api/v1/metrics
**Purpose**: Create new performance metric (triggers queue_calculation transition)

**Request Example**:
```json
{
  "productId": 1,
  "metricType": "REVENUE",
  "calculationPeriod": "WEEKLY",
  "periodStart": "2024-01-15",
  "periodEnd": "2024-01-21"
}
```

**Response Example**:
```json
{
  "id": 2,
  "productId": 1,
  "metricType": "REVENUE",
  "metricValue": null,
  "calculationPeriod": "WEEKLY",
  "periodStart": "2024-01-15",
  "periodEnd": "2024-01-21",
  "calculatedAt": null,
  "isOutlier": false,
  "state": "pending"
}
```

#### PUT /api/v1/metrics/{id}
**Purpose**: Update metric with optional transition  
**Parameters**: 
- `transition` (optional): Workflow transition name

**Request Example**:
```
PUT /api/v1/metrics/2?transition=start_calculation

{
  "metricValue": 22500.00
}
```

**Response Example**:
```json
{
  "id": 2,
  "productId": 1,
  "metricType": "REVENUE",
  "metricValue": 22500.00,
  "calculationPeriod": "WEEKLY",
  "periodStart": "2024-01-15",
  "periodEnd": "2024-01-21",
  "calculatedAt": "2024-01-16T10:00:00",
  "isOutlier": false,
  "state": "calculating"
}
```

## 3. ReportController

**Base Path**: `/api/v1/reports`

### Endpoints

#### GET /api/v1/reports
**Purpose**: Get all reports with filtering  
**Parameters**: 
- `reportType` (optional): Filter by report type
- `generationDate` (optional): Filter by generation date
- `state` (optional): Filter by report state

**Request Example**:
```
GET /api/v1/reports?reportType=WEEKLY_SUMMARY&state=distributed
```

**Response Example**:
```json
{
  "content": [
    {
      "id": 1,
      "reportName": "Weekly Performance Report 2024-01-15",
      "reportType": "WEEKLY_SUMMARY",
      "generationDate": "2024-01-15T18:00:00",
      "reportPeriodStart": "2024-01-08",
      "reportPeriodEnd": "2024-01-14",
      "filePath": "/reports/weekly_2024_01_15.pdf",
      "fileFormat": "PDF",
      "summary": "Weekly performance shows 15% increase in sales...",
      "totalProducts": 25,
      "topPerformingProducts": ["Golden Retriever Puppy", "Siamese Cat"],
      "underperformingProducts": ["Hamster Cage"],
      "keyInsights": ["Pet food sales increased", "Toy category declining"],
      "state": "distributed"
    }
  ]
}
```

#### POST /api/v1/reports
**Purpose**: Create new report (triggers schedule_report transition)

**Request Example**:
```json
{
  "reportName": "Weekly Performance Report 2024-01-22",
  "reportType": "WEEKLY_SUMMARY",
  "reportPeriodStart": "2024-01-15",
  "reportPeriodEnd": "2024-01-21",
  "fileFormat": "PDF"
}
```

**Response Example**:
```json
{
  "id": 2,
  "reportName": "Weekly Performance Report 2024-01-22",
  "reportType": "WEEKLY_SUMMARY",
  "generationDate": "2024-01-22T18:00:00",
  "reportPeriodStart": "2024-01-15",
  "reportPeriodEnd": "2024-01-21",
  "filePath": null,
  "fileFormat": "PDF",
  "summary": null,
  "totalProducts": 0,
  "topPerformingProducts": [],
  "underperformingProducts": [],
  "keyInsights": [],
  "state": "scheduled"
}
```

#### PUT /api/v1/reports/{id}
**Purpose**: Update report with optional transition  
**Parameters**: 
- `transition` (optional): Workflow transition name

**Request Example**:
```
PUT /api/v1/reports/2?transition=start_generation

{
  "summary": "Updated summary for the report"
}
```

#### GET /api/v1/reports/{id}/download
**Purpose**: Download report file

**Request Example**:
```
GET /api/v1/reports/1/download
```

**Response**: Binary PDF file with appropriate headers

## 4. EmailNotificationController

**Base Path**: `/api/v1/notifications`

### Endpoints

#### GET /api/v1/notifications
**Purpose**: Get all email notifications with filtering  
**Parameters**: 
- `reportId` (optional): Filter by report ID
- `deliveryStatus` (optional): Filter by delivery status
- `recipientEmail` (optional): Filter by recipient

**Request Example**:
```
GET /api/v1/notifications?reportId=1&deliveryStatus=DELIVERED
```

**Response Example**:
```json
{
  "content": [
    {
      "id": 1,
      "reportId": 1,
      "recipientEmail": "victoria.sagdieva@cyoda.com",
      "subject": "Weekly Product Performance Report - Jan 8-14, 2024",
      "bodyContent": "Please find attached the weekly performance report...",
      "attachmentPath": "/reports/weekly_2024_01_15.pdf",
      "scheduledSendTime": "2024-01-15T18:30:00",
      "actualSendTime": "2024-01-15T18:31:00",
      "deliveryStatus": "DELIVERED",
      "errorMessage": null,
      "retryCount": 0,
      "maxRetries": 3,
      "state": "delivered"
    }
  ]
}
```

#### POST /api/v1/notifications
**Purpose**: Create new email notification (triggers queue_email transition)

**Request Example**:
```json
{
  "reportId": 2,
  "recipientEmail": "victoria.sagdieva@cyoda.com",
  "subject": "Weekly Product Performance Report - Jan 15-21, 2024",
  "bodyContent": "Please find attached the latest weekly performance report with key insights...",
  "scheduledSendTime": "2024-01-22T18:30:00"
}
```

**Response Example**:
```json
{
  "id": 2,
  "reportId": 2,
  "recipientEmail": "victoria.sagdieva@cyoda.com",
  "subject": "Weekly Product Performance Report - Jan 15-21, 2024",
  "bodyContent": "Please find attached the latest weekly performance report...",
  "attachmentPath": "/reports/weekly_2024_01_22.pdf",
  "scheduledSendTime": "2024-01-22T18:30:00",
  "actualSendTime": null,
  "deliveryStatus": "PENDING",
  "errorMessage": null,
  "retryCount": 0,
  "maxRetries": 3,
  "state": "pending"
}
```

#### PUT /api/v1/notifications/{id}
**Purpose**: Update notification with optional transition  
**Parameters**: 
- `transition` (optional): Workflow transition name

**Request Example**:
```
PUT /api/v1/notifications/2?transition=start_sending

{
  "scheduledSendTime": "2024-01-22T19:00:00"
}
```

## 5. DataExtractionJobController

**Base Path**: `/api/v1/jobs`

### Endpoints

#### GET /api/v1/jobs
**Purpose**: Get all data extraction jobs

**Request Example**:
```
GET /api/v1/jobs
```

**Response Example**:
```json
{
  "content": [
    {
      "id": 1,
      "jobName": "Weekly Pet Store Data Extraction",
      "scheduledTime": "2024-01-15T09:00:00",
      "startTime": "2024-01-15T09:01:00",
      "endTime": "2024-01-15T09:15:00",
      "extractionType": "PRODUCTS",
      "apiEndpoint": "https://petstore.swagger.io/v2",
      "recordsExtracted": 25,
      "recordsProcessed": 24,
      "recordsFailed": 1,
      "errorLog": "Product ID 999 not found",
      "nextScheduledRun": "2024-01-22T09:00:00",
      "state": "completed"
    }
  ]
}
```

#### POST /api/v1/jobs
**Purpose**: Create new extraction job (triggers schedule_job transition)

**Request Example**:
```json
{
  "jobName": "Manual Pet Store Data Extraction",
  "extractionType": "PRODUCTS",
  "scheduledTime": "2024-01-16T14:00:00"
}
```

**Response Example**:
```json
{
  "id": 2,
  "jobName": "Manual Pet Store Data Extraction",
  "scheduledTime": "2024-01-16T14:00:00",
  "startTime": null,
  "endTime": null,
  "extractionType": "PRODUCTS",
  "apiEndpoint": "https://petstore.swagger.io/v2",
  "recordsExtracted": 0,
  "recordsProcessed": 0,
  "recordsFailed": 0,
  "errorLog": null,
  "nextScheduledRun": "2024-01-23T14:00:00",
  "state": "scheduled"
}
```

## Common Response Patterns

### Error Responses
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Product name cannot be empty",
  "timestamp": "2024-01-16T15:30:00",
  "path": "/api/v1/products"
}
```

### Validation Errors
```json
{
  "error": "INVALID_TRANSITION",
  "message": "Cannot transition from 'extracted' to 'analyzing'. Product must be in 'available' state.",
  "currentState": "extracted",
  "requestedTransition": "start_analysis",
  "validTransitions": ["validate_product"]
}
```

## API Design Notes

1. **Consistent Patterns**: All controllers follow RESTful conventions
2. **State Management**: Update endpoints support optional transition parameters
3. **Filtering**: GET endpoints support comprehensive filtering options
4. **Pagination**: List endpoints support pagination for large datasets
5. **Error Handling**: Consistent error response format across all endpoints
6. **Validation**: All endpoints include comprehensive input validation
7. **Security**: All endpoints require appropriate authentication (not shown in examples)
