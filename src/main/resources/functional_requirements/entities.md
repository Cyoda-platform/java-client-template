# Entities

This document defines the entities for the Weekly Cat Fact Subscription application.

## Entity Overview

The system consists of four main entities:
1. **Subscriber** - Users who subscribe to weekly cat facts
2. **CatFact** - Cat facts retrieved from the external API
3. **EmailCampaign** - Weekly email campaigns sent to subscribers
4. **Interaction** - Tracking user interactions with cat facts

## Entity Definitions

### 1. Subscriber Entity

**Purpose**: Represents users who have subscribed to receive weekly cat facts via email.

**Attributes**:
- `id` (String) - Unique identifier for the subscriber (email address)
- `email` (String) - Email address of the subscriber
- `firstName` (String, optional) - First name of the subscriber
- `lastName` (String, optional) - Last name of the subscriber
- `subscriptionDate` (LocalDateTime) - When the user subscribed
- `isActive` (Boolean) - Whether the subscription is active
- `preferences` (Object, optional) - User preferences for email delivery
  - `preferredDay` (String) - Preferred day of week for emails (default: "MONDAY")
  - `timezone` (String) - User's timezone (default: "UTC")

**Relationships**:
- One-to-Many with Interaction (subscriber can have multiple interactions)

**Business Rules**:
- Email must be unique and valid
- Subscription date is automatically set when created
- Default state is "pending" until email verification

### 2. CatFact Entity

**Purpose**: Represents cat facts retrieved from the external Cat Fact API.

**Attributes**:
- `id` (String) - Unique identifier for the cat fact
- `factText` (String) - The actual cat fact content
- `length` (Integer) - Length of the fact text
- `source` (String) - Source of the fact (default: "catfact.ninja")
- `retrievedDate` (LocalDateTime) - When the fact was retrieved from API
- `usageCount` (Integer) - How many times this fact has been sent
- `lastUsedDate` (LocalDateTime, optional) - When this fact was last sent

**Relationships**:
- One-to-Many with EmailCampaign (fact can be used in multiple campaigns)
- One-to-Many with Interaction (fact can have multiple interactions)

**Business Rules**:
- Fact text cannot be empty
- Usage count starts at 0
- Facts should be unique (no duplicates)

### 3. EmailCampaign Entity

**Purpose**: Represents weekly email campaigns sent to subscribers.

**Attributes**:
- `id` (String) - Unique identifier for the campaign
- `campaignName` (String) - Name of the campaign (e.g., "Week of 2024-01-15")
- `catFactId` (String) - Reference to the cat fact being sent
- `scheduledDate` (LocalDateTime) - When the campaign is scheduled to be sent
- `sentDate` (LocalDateTime, optional) - When the campaign was actually sent
- `totalSubscribers` (Integer) - Number of subscribers at time of sending
- `successfulSends` (Integer) - Number of successful email deliveries
- `failedSends` (Integer) - Number of failed email deliveries
- `subject` (String) - Email subject line
- `emailTemplate` (String) - Template used for the email

**Relationships**:
- Many-to-One with CatFact (campaign uses one cat fact)
- One-to-Many with Interaction (campaign can generate multiple interactions)

**Business Rules**:
- Scheduled date should be in the future when created
- Campaign name should be unique
- Success + failed sends should equal total subscribers

### 4. Interaction Entity

**Purpose**: Tracks user interactions with cat facts for reporting purposes.

**Attributes**:
- `id` (String) - Unique identifier for the interaction
- `subscriberId` (String) - Reference to the subscriber
- `catFactId` (String) - Reference to the cat fact
- `campaignId` (String) - Reference to the email campaign
- `interactionType` (String) - Type of interaction (EMAIL_OPENED, EMAIL_CLICKED, UNSUBSCRIBED)
- `interactionDate` (LocalDateTime) - When the interaction occurred
- `metadata` (Object, optional) - Additional interaction data
  - `userAgent` (String) - Browser/client information
  - `ipAddress` (String) - IP address of the user
  - `clickedLink` (String) - Which link was clicked (if applicable)

**Relationships**:
- Many-to-One with Subscriber (interaction belongs to one subscriber)
- Many-to-One with CatFact (interaction relates to one cat fact)
- Many-to-One with EmailCampaign (interaction relates to one campaign)

**Business Rules**:
- Interaction date cannot be in the future
- Interaction type must be from predefined list
- Each interaction must reference valid subscriber, fact, and campaign

## Entity State Management

**Important Note**: All entities use the internal `entity.meta.state` for workflow state management. The following states are managed automatically by the system:

### Subscriber States
- `pending` - Email verification pending
- `active` - Subscription is active
- `inactive` - Subscription is paused/inactive
- `unsubscribed` - User has unsubscribed

### CatFact States
- `retrieved` - Fact retrieved from API
- `validated` - Fact content validated
- `ready` - Ready to be used in campaigns
- `archived` - Fact is archived (too old or overused)

### EmailCampaign States
- `draft` - Campaign is being prepared
- `scheduled` - Campaign is scheduled for sending
- `sending` - Campaign is currently being sent
- `completed` - Campaign has been sent successfully
- `failed` - Campaign failed to send

### Interaction States
- `recorded` - Interaction has been recorded
- `processed` - Interaction has been processed for reporting

## Entity Relationships Summary

```
Subscriber (1) -----> (M) Interaction
CatFact (1) --------> (M) Interaction
EmailCampaign (1) --> (M) Interaction
CatFact (1) --------> (M) EmailCampaign
```

## Data Validation Rules

1. **Email Validation**: All email addresses must be valid format
2. **Date Validation**: All dates must be valid and logical (e.g., subscription date <= current date)
3. **Reference Integrity**: All foreign key references must exist
4. **Business Logic**: Usage counts, send statistics must be non-negative
5. **Text Length**: Cat facts should have reasonable length limits (10-500 characters)
