# Controller Requirements

## Overview
This document defines the REST controller requirements for the Product Performance Analysis and Reporting System. Each entity has its own controller providing CRUD operations and workflow transition endpoints.

## Controller Definitions

### 1. PetController

**Base Path**: `/api/pets`

#### Endpoints

##### GET /api/pets
**Purpose**: Retrieve all pets with optional filtering  
**Parameters**: 
- `status` (optional): Filter by pet status (available, pending, sold)
- `category` (optional): Filter by category name
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 20)

**Request Example**:
```
GET /api/pets?status=available&category=Dogs&page=0&size=10
```

**Response Example**:
```json
{
  "content": [
    {
      "uuid": "123e4567-e89b-12d3-a456-426614174000",
      "entity": {
        "petId": 1,
        "name": "Buddy",
        "category": {
          "id": 1,
          "name": "Dogs"
        },
        "photoUrls": ["https://example.com/photo1.jpg"],
        "tags": [{"id": 1, "name": "friendly"}],
        "price": 299.99,
        "stockLevel": 15,
        "salesVolume": 25,
        "revenue": 7499.75,
        "lastSaleDate": "2024-01-15T10:30:00Z",
        "createdAt": "2024-01-01T09:00:00Z",
        "updatedAt": "2024-01-15T10:30:00Z"
      },
      "meta": {
        "state": "active",
        "version": 1
      }
    }
  ],
  "totalElements": 50,
  "totalPages": 5,
  "size": 10,
  "number": 0
}
```

##### GET /api/pets/{uuid}
**Purpose**: Retrieve specific pet by UUID

**Request Example**:
```
GET /api/pets/123e4567-e89b-12d3-a456-426614174000
```

**Response Example**:
```json
{
  "uuid": "123e4567-e89b-12d3-a456-426614174000",
  "entity": {
    "petId": 1,
    "name": "Buddy",
    "category": {"id": 1, "name": "Dogs"},
    "photoUrls": ["https://example.com/photo1.jpg"],
    "tags": [{"id": 1, "name": "friendly"}],
    "price": 299.99,
    "stockLevel": 15,
    "salesVolume": 25,
    "revenue": 7499.75,
    "lastSaleDate": "2024-01-15T10:30:00Z",
    "createdAt": "2024-01-01T09:00:00Z",
    "updatedAt": "2024-01-15T10:30:00Z"
  },
  "meta": {
    "state": "active",
    "version": 1
  }
}
```

##### POST /api/pets
**Purpose**: Create new pet entity

**Request Example**:
```json
{
  "petId": 2,
  "name": "Max",
  "category": {"id": 2, "name": "Cats"},
  "photoUrls": ["https://example.com/cat1.jpg"],
  "tags": [{"id": 2, "name": "playful"}],
  "price": 199.99,
  "stockLevel": 10
}
```

**Response Example**:
```json
{
  "uuid": "456e7890-e89b-12d3-a456-426614174001",
  "entity": {
    "petId": 2,
    "name": "Max",
    "category": {"id": 2, "name": "Cats"},
    "photoUrls": ["https://example.com/cat1.jpg"],
    "tags": [{"id": 2, "name": "playful"}],
    "price": 199.99,
    "stockLevel": 10,
    "salesVolume": 0,
    "revenue": 0.0,
    "lastSaleDate": null,
    "createdAt": "2024-01-16T14:20:00Z",
    "updatedAt": "2024-01-16T14:20:00Z"
  },
  "meta": {
    "state": "none",
    "version": 1
  }
}
```

##### PUT /api/pets/{uuid}
**Purpose**: Update pet entity with optional state transition  
**Parameters**: 
- `transitionName` (optional): Workflow transition to execute

**Request Example**:
```
PUT /api/pets/123e4567-e89b-12d3-a456-426614174000?transitionName=activate_tracking

{
  "petId": 1,
  "name": "Buddy Updated",
  "category": {"id": 1, "name": "Dogs"},
  "photoUrls": ["https://example.com/photo1.jpg"],
  "tags": [{"id": 1, "name": "friendly"}],
  "price": 319.99,
  "stockLevel": 12
}
```

