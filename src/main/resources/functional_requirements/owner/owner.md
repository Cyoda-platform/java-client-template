# Owner Entity Requirements

## Overview
The Owner entity represents customers who can adopt or purchase pets from the Purrfect Pets store.

## Attributes
- **firstName**: String - Owner's first name
- **lastName**: String - Owner's last name
- **email**: String - Owner's email address (unique)
- **phone**: String - Owner's phone number
- **address**: String - Owner's address
- **dateOfBirth**: LocalDate - Owner's date of birth

## Relationships
- **petIds**: List<String> - References to owned Pet entities
- **orderIds**: List<String> - References to Order entities

## Notes
- Owner state is managed internally via `entity.meta.state` (Registered, Verified, Active)
- Email must be unique and valid format
- Must be 18+ years old to adopt pets
