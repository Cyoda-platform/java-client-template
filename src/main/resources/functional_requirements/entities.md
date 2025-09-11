# Entity Requirements

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
The Mail entity uses the internal `entity.meta.state` to track its workflow state. The state is managed automatically by the workflow system and represents the current stage of the mail processing lifecycle.

**Note**: The `isHappy` field is a business attribute that determines the content type, while the entity state tracks the processing workflow stage.

### Relationships
This entity is standalone and does not have relationships with other entities in the current system.

### Validation Rules
1. **isHappy**: Must be a boolean value (true or false)
2. **mailList**: 
   - Must not be null or empty
   - Each email address must be in valid email format (e.g., user@domain.com)
   - Duplicate email addresses are allowed (business decision)

### Business Rules
1. The mail content and processing logic will be determined by the `isHappy` attribute
2. All emails in the `mailList` will receive the same type of mail (happy or gloomy)
3. The entity state will track the mail processing workflow from creation to delivery

### Example Entity Structure
```json
{
  "isHappy": true,
  "mailList": [
    "user1@example.com",
    "user2@example.com",
    "user3@example.com"
  ]
}
```
