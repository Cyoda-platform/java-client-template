# Task Entity

## Overview
A Task represents a unit of work that needs to be completed within the system.

## Attributes
- **taskId** (String, required): Unique identifier for the task
- **title** (String, required): Brief description of the task
- **description** (String, optional): Detailed description of what needs to be done
- **priority** (String, required): Task priority level (HIGH, MEDIUM, LOW)
- **assigneeId** (String, optional): ID of the user assigned to this task
- **dueDate** (LocalDateTime, optional): When the task should be completed
- **estimatedHours** (Integer, optional): Estimated time to complete in hours
- **actualHours** (Integer, optional): Actual time spent on the task
- **createdAt** (LocalDateTime, auto): When the task was created
- **updatedAt** (LocalDateTime, auto): When the task was last modified

## Relationships
- **Assignee**: References User entity via assigneeId
- Task can be assigned to zero or one User
- User can have multiple Tasks assigned

## Validation Rules
- taskId must be unique and not null
- title must not be null or empty
- priority must be one of: HIGH, MEDIUM, LOW
- estimatedHours and actualHours must be positive if provided
- dueDate must be in the future when creating new tasks

## Notes
- Task state is managed internally via entity.meta.state
- Assignment and progress tracking handled through workflow transitions
