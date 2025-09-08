# Controllers Requirements

## Overview
This document defines the REST API controllers for the Weekly Cat Fact Subscription application. Each entity has its own controller with appropriate CRUD operations and workflow transitions.

## 1. SubscriberController

**Base Path:** `/api/subscribers`

### Endpoints

#### POST /api/subscribers
**Description:** Create a new subscriber subscription  
**Transition:** INITIAL → PENDING (SubscriberRegistrationProcessor)

**Request Body:**
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

**Response (201 Created):**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "subscriptionDate": "2024-01-15T10:30:00",
  "isActive": true,
  "preferences": {
    "emailFormat": "html",
    "frequency": "weekly"
  },
  "meta": {
    "state": "PENDING",
    "version": 1,
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T10:30:00"
  }
}
```

#### PUT /api/subscribers/{id}/activate
**Description:** Activate a pending subscription  
**Transition:** PENDING → ACTIVE (SubscriberActivationProcessor)

**Request Body:**
```json
{
  "confirmationToken": "abc123def456",
  "transitionName": "activate"
}
```

**Response (200 OK):**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "meta": {
    "state": "ACTIVE"
  }
}
```

#### PUT /api/subscribers/{id}/deactivate
**Description:** Temporarily deactivate an active subscription  
**Transition:** ACTIVE → INACTIVE (SubscriberDeactivationProcessor)

**Request Body:**
```json
{
  "reason": "Temporary pause requested by user",
  "transitionName": "deactivate"
}
```

#### PUT /api/subscribers/{id}/reactivate
**Description:** Reactivate an inactive subscription  
**Transition:** INACTIVE → ACTIVE (SubscriberReactivationProcessor)

**Request Body:**
```json
{
  "transitionName": "reactivate"
}
```

#### PUT /api/subscribers/{id}/unsubscribe
**Description:** Permanently unsubscribe  
**Transition:** ANY → UNSUBSCRIBED (SubscriberUnsubscribeProcessor)

**Request Body:**
```json
{
  "reason": "No longer interested",
  "transitionName": "unsubscribe"
}
```

#### GET /api/subscribers/{id}
**Description:** Get subscriber details

#### GET /api/subscribers
**Description:** List all subscribers with filtering
**Query Parameters:** `state`, `isActive`, `page`, `size`

---

## 2. CatFactController

**Base Path:** `/api/catfacts`

### Endpoints

#### POST /api/catfacts/retrieve
**Description:** Manually trigger cat fact retrieval from API  
**Transition:** INITIAL → RETRIEVED (CatFactRetrievalProcessor)

**Request Body:**
```json
{
  "source": "catfact.ninja",
  "transitionName": "retrieve"
}
```

**Response (201 Created):**
```json
{
  "id": "456e7890-e89b-12d3-a456-426614174001",
  "fact": "Cats have 32 muscles in each ear.",
  "length": 32,
  "retrievedDate": "2024-01-15T10:30:00",
  "source": "catfact.ninja",
  "isUsed": false,
  "meta": {
    "state": "RETRIEVED",
    "version": 1
  }
}
```

#### PUT /api/catfacts/{id}/schedule
**Description:** Schedule a cat fact for a campaign  
**Transition:** RETRIEVED → SCHEDULED (CatFactSchedulingProcessor)

**Request Body:**
```json
{
  "scheduledDate": "2024-01-22T09:00:00",
  "transitionName": "schedule"
}
```

#### GET /api/catfacts/{id}
**Description:** Get cat fact details

#### GET /api/catfacts
**Description:** List cat facts with filtering
**Query Parameters:** `state`, `isUsed`, `source`, `page`, `size`

#### GET /api/catfacts/available
**Description:** Get available cat facts for scheduling (RETRIEVED state)

---

## 3. EmailCampaignController

**Base Path:** `/api/campaigns`

### Endpoints

#### POST /api/campaigns
**Description:** Create a new email campaign  
**Transition:** INITIAL → CREATED (EmailCampaignCreationProcessor)

**Request Body:**
```json
{
  "campaignName": "Weekly Cat Facts - Week 3",
  "scheduledDate": "2024-01-22T09:00:00",
  "campaignType": "WEEKLY"
}
```

**Response (201 Created):**
```json
{
  "id": "789e0123-e89b-12d3-a456-426614174002",
  "campaignName": "Weekly Cat Facts - Week 3",
  "scheduledDate": "2024-01-22T09:00:00",
  "campaignType": "WEEKLY",
  "totalSubscribers": 0,
  "successfulDeliveries": 0,
  "failedDeliveries": 0,
  "meta": {
    "state": "CREATED",
    "version": 1
  }
}
```

#### PUT /api/campaigns/{id}/schedule
**Description:** Schedule campaign with cat fact  
**Transition:** CREATED → SCHEDULED (EmailCampaignSchedulingProcessor)

**Request Body:**
```json
{
  "catFactId": "456e7890-e89b-12d3-a456-426614174001",
  "transitionName": "schedule"
}
```

#### PUT /api/campaigns/{id}/execute
**Description:** Manually execute a scheduled campaign  
**Transition:** SCHEDULED → EXECUTING (EmailCampaignExecutionProcessor)

**Request Body:**
```json
{
  "transitionName": "execute"
}
```

