# EmailNotification Controller API

## EmailNotificationController

REST endpoints for managing email notifications and report delivery.

### Endpoints

#### POST /api/emailnotification
Create a new email notification entity.

**Request Example:**
```json
{
  "notificationId": "notification_001",
  "analysisId": "analysis_001",
  "subscriberEmails": [
    "subscriber1@example.com",
    "subscriber2@example.com",
    "admin@company.com"
  ]
}
```

**Response Example:**
```json
{
  "entity": {
    "notificationId": "notification_001",
    "analysisId": "analysis_001",
    "subscriberEmails": [
      "subscriber1@example.com",
      "subscriber2@example.com",
      "admin@company.com"
    ],
    "emailSubject": null,
    "emailBody": null,
    "sentAt": null
  },
  "meta": {
    "uuid": "770e8400-e29b-41d4-a716-446655440002",
    "state": "initial_state",
    "createdAt": "2025-09-17T10:20:00Z"
  }
}
```

#### PUT /api/emailnotification/{uuid}/transition
Trigger state transition for email notification.

**Request Example:**
```json
{
  "transitionName": "begin_send"
}
```

**Response Example:**
```json
{
  "entity": {
    "notificationId": "notification_001",
    "analysisId": "analysis_001",
    "subscriberEmails": [
      "subscriber1@example.com",
      "subscriber2@example.com",
      "admin@company.com"
    ],
    "emailSubject": "London Housing Market Analysis Report - 2025-09-17",
    "emailBody": "London Housing Market Analysis Report\n\nSummary:\n- Total Properties Analyzed: 1500\n- Average Price: £650,000...",
    "sentAt": "2025-09-17T10:25:00Z"
  },
  "meta": {
    "uuid": "770e8400-e29b-41d4-a716-446655440002",
    "state": "sent",
    "updatedAt": "2025-09-17T10:25:00Z"
  }
}
```

#### GET /api/emailnotification/{uuid}
Retrieve email notification by UUID.

**Response Example:**
```json
{
  "entity": {
    "notificationId": "notification_001",
    "analysisId": "analysis_001",
    "subscriberEmails": [
      "subscriber1@example.com",
      "subscriber2@example.com"
    ],
    "emailSubject": "London Housing Market Analysis Report - 2025-09-17",
    "emailBody": "London Housing Market Analysis Report...",
    "sentAt": "2025-09-17T10:25:00Z"
  },
  "meta": {
    "uuid": "770e8400-e29b-41d4-a716-446655440002",
    "state": "sent"
  }
}
```
