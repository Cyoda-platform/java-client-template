# Controllers Specification

## Overview
This document defines the REST API controllers for the Weekly Cat Fact Subscription application. Each entity has its own controller with endpoints aligned to workflow transitions.

## 1. SubscriberController

### Base Path: `/api/subscribers`

### Endpoints

#### POST /api/subscribers
**Purpose**: Register new subscriber  
**Workflow Transition**: INITIAL → PENDING  
**Transition Name**: null (automatic transition)

**Request Body**:
```json
{
    "email": "user@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "preferences": {
        "emailFormat": "html",
        "frequency": "weekly"
    }
}
```

**Response Body**:
```json
{
    "entity": {
        "email": "user@example.com",
        "firstName": "John",
        "lastName": "Doe",
        "subscriptionDate": "2024-01-15T10:30:00Z",
        "isActive": true,
        "totalEmailsReceived": 0,
        "unsubscribeToken": "abc123xyz"
    },
    "meta": {
        "uuid": "550e8400-e29b-41d4-a716-446655440000",
        "state": "PENDING",
        "createdAt": "2024-01-15T10:30:00Z",
        "updatedAt": "2024-01-15T10:30:00Z"
    }
}
```

#### PUT /api/subscribers/{uuid}/activate
**Purpose**: Activate pending subscriber  
**Workflow Transition**: PENDING → ACTIVE  
**Transition Name**: "activate"

**Request Body**:
```json
{
    "transitionName": "activate",
    "confirmationToken": "confirmation123"
}
```

**Response Body**:
```json
{
    "entity": {
        "email": "user@example.com",
        "isActive": true
    },
    "meta": {
        "uuid": "550e8400-e29b-41d4-a716-446655440000",
        "state": "ACTIVE",
        "updatedAt": "2024-01-15T10:35:00Z"
    }
}
```

#### PUT /api/subscribers/{uuid}/unsubscribe
**Purpose**: Unsubscribe active subscriber  
**Workflow Transition**: ACTIVE → UNSUBSCRIBED  
**Transition Name**: "unsubscribe"

**Request Body**:
```json
{
    "transitionName": "unsubscribe",
    "unsubscribeToken": "abc123xyz",
    "reason": "No longer interested"
}
```

#### PUT /api/subscribers/{uuid}/reactivate
**Purpose**: Reactivate bounced subscriber  
**Workflow Transition**: BOUNCED → ACTIVE  
**Transition Name**: "reactivate"

**Request Body**:
```json
{
    "transitionName": "reactivate",
    "newEmail": "newemail@example.com"
}
```

#### PUT /api/subscribers/{uuid}/resubscribe
**Purpose**: Resubscribe unsubscribed user  
**Workflow Transition**: UNSUBSCRIBED → ACTIVE  
**Transition Name**: "resubscribe"

**Request Body**:
```json
{
    "transitionName": "resubscribe",
    "email": "user@example.com",
    "confirmOptIn": true
}
```

#### GET /api/subscribers
**Purpose**: List all subscribers with filtering

**Query Parameters**:
- `state`: Filter by subscriber state (PENDING, ACTIVE, UNSUBSCRIBED, BOUNCED)
- `page`: Page number (default: 0)
- `size`: Page size (default: 20)

#### GET /api/subscribers/{uuid}
**Purpose**: Get subscriber by UUID

## 2. CatFactController

### Base Path: `/api/catfacts`

### Endpoints

#### POST /api/catfacts/ingest
**Purpose**: Trigger cat fact ingestion from API  
**Workflow Transition**: INITIAL → RETRIEVED  
**Transition Name**: null (automatic transition)

**Request Body**:
```json
{
    "source": "catfact.ninja",
    "count": 5
}
```

**Response Body**:
```json
{
    "entity": {
        "factId": "fact_001",
        "text": "Cats have 32 muscles in each ear.",
        "length": 32,
        "retrievedDate": "2024-01-15T10:30:00Z",
        "source": "catfact.ninja",
        "isUsed": false,
        "usageCount": 0
    },
    "meta": {
        "uuid": "660e8400-e29b-41d4-a716-446655440001",
        "state": "RETRIEVED",
        "createdAt": "2024-01-15T10:30:00Z"
    }
}
```

#### PUT /api/catfacts/{uuid}/validate
**Purpose**: Validate retrieved cat fact  
**Workflow Transition**: RETRIEVED → READY  
**Transition Name**: "validate"

**Request Body**:
```json
{
    "transitionName": "validate"
}
```

#### PUT /api/catfacts/{uuid}/use
**Purpose**: Mark cat fact as used in campaign  
**Workflow Transition**: READY → USED  
**Transition Name**: "use"

**Request Body**:
```json
{
    "transitionName": "use",
    "campaignId": "campaign_001"
}
```

