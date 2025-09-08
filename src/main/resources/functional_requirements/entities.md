# Entities Specification

## Overview
This document defines the entities required for the Weekly Cat Fact Subscription application. The system manages subscribers, cat facts, and email campaigns to deliver weekly cat facts to users.

## Entity Definitions

### 1. Subscriber Entity

**Purpose**: Represents users who have subscribed to receive weekly cat facts via email.

**Attributes**:
- `email` (String, required): The subscriber's email address (unique identifier)
- `firstName` (String, optional): The subscriber's first name for personalization
- `lastName` (String, optional): The subscriber's last name for personalization
- `subscriptionDate` (LocalDateTime, required): When the user subscribed
- `isActive` (Boolean, required): Whether the subscription is active (default: true)
- `preferences` (Map<String, Object>, optional): User preferences for email delivery
- `lastEmailSent` (LocalDateTime, optional): Timestamp of the last email sent to this subscriber
- `totalEmailsReceived` (Integer, required): Count of emails received (default: 0)
- `unsubscribeToken` (String, required): Unique token for unsubscribe functionality

**Entity State**: The system will manage the subscriber state automatically through workflow transitions:
- `PENDING` - Initial state when user signs up
- `ACTIVE` - Confirmed and receiving emails
- `UNSUBSCRIBED` - User has unsubscribed
- `BOUNCED` - Email delivery failed

**Relationships**:
- One-to-many with EmailCampaign (a subscriber can receive multiple email campaigns)

**Validation Rules**:
- Email must be valid format and unique
- Subscription date cannot be in the future
- Unsubscribe token must be unique

### 2. CatFact Entity

**Purpose**: Represents cat facts retrieved from the Cat Fact API and stored for email campaigns.

**Attributes**:
- `factId` (String, required): Unique identifier for the cat fact
- `text` (String, required): The actual cat fact content
- `length` (Integer, required): Length of the fact text
- `retrievedDate` (LocalDateTime, required): When the fact was retrieved from the API
- `source` (String, required): Source of the fact (default: "catfact.ninja")
- `isUsed` (Boolean, required): Whether this fact has been used in an email campaign (default: false)
- `usageCount` (Integer, required): Number of times this fact has been used (default: 0)
- `lastUsedDate` (LocalDateTime, optional): When this fact was last used in a campaign

**Entity State**: The system will manage the cat fact state automatically:
- `RETRIEVED` - Initial state when fact is fetched from API
- `READY` - Fact is ready to be used in campaigns
- `USED` - Fact has been used in at least one campaign
- `ARCHIVED` - Fact is archived and no longer used

**Relationships**:
- One-to-many with EmailCampaign (a cat fact can be used in multiple campaigns)

**Validation Rules**:
- Text cannot be empty or null
- Length must match the actual text length
- Retrieved date cannot be in the future

### 3. EmailCampaign Entity

**Purpose**: Represents a weekly email campaign that sends cat facts to subscribers.

**Attributes**:
- `campaignId` (String, required): Unique identifier for the campaign
- `campaignName` (String, required): Descriptive name for the campaign
- `catFactId` (String, required): Reference to the CatFact used in this campaign
- `scheduledDate` (LocalDateTime, required): When the campaign is scheduled to be sent
- `actualSentDate` (LocalDateTime, optional): When the campaign was actually sent
- `totalSubscribers` (Integer, required): Number of subscribers at the time of sending
- `successfulDeliveries` (Integer, required): Number of successful email deliveries (default: 0)
- `failedDeliveries` (Integer, required): Number of failed email deliveries (default: 0)
- `bounces` (Integer, required): Number of email bounces (default: 0)
- `unsubscribes` (Integer, required): Number of unsubscribes triggered by this campaign (default: 0)
- `opens` (Integer, required): Number of email opens (default: 0)
- `clicks` (Integer, required): Number of clicks in the email (default: 0)
- `emailSubject` (String, required): Subject line of the email
- `emailTemplate` (String, required): Template used for the email

**Entity State**: The system will manage the email campaign state automatically:
- `SCHEDULED` - Campaign is scheduled but not yet sent
- `SENDING` - Campaign is currently being sent
- `SENT` - Campaign has been successfully sent
- `FAILED` - Campaign failed to send
- `CANCELLED` - Campaign was cancelled before sending

**Relationships**:
- Many-to-one with CatFact (multiple campaigns can use the same cat fact)
- Many-to-many with Subscriber (a campaign is sent to multiple subscribers)

**Validation Rules**:
- Scheduled date cannot be in the past
- Cat fact ID must reference an existing CatFact
- Email subject cannot be empty
- Total subscribers must be non-negative

## Entity Relationships Summary

```
Subscriber (1) ←→ (M) EmailCampaign (M) ←→ (1) CatFact
```

- **Subscriber to EmailCampaign**: One subscriber can receive multiple email campaigns
- **EmailCampaign to CatFact**: Multiple campaigns can use the same cat fact (for different weeks or retries)
- **Subscriber to CatFact**: Indirect relationship through EmailCampaign

## Key Design Decisions

1. **State Management**: All entities use the built-in entity.meta.state for workflow state management instead of custom status fields.

2. **Email Tracking**: EmailCampaign entity includes comprehensive tracking metrics for reporting purposes.

3. **Cat Fact Reusability**: Cat facts can be reused across multiple campaigns, with usage tracking.

4. **Subscriber Management**: Subscribers have detailed tracking of email interactions and preferences.

5. **Unique Identifiers**: Each entity has appropriate unique identifiers for data integrity.

6. **Audit Trail**: All entities include timestamps for creation and modification tracking.
