# User Entity

## Overview
Represents users in the Research & Clinical Trial Management platform with role-based access control.

## Attributes
- **email**: String - Unique user email address (primary identifier)
- **firstName**: String - User's first name
- **lastName**: String - User's last name
- **role**: String - User role (EXTERNAL_SUBMITTER, REVIEWER, ADMIN)
- **organization**: String - User's affiliated organization
- **isActive**: Boolean - Account activation status
- **registrationDate**: LocalDateTime - Account registration timestamp

## Relationships
- One User can have many Submissions (as submitter)
- One User can review many Submissions (as reviewer)
- User state managed internally via `entity.meta.state`

## Business Rules
- Email must be unique across the system
- Role determines access permissions and available actions
- Only ADMIN users can manage other users' roles and permissions
