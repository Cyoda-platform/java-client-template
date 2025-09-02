# Controllers

This document defines the REST API controllers for the Weekly Cat Fact Subscription application.

## Controller Overview

The application provides REST APIs for subscriber management, cat fact retrieval, email campaign management, and reporting. Each entity has its own controller following RESTful conventions.

## 1. SubscriberController

**Base Path**: `/api/subscribers`
**Purpose**: Manages subscriber operations including registration, verification, and subscription management.

### Endpoints

#### POST /api/subscribers
**Purpose**: Register a new subscriber
**Transition**: subscribe (none → pending)

**Request Body**:
```json
{
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "preferences": {
    "preferredDay": "MONDAY",
    "timezone": "America/New_York"
  }
}
```

**Response** (201 Created):
```json
{
  "id": "user@example.com",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "subscriptionDate": "2024-01-15T10:30:00Z",
  "isActive": false,
  "state": "pending"
}
```

#### POST /api/subscribers/{email}/verify
**Purpose**: Verify subscriber email
**Transition**: verify_email (pending → active)

**Request Body**:
```json
{
  "verificationToken": "abc123-def456-ghi789"
}
```

**Response** (200 OK):
```json
{
  "id": "user@example.com",
  "email": "user@example.com",
  "isActive": true,
  "state": "active",
  "message": "Email verified successfully"
}
```

#### PUT /api/subscribers/{email}
**Purpose**: Update subscriber information or state
**Transition**: pause_subscription, resume_subscription, or null

**Request Body**:
```json
{
  "firstName": "John",
  "lastName": "Smith",
  "preferences": {
    "preferredDay": "TUESDAY",
    "timezone": "America/Los_Angeles"
  },
  "transitionName": "pause_subscription"
}
```

**Response** (200 OK):
```json
{
  "id": "user@example.com",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Smith",
  "isActive": false,
  "state": "inactive"
}
```

#### DELETE /api/subscribers/{email}
**Purpose**: Unsubscribe a subscriber
**Transition**: unsubscribe (any state → unsubscribed)

**Request Body**:
```json
{
  "reason": "No longer interested",
  "transitionName": "unsubscribe"
}
```

**Response** (200 OK):
```json
{
  "message": "Successfully unsubscribed",
  "unsubscribeDate": "2024-01-15T15:45:00Z"
}
```

#### GET /api/subscribers/{email}
**Purpose**: Get subscriber details
**Transition**: null

**Response** (200 OK):
```json
{
  "id": "user@example.com",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "subscriptionDate": "2024-01-15T10:30:00Z",
  "isActive": true,
  "state": "active",
  "preferences": {
    "preferredDay": "MONDAY",
    "timezone": "America/New_York"
  }
}
```

#### GET /api/subscribers
**Purpose**: Get all subscribers with optional filtering
**Transition**: null

**Query Parameters**:
- `state` (optional): Filter by subscriber state
- `active` (optional): Filter by active status
- `page` (optional): Page number for pagination
- `size` (optional): Page size for pagination

**Response** (200 OK):
```json
{
  "subscribers": [
    {
      "id": "user1@example.com",
      "email": "user1@example.com",
      "firstName": "John",
      "isActive": true,
      "state": "active"
    }
  ],
  "totalCount": 150,
  "page": 0,
  "size": 20
}
```

## 2. CatFactController

**Base Path**: `/api/catfacts`
**Purpose**: Manages cat facts including retrieval, validation, and lifecycle operations.

### Endpoints

#### POST /api/catfacts/fetch
**Purpose**: Manually trigger cat fact retrieval from API
**Transition**: fetch_from_api (none → retrieved)

**Request Body**:
```json
{
  "source": "catfact.ninja",
  "transitionName": "fetch_from_api"
}
```

**Response** (201 Created):
```json
{
  "id": "fact-123",
  "factText": "Cats have 32 muscles in each ear.",
  "length": 32,
  "source": "catfact.ninja",
  "retrievedDate": "2024-01-15T12:00:00Z",
  "state": "retrieved"
}
```

#### PUT /api/catfacts/{id}
**Purpose**: Update cat fact or trigger state transition
**Transition**: validate_content, approve_for_use, archive_fact, reject_fact

**Request Body**:
```json
{
  "transitionName": "approve_for_use",
  "notes": "Good quality fact, approved for use"
}
```

**Response** (200 OK):
```json
{
  "id": "fact-123",
  "factText": "Cats have 32 muscles in each ear.",
  "state": "ready",
  "usageCount": 0
}
```

#### GET /api/catfacts/{id}
**Purpose**: Get specific cat fact details
**Transition**: null

**Response** (200 OK):
```json
{
  "id": "fact-123",
  "factText": "Cats have 32 muscles in each ear.",
  "length": 32,
  "source": "catfact.ninja",
  "retrievedDate": "2024-01-15T12:00:00Z",
  "usageCount": 2,
  "lastUsedDate": "2024-01-20T09:00:00Z",
  "state": "ready"
}
```

#### GET /api/catfacts
**Purpose**: Get cat facts with filtering and pagination
**Transition**: null

**Query Parameters**:
- `state` (optional): Filter by fact state
- `minLength` (optional): Minimum fact length
- `maxLength` (optional): Maximum fact length
- `page` (optional): Page number
- `size` (optional): Page size

**Response** (200 OK):
```json
{
  "facts": [
    {
      "id": "fact-123",
      "factText": "Cats have 32 muscles in each ear.",
      "length": 32,
      "state": "ready",
      "usageCount": 2
    }
  ],
  "totalCount": 45,
  "page": 0,
  "size": 10
}
```

