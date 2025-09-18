# Owner Entity Requirements

## Overview
The Owner entity represents customers who can adopt or purchase pets from the store.

## Attributes
- **firstName**: String - Owner's first name
- **lastName**: String - Owner's last name
- **email**: String - Contact email address
- **phone**: String - Contact phone number
- **address**: String - Home address
- **dateOfBirth**: LocalDate - Owner's birth date
- **verified**: Boolean - Account verification status

## Relationships
- Can place multiple Orders for pets
- Associated with Store for customer management

## Business Rules
- Email must be unique across all owners
- Phone number must be valid format
- Must be 18+ years old to adopt pets
- Verification required for high-value adoptions

## Notes
Entity state is managed internally via `entity.meta.state` and should not appear in the entity schema.
