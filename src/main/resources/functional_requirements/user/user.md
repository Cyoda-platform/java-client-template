# User Entity

## Overview
A User represents a person who can be assigned tasks and interact with the system.

## Attributes
- **userId** (String, required): Unique identifier for the user
- **name** (String, required): Full name of the user
- **email** (String, required): Email address for communication
- **role** (String, required): User role (ADMIN, MANAGER, DEVELOPER, TESTER)
- **department** (String, optional): Department or team the user belongs to
- **isActive** (Boolean, required): Whether the user account is active
- **createdAt** (LocalDateTime, auto): When the user account was created
- **lastLoginAt** (LocalDateTime, optional): When the user last logged in

## Relationships
- **Tasks**: User can have multiple Tasks assigned via Task.assigneeId
- One User can be assigned to many Tasks

## Validation Rules
- userId must be unique and not null
- name must not be null or empty
- email must be valid email format and unique
- role must be one of: ADMIN, MANAGER, DEVELOPER, TESTER
- isActive defaults to true for new users

## Notes
- User state is managed internally via entity.meta.state
- Account activation/deactivation handled through workflow transitions
- Only active users can be assigned new tasks