## 3. EmailCampaignController

**Base Path**: `/api/campaigns`
**Purpose**: Manages email campaigns including creation, scheduling, and execution.

### Endpoints

#### POST /api/campaigns
**Purpose**: Create a new email campaign
**Transition**: create_campaign (none → draft)

**Request Body**:
```json
{
  "campaignName": "Week of 2024-01-15",
  "catFactId": "fact-123",
  "scheduledDate": "2024-01-22T09:00:00Z",
  "subject": "Your Weekly Cat Fact!",
  "transitionName": "create_campaign"
}
```

**Response** (201 Created):
```json
{
  "id": "campaign-456",
  "campaignName": "Week of 2024-01-15",
  "catFactId": "fact-123",
  "scheduledDate": "2024-01-22T09:00:00Z",
  "state": "draft",
  "totalSubscribers": 150
}
```

#### PUT /api/campaigns/{id}
**Purpose**: Update campaign or trigger state transition
**Transition**: schedule_campaign, start_sending, retry_campaign, cancel_campaign

**Request Body**:
```json
{
  "scheduledDate": "2024-01-22T10:00:00Z",
  "transitionName": "schedule_campaign"
}
```

**Response** (200 OK):
```json
{
  "id": "campaign-456",
  "state": "scheduled",
  "scheduledDate": "2024-01-22T10:00:00Z",
  "totalSubscribers": 152
}
```

#### GET /api/campaigns/{id}
**Purpose**: Get campaign details and statistics
**Transition**: null

**Response** (200 OK):
```json
{
  "id": "campaign-456",
  "campaignName": "Week of 2024-01-15",
  "catFactId": "fact-123",
  "scheduledDate": "2024-01-22T09:00:00Z",
  "sentDate": "2024-01-22T09:05:00Z",
  "state": "completed",
  "totalSubscribers": 150,
  "successfulSends": 148,
  "failedSends": 2,
  "subject": "Your Weekly Cat Fact!"
}
```

#### GET /api/campaigns
**Purpose**: Get campaigns with filtering and pagination
**Transition**: null

**Query Parameters**:
- `state` (optional): Filter by campaign state
- `startDate` (optional): Filter campaigns after date
- `endDate` (optional): Filter campaigns before date
- `page` (optional): Page number
- `size` (optional): Page size

**Response** (200 OK):
```json
{
  "campaigns": [
    {
      "id": "campaign-456",
      "campaignName": "Week of 2024-01-15",
      "scheduledDate": "2024-01-22T09:00:00Z",
      "state": "completed",
      "successfulSends": 148,
      "totalSubscribers": 150
    }
  ],
  "totalCount": 25,
  "page": 0,
  "size": 10
}
```

## 4. ReportingController

**Base Path**: `/api/reports`
**Purpose**: Provides reporting and analytics endpoints.

### Endpoints

#### GET /api/reports/subscribers
**Purpose**: Get subscriber statistics and metrics
**Transition**: null

**Query Parameters**:
- `startDate` (optional): Start date for metrics
- `endDate` (optional): End date for metrics

**Response** (200 OK):
```json
{
  "totalSubscribers": 150,
  "activeSubscribers": 142,
  "pendingSubscribers": 5,
  "unsubscribedSubscribers": 3,
  "newSubscribersThisWeek": 12,
  "unsubscribeRate": 0.02,
  "growthRate": 0.08
}
```

#### GET /api/reports/campaigns
**Purpose**: Get campaign performance metrics
**Transition**: null

**Query Parameters**:
- `startDate` (optional): Start date for metrics
- `endDate` (optional): End date for metrics

**Response** (200 OK):
```json
{
  "totalCampaigns": 25,
  "completedCampaigns": 23,
  "failedCampaigns": 2,
  "averageDeliveryRate": 0.987,
  "totalEmailsSent": 3450,
  "totalInteractions": 1250,
  "engagementRate": 0.362
}
```

#### GET /api/reports/interactions
**Purpose**: Get interaction and engagement metrics
**Transition**: null

**Query Parameters**:
- `type` (optional): Filter by interaction type
- `startDate` (optional): Start date for metrics
- `endDate` (optional): End date for metrics

**Response** (200 OK):
```json
{
  "totalInteractions": 1250,
  "emailOpens": 890,
  "emailClicks": 360,
  "openRate": 0.712,
  "clickRate": 0.288,
  "topPerformingFacts": [
    {
      "factId": "fact-123",
      "factText": "Cats have 32 muscles in each ear.",
      "interactions": 45,
      "engagementScore": 0.85
    }
  ]
}
```

## Error Responses

All controllers return consistent error responses:

**400 Bad Request**:
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid email format",
  "timestamp": "2024-01-15T12:00:00Z"
}
```

**404 Not Found**:
```json
{
  "error": "RESOURCE_NOT_FOUND",
  "message": "Subscriber not found",
  "timestamp": "2024-01-15T12:00:00Z"
}
```

**409 Conflict**:
```json
{
  "error": "BUSINESS_RULE_VIOLATION",
  "message": "Email already exists",
  "timestamp": "2024-01-15T12:00:00Z"
}
```

**500 Internal Server Error**:
```json
{
  "error": "INTERNAL_ERROR",
  "message": "An unexpected error occurred",
  "timestamp": "2024-01-15T12:00:00Z"
}
```
