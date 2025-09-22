# Subscriber Entity

## Overview
Represents an email subscriber who receives analysis reports. Manages subscription preferences and delivery status.

## Attributes
- **subscriberId** (String, required): Unique identifier for the subscriber
- **email** (String, required): Email address of the subscriber
- **name** (String, optional): Full name of the subscriber
- **subscribedAt** (LocalDateTime, optional): Timestamp when subscription was created
- **lastEmailSent** (LocalDateTime, optional): Timestamp of last email sent
- **emailPreferences** (EmailPreferences, optional): Delivery preferences
- **isActive** (Boolean, optional): Whether subscription is active

## Nested Classes
### EmailPreferences
- **frequency** (String): Email frequency (IMMEDIATE, DAILY, WEEKLY)
- **format** (String): Preferred email format (HTML, TEXT)
- **topics** (List<String>): Subscribed topics or data source types

## Relationships
- Subscribers receive Reports via email
- Subscriber state is managed internally via entity.meta.state

## Validation Rules
- subscriberId must not be null
- email must be a valid email address format