#### PUT /api/campaigns/{id}/cancel
**Description:** Cancel a campaign  
**Transition:** CREATED/SCHEDULED → CANCELLED (EmailCampaignCancellationProcessor)

**Request Body:**
```json
{
  "reason": "Campaign no longer needed",
  "transitionName": "cancel"
}
```

#### GET /api/campaigns/{id}
**Description:** Get campaign details with statistics

#### GET /api/campaigns
**Description:** List campaigns with filtering
**Query Parameters:** `state`, `campaignType`, `scheduledDate`, `page`, `size`

#### GET /api/campaigns/{id}/deliveries
**Description:** Get delivery details for a campaign

---

## 4. EmailDeliveryController

**Base Path:** `/api/deliveries`

### Endpoints

#### GET /api/deliveries/{id}
**Description:** Get delivery details

**Response (200 OK):**
```json
{
  "id": "abc1234-e89b-12d3-a456-426614174003",
  "campaignId": "789e0123-e89b-12d3-a456-426614174002",
  "subscriberId": "123e4567-e89b-12d3-a456-426614174000",
  "emailAddress": "user@example.com",
  "sentDate": "2024-01-22T09:05:00",
  "deliveryStatus": "DELIVERED",
  "openedDate": "2024-01-22T10:15:00",
  "meta": {
    "state": "OPENED",
    "version": 3
  }
}
```

#### GET /api/deliveries
**Description:** List deliveries with filtering
**Query Parameters:** `campaignId`, `subscriberId`, `deliveryStatus`, `state`, `page`, `size`

#### PUT /api/deliveries/{id}/track-open
**Description:** Track email open event  
**Transition:** DELIVERED → OPENED (EmailDeliveryOpenProcessor)

**Request Body:**
```json
{
  "openedDate": "2024-01-22T10:15:00",
  "userAgent": "Mozilla/5.0...",
  "transitionName": "track-open"
}
```

#### PUT /api/deliveries/{id}/track-click
**Description:** Track email click event  
**Transition:** OPENED → CLICKED (EmailDeliveryClickProcessor)

**Request Body:**
```json
{
  "clickedDate": "2024-01-22T10:20:00",
  "clickedUrl": "https://example.com/unsubscribe",
  "transitionName": "track-click"
}
```

#### PUT /api/deliveries/{id}/mark-bounced
**Description:** Mark delivery as bounced  
**Transition:** SENT → BOUNCED (EmailDeliveryBounceProcessor)

**Request Body:**
```json
{
  "bounceReason": "Mailbox full",
  "bounceType": "soft",
  "transitionName": "mark-bounced"
}
```

---

## 5. ReportingController

**Base Path:** `/api/reports`

### Endpoints

#### GET /api/reports/subscribers
**Description:** Get subscriber statistics

**Response (200 OK):**
```json
{
  "totalSubscribers": 1250,
  "activeSubscribers": 1100,
  "inactiveSubscribers": 50,
  "unsubscribed": 100,
  "newSubscriptionsThisWeek": 25,
  "unsubscriptionsThisWeek": 5
}
```

#### GET /api/reports/campaigns
**Description:** Get campaign performance statistics

**Query Parameters:** `startDate`, `endDate`, `campaignType`

**Response (200 OK):**
```json
{
  "totalCampaigns": 52,
  "completedCampaigns": 50,
  "failedCampaigns": 2,
  "averageDeliveryRate": 95.5,
  "averageOpenRate": 25.3,
  "averageClickRate": 3.2,
  "totalEmailsSent": 57500
}
```

#### GET /api/reports/engagement
**Description:** Get engagement metrics

**Response (200 OK):**
```json
{
  "totalDeliveries": 57500,
  "successfulDeliveries": 54925,
  "failedDeliveries": 2575,
  "emailsOpened": 14550,
  "emailsClicked": 1840,
  "bounceRate": 4.5,
  "openRate": 25.3,
  "clickRate": 3.2
}
```

## Common Response Patterns

### Error Responses

**400 Bad Request:**
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid email format",
  "details": {
    "field": "email",
    "value": "invalid-email"
  }
}
```

**404 Not Found:**
```json
{
  "error": "ENTITY_NOT_FOUND",
  "message": "Subscriber not found",
  "entityId": "123e4567-e89b-12d3-a456-426614174000"
}
```

**409 Conflict:**
```json
{
  "error": "INVALID_STATE_TRANSITION",
  "message": "Cannot activate subscriber in UNSUBSCRIBED state",
  "currentState": "UNSUBSCRIBED",
  "requestedTransition": "activate"
}
```

### Pagination Response

```json
{
  "content": [...],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 1250,
    "totalPages": 63
  }
}
```

## Security and Validation

1. **Input Validation**: All request bodies validated for required fields and formats
2. **Email Validation**: Email addresses validated for format and domain existence
3. **State Validation**: Transition requests validated against current entity state
4. **Rate Limiting**: API endpoints protected against abuse
5. **Authentication**: All endpoints require valid authentication (implementation dependent)
6. **CORS**: Appropriate CORS headers for web client access

## Notes

- All timestamps use ISO 8601 format with timezone
- Entity IDs are UUIDs
- Pagination uses zero-based page numbering
- All update endpoints with transitions include `transitionName` parameter
- Transition names must match exactly with workflow definitions
- Response includes entity metadata with current state and version information
