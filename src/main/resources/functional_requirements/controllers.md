# Controllers

## Overview
This document defines the REST API controllers for the Weekly Cat Fact Subscription application. Each entity has its own controller with CRUD operations and workflow transition endpoints.

## 1. SubscriberController

**Base Path**: `/api/subscribers`

### Endpoints

#### POST /api/subscribers
**Description**: Create a new subscriber (triggers subscribe transition)
**Transition**: subscribe (none → pending_verification)

**Request Body**:
```json
{
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "preferences": {
    "frequency": "weekly",
    "format": "html"
  }
}
```

**Response**:
```json
{
  "id": 1,
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "subscriptionDate": "2024-01-15T10:30:00Z",
  "isActive": true,
  "unsubscribeToken": "550e8400-e29b-41d4-a716-446655440000",
  "state": "pending_verification"
}
```

#### GET /api/subscribers/{id}
**Description**: Get subscriber by ID

**Response**:
```json
{
  "id": 1,
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "subscriptionDate": "2024-01-15T10:30:00Z",
  "isActive": true,
  "state": "active"
}
```

#### PUT /api/subscribers/{id}
**Description**: Update subscriber information
**Transition**: null (no state change)

**Request Body**:
```json
{
  "firstName": "Jane",
  "lastName": "Smith",
  "preferences": {
    "frequency": "weekly",
    "format": "text"
  }
}
```

#### POST /api/subscribers/{id}/verify
**Description**: Verify subscriber email
**Transition**: verify_email (pending_verification → active)

**Request Body**:
```json
{
  "verificationToken": "abc123def456"
}
```

#### POST /api/subscribers/{id}/unsubscribe
**Description**: Unsubscribe a subscriber
**Transition**: unsubscribe (active → unsubscribed)

**Request Body**:
```json
{
  "unsubscribeToken": "550e8400-e29b-41d4-a716-446655440000",
  "reason": "No longer interested"
}
```

#### POST /api/subscribers/{id}/reactivate
**Description**: Reactivate suspended subscriber
**Transition**: reactivate (suspended → active)

**Request Body**:
```json
{
  "reactivationReason": "Email issues resolved"
}
```

#### GET /api/subscribers
**Description**: Get all subscribers with optional filtering

**Query Parameters**:
- `state`: Filter by state (active, pending_verification, unsubscribed, suspended)
- `page`: Page number (default: 0)
- `size`: Page size (default: 20)

