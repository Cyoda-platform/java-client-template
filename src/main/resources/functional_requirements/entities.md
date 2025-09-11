# Entities Requirements

## Mail Entity

### Overview
The Mail entity represents an email message that can be either happy or gloomy in nature. It contains information about the email's mood and the list of recipients.

### Entity Name
`Mail`

### Attributes

| Attribute | Type | Description | Required | Validation |
|-----------|------|-------------|----------|------------|
| isHappy | Boolean | Indicates whether the mail content is happy (true) or gloomy (false) | Yes | Not null |
| mailList | List<String> | List of email addresses to send the mail to | Yes | Not null, not empty, valid email format |

### Entity State
The entity state is managed internally by the workflow system and represents the current state of the mail in the processing workflow. The state will be automatically updated based on workflow transitions.

**Note**: The `isHappy` field is a business attribute that determines the content type of the mail, while the entity state (accessible via `entity.meta.state`) tracks the workflow progression.

### Relationships
This entity is standalone and does not have relationships with other entities in the current system.

### Validation Rules
1. `isHappy` field must not be null
2. `mailList` must not be null or empty
3. Each email address in `mailList` must be in valid email format
4. The entity is considered valid when both fields meet their validation criteria

### Business Logic
- When `isHappy` is true, the mail should contain happy/positive content
- When `isHappy` is false, the mail should contain gloomy/sad content
- The `mailList` determines the recipients for the email distribution