**Response Example**:
```json
{
  "uuid": "123e4567-e89b-12d3-a456-426614174000",
  "entity": {
    "petId": 1,
    "name": "Buddy Updated",
    "price": 319.99,
    "stockLevel": 12,
    "updatedAt": "2024-01-16T15:00:00Z"
  },
  "meta": {
    "state": "active",
    "version": 2
  }
}
```

##### DELETE /api/pets/{uuid}
**Purpose**: Delete pet entity

**Request Example**:
```
DELETE /api/pets/123e4567-e89b-12d3-a456-426614174000
```

**Response**: 204 No Content

### 2. OrderController

**Base Path**: `/api/orders`

#### Endpoints

##### GET /api/orders
**Purpose**: Retrieve all orders with optional filtering  
**Parameters**: 
- `petId` (optional): Filter by pet ID
- `status` (optional): Filter by order status
- `startDate` (optional): Filter orders after date
- `endDate` (optional): Filter orders before date
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 20)

**Request Example**:
```
GET /api/orders?petId=1&status=completed&startDate=2024-01-01&endDate=2024-01-31&page=0&size=10
```

**Response Example**:
```json
{
  "content": [
    {
      "uuid": "789e0123-e89b-12d3-a456-426614174002",
      "entity": {
        "orderId": 1001,
        "petId": 1,
        "quantity": 2,
        "unitPrice": 299.99,
        "totalAmount": 599.98,
        "shipDate": "2024-01-16T00:00:00Z",
        "orderDate": "2024-01-15T10:30:00Z",
        "customerInfo": {
          "name": "John Doe",
          "email": "john.doe@example.com",
          "phone": "+1-555-0123"
        },
        "complete": true
      },
      "meta": {
        "state": "completed",
        "version": 1
      }
    }
  ],
  "totalElements": 25,
  "totalPages": 3,
  "size": 10,
  "number": 0
}
```

##### POST /api/orders
**Purpose**: Create new order entity

**Request Example**:
```json
{
  "orderId": 1002,
  "petId": 2,
  "quantity": 1,
  "unitPrice": 199.99,
  "totalAmount": 199.99,
  "shipDate": "2024-01-18T00:00:00Z",
  "orderDate": "2024-01-16T15:30:00Z",
  "customerInfo": {
    "name": "Jane Smith",
    "email": "jane.smith@example.com",
    "phone": "+1-555-0456"
  },
  "complete": false
}
```

**Response Example**:
```json
{
  "uuid": "abc1234d-e89b-12d3-a456-426614174003",
  "entity": {
    "orderId": 1002,
    "petId": 2,
    "quantity": 1,
    "unitPrice": 199.99,
    "totalAmount": 199.99,
    "shipDate": "2024-01-18T00:00:00Z",
    "orderDate": "2024-01-16T15:30:00Z",
    "customerInfo": {
      "name": "Jane Smith",
      "email": "jane.smith@example.com",
      "phone": "+1-555-0456"
    },
    "complete": false
  },
  "meta": {
    "state": "none",
    "version": 1
  }
}
```

##### PUT /api/orders/{uuid}
**Purpose**: Update order entity with optional state transition  
**Parameters**: 
- `transitionName` (optional): Workflow transition to execute

**Request Example**:
```
PUT /api/orders/789e0123-e89b-12d3-a456-426614174002?transitionName=complete_order

{
  "complete": true
}
```

### 3. ReportController

**Base Path**: `/api/reports`

#### Endpoints

##### GET /api/reports
**Purpose**: Retrieve all reports with optional filtering  
**Parameters**: 
- `reportType` (optional): Filter by report type
- `startDate` (optional): Filter reports after date
- `endDate` (optional): Filter reports before date
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 20)

**Request Example**:
```
GET /api/reports?reportType=WEEKLY_PERFORMANCE&startDate=2024-01-01&page=0&size=5
```

**Response Example**:
```json
{
  "content": [
    {
      "uuid": "def5678e-e89b-12d3-a456-426614174004",
      "entity": {
        "reportId": "report-2024-01-15",
        "reportType": "WEEKLY_PERFORMANCE",
        "generationDate": "2024-01-15T08:00:00Z",
        "reportPeriod": {
          "startDate": "2024-01-08T00:00:00Z",
          "endDate": "2024-01-15T00:00:00Z"
        },
        "metrics": {
          "totalSales": 150,
          "totalRevenue": 45000.00,
          "topSellingPets": ["Buddy", "Max", "Luna"],
          "slowMovingPets": ["Rex"],
          "inventoryTurnover": 2.5
        },
        "filePath": "/reports/weekly-2024-01-15.pdf",
        "fileFormat": "PDF",
        "fileSize": 2048576
      },
      "meta": {
        "state": "distributed",
        "version": 1
      }
    }
  ]
}
```