**Response**:
```json
{
  "content": [
    {
      "id": 1,
      "email": "user@example.com",
      "state": "active"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

## 2. CatFactController

**Base Path**: `/api/catfacts`

### Endpoints

#### POST /api/catfacts/retrieve
**Description**: Manually trigger cat fact retrieval
**Transition**: retrieve (none → retrieved)

**Request Body**:
```json
{
  "source": "catfact.ninja"
}
```

**Response**:
```json
{
  "id": 1,
  "factText": "Cats have 32 muscles in each ear.",
  "source": "catfact.ninja",
  "retrievedDate": "2024-01-15T10:30:00Z",
  "length": 32,
  "isUsed": false,
  "state": "retrieved"
}
```

#### GET /api/catfacts/{id}
**Description**: Get cat fact by ID

**Response**:
```json
{
  "id": 1,
  "factText": "Cats have 32 muscles in each ear.",
  "source": "catfact.ninja",
  "retrievedDate": "2024-01-15T10:30:00Z",
  "length": 32,
  "isUsed": false,
  "state": "ready"
}
```

#### POST /api/catfacts/{id}/validate
**Description**: Manually validate a cat fact
**Transition**: validate (retrieved → validated)

**Request Body**: `{}`

#### POST /api/catfacts/{id}/approve
**Description**: Approve a validated cat fact
**Transition**: approve (validated → ready)

**Request Body**: `{}`

#### POST /api/catfacts/{id}/archive
**Description**: Archive a used cat fact
**Transition**: archive (used → archived)

**Request Body**:
```json
{
  "archiveReason": "Overused"
}
```

#### GET /api/catfacts
**Description**: Get all cat facts with optional filtering

**Query Parameters**:
- `state`: Filter by state (retrieved, validated, ready, used, archived)
- `isUsed`: Filter by usage status (true/false)
- `page`: Page number (default: 0)
- `size`: Page size (default: 20)

**Response**:
```json
{
  "content": [
    {
      "id": 1,
      "factText": "Cats have 32 muscles in each ear.",
      "state": "ready",
      "isUsed": false
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

#### GET /api/catfacts/random
**Description**: Get a random ready cat fact

**Response**:
```json
{
  "id": 5,
  "factText": "A cat's purr vibrates at a frequency of 25 to 50 hertz.",
  "state": "ready"
}
```

## 3. EmailCampaignController

**Base Path**: `/api/campaigns`

### Endpoints

#### POST /api/campaigns
**Description**: Create and schedule a new email campaign
**Transition**: schedule (none → scheduled)

**Request Body**:
```json
{
  "campaignName": "Weekly Cat Facts - Week 5",
  "catFactId": 1,
  "scheduledDate": "2024-01-22T09:00:00Z"
}
```

**Response**:
```json
{
  "id": 1,
  "campaignName": "Weekly Cat Facts - Week 5",
  "catFactId": 1,
  "scheduledDate": "2024-01-22T09:00:00Z",
  "totalSubscribers": 150,
  "state": "scheduled"
}
```

#### GET /api/campaigns/{id}
**Description**: Get campaign by ID

**Response**:
```json
{
  "id": 1,
  "campaignName": "Weekly Cat Facts - Week 5",
  "catFactId": 1,
  "scheduledDate": "2024-01-22T09:00:00Z",
  "sentDate": "2024-01-22T09:15:00Z",
  "totalSubscribers": 150,
  "successfulDeliveries": 148,
  "failedDeliveries": 2,
  "openCount": 89,
  "clickCount": 12,
  "unsubscribeCount": 1,
  "state": "completed"
}
```

#### POST /api/campaigns/{id}/cancel
**Description**: Cancel a scheduled campaign
**Transition**: cancel (scheduled → cancelled)

**Request Body**:
```json
{
  "cancellationReason": "Content needs revision"
}
```

#### POST /api/campaigns/{id}/retry
**Description**: Retry a failed campaign
**Transition**: retry (failed → preparing)

**Request Body**: `{}`

#### GET /api/campaigns
**Description**: Get all campaigns with optional filtering

**Query Parameters**:
- `state`: Filter by state (scheduled, preparing, sending, sent, completed, failed, cancelled)
- `startDate`: Filter campaigns after this date
- `endDate`: Filter campaigns before this date
- `page`: Page number (default: 0)
- `size`: Page size (default: 20)

**Response**:
```json
{
  "content": [
    {
      "id": 1,
      "campaignName": "Weekly Cat Facts - Week 5",
      "scheduledDate": "2024-01-22T09:00:00Z",
      "state": "completed",
      "successfulDeliveries": 148,
      "totalSubscribers": 150
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

#### GET /api/campaigns/{id}/analytics
**Description**: Get detailed analytics for a campaign

**Response**:
```json
{
  "campaignId": 1,
  "campaignName": "Weekly Cat Facts - Week 5",
  "sentDate": "2024-01-22T09:15:00Z",
  "metrics": {
    "totalSubscribers": 150,
    "successfulDeliveries": 148,
    "failedDeliveries": 2,
    "deliveryRate": 98.67,
    "openCount": 89,
    "openRate": 60.14,
    "clickCount": 12,
    "clickRate": 8.11,
    "unsubscribeCount": 1,
    "unsubscribeRate": 0.68
  },
  "catFact": {
    "id": 1,
    "factText": "Cats have 32 muscles in each ear."
  }
}
```

## 4. ReportsController

**Base Path**: `/api/reports`

### Endpoints

#### GET /api/reports/dashboard
**Description**: Get dashboard summary statistics

**Response**:
```json
{
  "totalSubscribers": 1250,
  "activeSubscribers": 1180,
  "pendingVerification": 45,
  "suspendedSubscribers": 15,
  "unsubscribedSubscribers": 10,
  "totalCatFacts": 85,
  "readyCatFacts": 12,
  "usedCatFacts": 68,
  "totalCampaigns": 24,
  "completedCampaigns": 22,
  "scheduledCampaigns": 1,
  "failedCampaigns": 1,
  "averageOpenRate": 62.5,
  "averageClickRate": 8.3,
  "averageUnsubscribeRate": 0.8
}
```

#### GET /api/reports/subscriber-growth
**Description**: Get subscriber growth over time

**Query Parameters**:
- `period`: Time period (daily, weekly, monthly)
- `startDate`: Start date for the report
- `endDate`: End date for the report

**Response**:
```json
{
  "period": "weekly",
  "data": [
    {
      "date": "2024-01-01",
      "newSubscribers": 25,
      "unsubscribers": 3,
      "netGrowth": 22,
      "totalSubscribers": 1180
    },
    {
      "date": "2024-01-08",
      "newSubscribers": 30,
      "unsubscribers": 2,
      "netGrowth": 28,
      "totalSubscribers": 1208
    }
  ]
}
```

#### GET /api/reports/campaign-performance
**Description**: Get campaign performance metrics

**Query Parameters**:
- `startDate`: Start date for the report
- `endDate`: End date for the report

**Response**:
```json
{
  "campaigns": [
    {
      "id": 1,
      "campaignName": "Weekly Cat Facts - Week 5",
      "sentDate": "2024-01-22T09:15:00Z",
      "deliveryRate": 98.67,
      "openRate": 60.14,
      "clickRate": 8.11,
      "unsubscribeRate": 0.68
    }
  ],
  "averages": {
    "deliveryRate": 97.8,
    "openRate": 62.5,
    "clickRate": 8.3,
    "unsubscribeRate": 0.8
  }
}
```

## Error Responses

All endpoints return consistent error responses:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Email address is required",
    "timestamp": "2024-01-15T10:30:00Z",
    "path": "/api/subscribers"
  }
}
```

## Common HTTP Status Codes

- `200 OK`: Successful GET, PUT requests
- `201 Created`: Successful POST requests
- `400 Bad Request`: Invalid request data
- `404 Not Found`: Resource not found
- `409 Conflict`: Business rule violation (e.g., duplicate email)
- `422 Unprocessable Entity`: Workflow transition not allowed
- `500 Internal Server Error`: Server error

## Notes

1. **Authentication**: All endpoints should require appropriate authentication/authorization (not detailed here).

2. **Rate Limiting**: Consider implementing rate limiting for public endpoints.

3. **Validation**: All request bodies should be validated according to entity constraints.

4. **Pagination**: List endpoints support pagination with standard parameters.

5. **Filtering**: List endpoints support filtering by entity state and other relevant fields.

6. **Workflow Transitions**: Endpoints that trigger transitions include the transition name in the operation.
