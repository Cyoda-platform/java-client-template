# Entities

## Mail Entity

### Description
The Mail entity represents an email message that can be either happy or gloomy in nature. The system will process these mails and send them to appropriate recipients based on their emotional tone.

### Attributes

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| isHappy | Boolean | Yes | Indicates whether the mail content is happy (true) or gloomy (false) |
| mailList | List<String> | Yes | List of email addresses to send the mail to |

### Entity State
- **Internal State Management**: The entity state is managed internally by the workflow system
- **State Access**: Use `entity.meta.state` to get the current state in processor code
- **State Modification**: Cannot be directly modified - managed automatically by workflow transitions
- **Note**: Since the user requirement doesn't specify a status field, the internal state will track the mail processing workflow stages

### Relationships
- **No direct relationships**: This entity operates independently
- **Implicit relationships**: May interact with external email services through processors

### Business Rules
1. **isHappy field**: Must be explicitly set to determine mail processing path
2. **mailList field**: Must contain at least one valid email address
3. **Email validation**: All email addresses in mailList should follow standard email format
4. **Processing path**: The value of isHappy determines which processor (sendHappyMail or sendGloomyMail) will be used

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

### Notes
- The entity does not include a status or state field in its schema as this is managed internally
- The workflow will automatically transition the entity through different states based on processing results
- Processors will access the current state via `entity.meta.state` if needed for business logic