#### PUT /api/catfacts/{uuid}/archive
**Purpose**: Archive overused cat fact  
**Workflow Transition**: USED → ARCHIVED  
**Transition Name**: "archive"

**Request Body**:
```json
{
    "transitionName": "archive",
    "reason": "Exceeded usage limit"
}
```

#### GET /api/catfacts
**Purpose**: List cat facts with filtering

**Query Parameters**:
- `state`: Filter by fact state (RETRIEVED, READY, USED, ARCHIVED)
- `unused`: Boolean to filter unused facts
- `page`: Page number
- `size`: Page size

#### GET /api/catfacts/random
**Purpose**: Get random ready cat fact for campaign use

## 3. EmailCampaignController

### Base Path: `/api/campaigns`

### Endpoints

#### POST /api/campaigns
**Purpose**: Create and schedule new email campaign  
**Workflow Transition**: INITIAL → SCHEDULED  
**Transition Name**: null (automatic transition)

**Request Body**:
```json
{
    "campaignName": "Weekly Cat Facts - Week 3",
    "catFactId": "fact_001",
    "scheduledDate": "2024-01-22T09:00:00Z",
    "emailSubject": "Your Weekly Cat Fact is Here!",
    "emailTemplate": "weekly_template"
}
```

**Response Body**:
```json
{
    "entity": {
        "campaignId": "campaign_001",
        "campaignName": "Weekly Cat Facts - Week 3",
        "catFactId": "fact_001",
        "scheduledDate": "2024-01-22T09:00:00Z",
        "totalSubscribers": 150,
        "successfulDeliveries": 0,
        "failedDeliveries": 0,
        "emailSubject": "Your Weekly Cat Fact is Here!"
    },
    "meta": {
        "uuid": "770e8400-e29b-41d4-a716-446655440002",
        "state": "SCHEDULED",
        "createdAt": "2024-01-15T10:30:00Z"
    }
}
```

#### PUT /api/campaigns/{uuid}/send
**Purpose**: Start sending scheduled campaign  
**Workflow Transition**: SCHEDULED → SENDING  
**Transition Name**: "send"

**Request Body**:
```json
{
    "transitionName": "send",
    "priority": "normal"
}
```

#### PUT /api/campaigns/{uuid}/cancel
**Purpose**: Cancel scheduled campaign  
**Workflow Transition**: SCHEDULED → CANCELLED  
**Transition Name**: "cancel"

**Request Body**:
```json
{
    "transitionName": "cancel",
    "reason": "Content needs revision"
}
```

#### PUT /api/campaigns/{uuid}/retry
**Purpose**: Retry failed campaign  
**Workflow Transition**: FAILED → SCHEDULED  
**Transition Name**: "retry"

**Request Body**:
```json
{
    "transitionName": "retry",
    "newScheduledDate": "2024-01-23T09:00:00Z"
}
```

#### GET /api/campaigns
**Purpose**: List email campaigns with filtering

**Query Parameters**:
- `state`: Filter by campaign state
- `dateFrom`: Filter campaigns from date
- `dateTo`: Filter campaigns to date
- `page`: Page number
- `size`: Page size

#### GET /api/campaigns/{uuid}
**Purpose**: Get campaign details by UUID

#### GET /api/campaigns/{uuid}/report
**Purpose**: Get detailed campaign performance report

**Response Body**:
```json
{
    "campaignId": "campaign_001",
    "totalSubscribers": 150,
    "successfulDeliveries": 142,
    "failedDeliveries": 8,
    "bounces": 3,
    "opens": 89,
    "clicks": 23,
    "unsubscribes": 2,
    "deliveryRate": 94.7,
    "openRate": 62.7,
    "clickRate": 25.8
}
```

## Common Response Patterns

### Success Response Format
All successful responses follow the EntityWithMetadata pattern:
```json
{
    "entity": { /* entity data */ },
    "meta": {
        "uuid": "entity-uuid",
        "state": "CURRENT_STATE",
        "createdAt": "timestamp",
        "updatedAt": "timestamp"
    }
}
```

### Error Response Format
```json
{
    "error": {
        "code": "VALIDATION_ERROR",
        "message": "Invalid email format",
        "details": {
            "field": "email",
            "value": "invalid-email"
        }
    }
}
```

### Pagination Response Format
```json
{
    "content": [ /* array of entities */ ],
    "page": {
        "number": 0,
        "size": 20,
        "totalElements": 150,
        "totalPages": 8
    }
}
```

## API Design Principles

### RESTful Design
- Standard HTTP methods (GET, POST, PUT, DELETE)
- Resource-based URLs
- Consistent response formats
- Proper HTTP status codes

### Workflow Integration
- Transition names align with workflow definitions
- State validation before transitions
- Automatic vs manual transition handling
- Error handling for invalid transitions

### Security Considerations
- Input validation on all endpoints
- Authentication required for all operations
- Rate limiting on public endpoints
- Secure unsubscribe token handling
