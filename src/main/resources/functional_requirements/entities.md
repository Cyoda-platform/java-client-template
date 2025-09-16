# Entities

## Overview
This document defines the entities required for the Weekly Cat Fact Subscription application. The system manages subscribers, cat facts, and email campaigns to deliver weekly cat facts to users.

## Entity Definitions

### 1. Subscriber

**Description**: Represents a user who has subscribed to receive weekly cat fact emails.

**Attributes**:
- `id` (Long): Unique identifier for the subscriber
- `email` (String): Email address of the subscriber (required, unique)
- `firstName` (String): First name of the subscriber (optional)
- `lastName` (String): Last name of the subscriber (optional)
- `subscriptionDate` (LocalDateTime): Date and time when the user subscribed
- `isActive` (Boolean): Whether the subscription is active (default: true)
- `unsubscribeToken` (String): Unique token for unsubscribing (UUID)
- `preferences` (Object): Email preferences (frequency, format, etc.)

**Entity State**: 
- The system will manage the subscriber state through workflow transitions
- States represent the lifecycle of a subscription (pending, active, unsubscribed, etc.)
- Use `entity.meta.state` to get the current state

**Relationships**:
- One-to-many with EmailCampaign (a subscriber can receive multiple email campaigns)

### 2. CatFact

**Description**: Represents a cat fact retrieved from the Cat Fact API or stored in the system.

**Attributes**:
- `id` (Long): Unique identifier for the cat fact
- `factText` (String): The actual cat fact content (required)
- `source` (String): Source of the fact (e.g., "catfact.ninja")
- `retrievedDate` (LocalDateTime): Date and time when the fact was retrieved
- `isUsed` (Boolean): Whether this fact has been used in an email campaign (default: false)
- `length` (Integer): Length of the fact text
- `category` (String): Category of the cat fact (optional)

**Entity State**:
- The system will manage the cat fact state through workflow transitions
- States represent the lifecycle of a fact (retrieved, validated, used, etc.)
- Use `entity.meta.state` to get the current state

**Relationships**:
- One-to-many with EmailCampaign (a cat fact can be used in multiple campaigns)

### 3. EmailCampaign

**Description**: Represents an email campaign that sends cat facts to subscribers.

**Attributes**:
- `id` (Long): Unique identifier for the email campaign
- `campaignName` (String): Name of the campaign (e.g., "Weekly Cat Facts - Week 1")
- `catFactId` (Long): Reference to the cat fact being sent
- `scheduledDate` (LocalDateTime): When the campaign is scheduled to be sent
- `sentDate` (LocalDateTime): When the campaign was actually sent (null if not sent)
- `totalSubscribers` (Integer): Number of subscribers at the time of sending
- `successfulDeliveries` (Integer): Number of successful email deliveries
- `failedDeliveries` (Integer): Number of failed email deliveries
- `openCount` (Integer): Number of emails opened (if tracking is enabled)
- `clickCount` (Integer): Number of links clicked (if tracking is enabled)
- `unsubscribeCount` (Integer): Number of unsubscribes triggered by this campaign

**Entity State**:
- The system will manage the email campaign state through workflow transitions
- States represent the campaign lifecycle (scheduled, sending, sent, completed, failed)
- Use `entity.meta.state` to get the current state

**Relationships**:
- Many-to-one with CatFact (multiple campaigns can use the same fact)
- Many-to-many with Subscriber (a campaign is sent to multiple subscribers)

## Entity Relationships Summary

```
Subscriber (1) ←→ (M) EmailCampaign (M) ←→ (1) CatFact
```

- **Subscriber to EmailCampaign**: One-to-many (a subscriber receives multiple campaigns)
- **CatFact to EmailCampaign**: One-to-many (a cat fact can be used in multiple campaigns)
- **EmailCampaign**: Acts as a junction entity connecting subscribers and cat facts

## Notes

1. **Entity State Management**: All entities use the workflow state management system. The `state` field is managed automatically by the workflow engine and should not be included in the entity schema.

2. **Unique Constraints**: 
   - Subscriber email must be unique
   - Unsubscribe tokens must be unique

3. **Data Validation**:
   - Email addresses must be valid format
   - Required fields must not be null or empty
   - Dates must be valid and logical (e.g., sentDate >= scheduledDate)

4. **Soft Deletes**: Consider using the `isActive` flag for subscribers instead of hard deletes to maintain historical data.

5. **Audit Trail**: The workflow system provides automatic tracking of state changes and timestamps through the meta fields.
