# Entities Requirements

## Overview
This document defines the entities required for the Weekly Cat Fact Subscription application. The application manages subscribers, cat facts, and email campaigns for weekly distribution.

## Entity Definitions

### 1. Subscriber Entity

**Name:** `Subscriber`

**Description:** Represents a user who has subscribed to receive weekly cat facts via email.

**Attributes:**
- `id` (String) - Unique identifier for the subscriber
- `email` (String) - Email address of the subscriber (required, must be valid email format)
- `firstName` (String) - First name of the subscriber (optional)
- `lastName` (String) - Last name of the subscriber (optional)
- `subscriptionDate` (LocalDateTime) - Date and time when the user subscribed
- `isActive` (Boolean) - Whether the subscription is currently active (default: true)
- `preferences` (Map<String, Object>) - User preferences for email delivery (optional)

**Entity State:** The subscriber entity will have the following states managed by the workflow:
- `PENDING` - Initial state when subscription is created but not yet confirmed
- `ACTIVE` - Subscription is confirmed and active
- `INACTIVE` - Subscription is temporarily deactivated
- `UNSUBSCRIBED` - User has permanently unsubscribed

**Relationships:**
- One-to-many with `EmailDelivery` (a subscriber can receive multiple email deliveries)

**Validation Rules:**
- Email must be unique across all subscribers
- Email must be in valid format
- SubscriptionDate cannot be in the future

---

### 2. CatFact Entity

**Name:** `CatFact`

**Description:** Represents a cat fact retrieved from the external API and stored for weekly distribution.

**Attributes:**
- `id` (String) - Unique identifier for the cat fact
- `fact` (String) - The actual cat fact text content (required)
- `length` (Integer) - Length of the fact text in characters
- `retrievedDate` (LocalDateTime) - Date and time when the fact was retrieved from API
- `source` (String) - Source of the fact (default: "catfact.ninja")
- `isUsed` (Boolean) - Whether this fact has been used in an email campaign (default: false)
- `scheduledDate` (LocalDateTime) - Date when this fact is scheduled to be sent (optional)

**Entity State:** The cat fact entity will have the following states managed by the workflow:
- `RETRIEVED` - Initial state when fact is fetched from API
- `SCHEDULED` - Fact is scheduled for a specific week's email campaign
- `SENT` - Fact has been sent to subscribers
- `ARCHIVED` - Fact is archived after being sent

**Relationships:**
- One-to-many with `EmailCampaign` (a cat fact can be used in multiple campaigns if needed)

**Validation Rules:**
- Fact text cannot be empty or null
- Length must match the actual fact text length
- RetrievedDate cannot be in the future

---

### 3. EmailCampaign Entity

**Name:** `EmailCampaign`

**Description:** Represents a weekly email campaign that sends a cat fact to all active subscribers.

**Attributes:**
- `id` (String) - Unique identifier for the email campaign
- `campaignName` (String) - Name/title of the campaign (e.g., "Weekly Cat Facts - Week 1")
- `catFactId` (String) - Reference to the CatFact being sent in this campaign
- `scheduledDate` (LocalDateTime) - Date and time when the campaign is scheduled to run
- `executedDate` (LocalDateTime) - Actual date and time when the campaign was executed (optional)
- `totalSubscribers` (Integer) - Total number of active subscribers at campaign time
- `successfulDeliveries` (Integer) - Number of successful email deliveries (default: 0)
- `failedDeliveries` (Integer) - Number of failed email deliveries (default: 0)
- `campaignType` (String) - Type of campaign (default: "WEEKLY")

**Entity State:** The email campaign entity will have the following states managed by the workflow:
- `CREATED` - Initial state when campaign is created
- `SCHEDULED` - Campaign is scheduled for execution
- `EXECUTING` - Campaign is currently being executed
- `COMPLETED` - Campaign execution completed successfully
- `FAILED` - Campaign execution failed
- `CANCELLED` - Campaign was cancelled before execution

**Relationships:**
- Many-to-one with `CatFact` (multiple campaigns can reference the same cat fact)
- One-to-many with `EmailDelivery` (a campaign generates multiple email deliveries)

**Validation Rules:**
- CatFactId must reference an existing CatFact
- ScheduledDate cannot be in the past
- TotalSubscribers must be non-negative
- SuccessfulDeliveries + FailedDeliveries should not exceed TotalSubscribers

---

### 4. EmailDelivery Entity

**Name:** `EmailDelivery`

**Description:** Represents an individual email delivery attempt to a specific subscriber as part of an email campaign.

**Attributes:**
- `id` (String) - Unique identifier for the email delivery
- `campaignId` (String) - Reference to the EmailCampaign this delivery belongs to
- `subscriberId` (String) - Reference to the Subscriber receiving the email
- `emailAddress` (String) - Email address where the email was sent
- `sentDate` (LocalDateTime) - Date and time when the email was sent (optional)
- `deliveryStatus` (String) - Status of the delivery (PENDING, SENT, DELIVERED, FAILED, BOUNCED)
- `errorMessage` (String) - Error message if delivery failed (optional)
- `openedDate` (LocalDateTime) - Date and time when the email was opened by recipient (optional)
- `clickedDate` (LocalDateTime) - Date and time when recipient clicked a link in the email (optional)

**Entity State:** The email delivery entity will have the following states managed by the workflow:
- `PENDING` - Initial state when delivery is queued
- `SENT` - Email has been sent to the email service
- `DELIVERED` - Email was successfully delivered to recipient's inbox
- `OPENED` - Recipient opened the email
- `CLICKED` - Recipient clicked a link in the email
- `FAILED` - Email delivery failed
- `BOUNCED` - Email bounced back

**Relationships:**
- Many-to-one with `EmailCampaign` (multiple deliveries belong to one campaign)
- Many-to-one with `Subscriber` (multiple deliveries can be sent to one subscriber over time)

**Validation Rules:**
- CampaignId must reference an existing EmailCampaign
- SubscriberId must reference an existing Subscriber
- EmailAddress must be in valid format
- SentDate cannot be in the future
- OpenedDate must be after SentDate if both are present
- ClickedDate must be after OpenedDate if both are present

## Entity Relationships Summary

```
Subscriber (1) ←→ (many) EmailDelivery
CatFact (1) ←→ (many) EmailCampaign
EmailCampaign (1) ←→ (many) EmailDelivery
```

## Notes

- All entities use String IDs which will be generated as UUIDs by the Cyoda platform
- Entity states are managed automatically by the workflow system and should not be included in the entity schema
- All date/time fields use LocalDateTime for consistency
- Boolean fields have appropriate default values specified
- Validation rules will be implemented in the entity's `isValid()` method