##### POST /api/reports
**Purpose**: Create new report entity

**Request Example**:
```json
{
  "reportType": "WEEKLY_PERFORMANCE",
  "reportPeriod": {
    "startDate": "2024-01-15T00:00:00Z",
    "endDate": "2024-01-22T00:00:00Z"
  },
  "fileFormat": "PDF"
}
```

##### PUT /api/reports/{uuid}
**Purpose**: Update report entity with optional state transition  
**Parameters**: 
- `transitionName` (optional): Workflow transition to execute

**Request Example**:
```
PUT /api/reports/def5678e-e89b-12d3-a456-426614174004?transitionName=review_report
```

##### GET /api/reports/{uuid}/download
**Purpose**: Download report file

**Request Example**:
```
GET /api/reports/def5678e-e89b-12d3-a456-426614174004/download
```

**Response**: Binary file content with appropriate headers

### 4. EmailNotificationController

**Base Path**: `/api/notifications`

#### Endpoints

##### GET /api/notifications
**Purpose**: Retrieve all email notifications with optional filtering  
**Parameters**: 
- `reportId` (optional): Filter by report ID
- `recipientEmail` (optional): Filter by recipient
- `status` (optional): Filter by notification status
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 20)

**Request Example**:
```
GET /api/notifications?reportId=report-2024-01-15&status=sent&page=0&size=10
```

**Response Example**:
```json
{
  "content": [
    {
      "uuid": "ghi9012f-e89b-12d3-a456-426614174005",
      "entity": {
        "notificationId": "notif-2024-01-15-001",
        "reportId": "report-2024-01-15",
        "recipientEmail": "victoria.sagdieva@cyoda.com",
        "subject": "Weekly Performance Report - 2024-01-15",
        "body": "Please find attached the weekly performance report...",
        "attachmentPath": "/reports/weekly-2024-01-15.pdf",
        "scheduledTime": "2024-01-15T08:05:00Z",
        "sentTime": "2024-01-15T08:05:23Z",
        "deliveryAttempts": 1,
        "lastError": null
      },
      "meta": {
        "state": "sent",
        "version": 1
      }
    }
  ]
}
```

##### POST /api/notifications
**Purpose**: Create new email notification

**Request Example**:
```json
{
  "reportId": "report-2024-01-22",
  "recipientEmail": "victoria.sagdieva@cyoda.com",
  "subject": "Weekly Performance Report - 2024-01-22",
  "body": "Please find attached the weekly performance report for the period ending January 22, 2024.",
  "attachmentPath": "/reports/weekly-2024-01-22.pdf",
  "scheduledTime": "2024-01-22T08:00:00Z"
}
```

##### PUT /api/notifications/{uuid}
**Purpose**: Update notification entity with optional state transition  
**Parameters**: 
- `transitionName` (optional): Workflow transition to execute

**Request Example**:
```
PUT /api/notifications/ghi9012f-e89b-12d3-a456-426614174005?transitionName=retry_sending
```

## API Standards

### Response Format
All responses follow the EntityWithMetadata pattern:
- `uuid`: Entity unique identifier
- `entity`: Business entity data
- `meta`: Technical metadata including state and version

### Error Handling
Standard HTTP status codes:
- 200: Success
- 201: Created
- 204: No Content
- 400: Bad Request
- 404: Not Found
- 500: Internal Server Error

### Pagination
List endpoints support pagination:
- `page`: Zero-based page number
- `size`: Number of items per page
- Response includes `totalElements`, `totalPages`, `size`, `number`

### Filtering
Query parameters for filtering:
- Date filters use ISO 8601 format
- String filters support partial matching
- Numeric filters support exact matching

### Security
- All endpoints require authentication
- Input validation on all request bodies
- SQL injection prevention
- XSS protection on string fields
