# Entities Requirements

## EggAlarm Entity

### Description
The EggAlarm entity represents an egg cooking alarm that users can create to time their egg cooking process. It manages the cooking type selection and alarm timing functionality.

### Entity Name
`EggAlarm`

### Attributes

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| id | String | Yes | Unique identifier for the egg alarm |
| eggType | String | Yes | Type of egg cooking: "SOFT_BOILED", "MEDIUM_BOILED", or "HARD_BOILED" |
| cookingTimeMinutes | Integer | Yes | Cooking time in minutes based on egg type |
| createdAt | LocalDateTime | Yes | Timestamp when the alarm was created |
| startedAt | LocalDateTime | No | Timestamp when the alarm was started |
| completedAt | LocalDateTime | No | Timestamp when the alarm completed |
| userId | String | No | Optional user identifier for multi-user support |

### Entity State Management
The entity state is managed internally by the workflow system and represents the current status of the egg alarm:
- **CREATED** - Alarm has been created but not started
- **ACTIVE** - Alarm is currently running/timing
- **COMPLETED** - Alarm has finished and egg is ready
- **CANCELLED** - Alarm was cancelled before completion

**Note**: The entity state is accessed via `entity.meta.state` in processor code and cannot be directly modified. The workflow system manages state transitions automatically.

### Cooking Time Mapping
- **SOFT_BOILED**: 4 minutes
- **MEDIUM_BOILED**: 6 minutes  
- **HARD_BOILED**: 8 minutes

### Validation Rules
- `eggType` must be one of: "SOFT_BOILED", "MEDIUM_BOILED", "HARD_BOILED"
- `cookingTimeMinutes` must be positive integer
- `id` must be non-null and non-empty
- `createdAt` must be non-null

### Relationships
This entity is standalone and does not have direct relationships with other entities in the current scope. Future enhancements could include:
- User entity for multi-user support
- AlarmHistory entity for tracking past alarms
- NotificationSettings entity for customizing alarm notifications

### Model Key
The model key for this entity will be "EggAlarm" which is used by the Cyoda platform for entity identification and workflow management.
