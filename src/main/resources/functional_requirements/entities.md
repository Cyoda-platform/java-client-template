# Entities

## Mail Entity

### Description
The Mail entity represents an email message that can be sent to a list of recipients. The system determines whether the mail is happy or gloomy based on its content and sends it accordingly.

### Entity Name
`Mail`

### Attributes

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| isHappy | Boolean | Yes | Indicates whether the mail content is happy (true) or gloomy (false) |
| mailList | List<String> | Yes | List of email addresses to send the mail to |

### Notes
- The entity state is managed internally by the workflow system and is not part of the entity schema
- The `isHappy` field determines which processor will be used to send the mail
- The `mailList` contains the recipient email addresses for the mail

### Relationships
- No direct relationships with other entities in this system
- The Mail entity is a standalone entity that represents a single mail sending operation

### Entity State Management
- Entity state is accessed via `entity.meta.state` in processor code
- Entity state cannot be modified directly - it's managed automatically by the workflow
- The workflow will transition the entity through different states based on the defined transitions
