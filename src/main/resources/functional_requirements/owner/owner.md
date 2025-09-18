# Owner Entity Requirements

## Overview
The Owner entity represents people who can adopt pets from the Purrfect Pets system.

## Attributes
- **firstName**: String - Owner's first name
- **lastName**: String - Owner's last name
- **email**: String - Contact email address
- **phone**: String - Phone number
- **address**: String - Home address
- **experienceWithPets**: String - Previous pet ownership experience
- **housingType**: String - Type of housing (apartment, house, etc.)
- **registrationDate**: LocalDate - Date when owner registered

## Relationships
- Can have multiple Adoption records for different pets
- Internal state managed via entity.meta.state (not in entity schema)

## Business Rules
- Owner must have valid first name, last name, and email
- Email must be unique in the system
- Phone number required for contact
- Address required for pet delivery/pickup
- Housing type helps match suitable pets
