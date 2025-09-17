# EmailNotification Entity

## Overview
Manages sending analysis reports via email to a list of subscribers.

## Attributes
- **notificationId**: Unique identifier for the notification
- **analysisId**: Reference to the DataAnalysis entity
- **subscriberEmails**: List of email addresses to send reports to
- **emailSubject**: Subject line for the email
- **emailBody**: Formatted email content with analysis results
- **sentAt**: Timestamp when email was sent
- **status**: Current notification status (mapped to entity.meta.state)

## Relationships
- Depends on DataAnalysis entity for report content
- Final step in the data processing pipeline

## Business Rules
- Email can only be sent after analysis completion
- Must include meaningful analysis results in email body
- All subscriber emails must be valid email addresses
- Email delivery must be confirmed
