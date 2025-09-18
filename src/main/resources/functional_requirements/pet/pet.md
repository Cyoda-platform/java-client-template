# Pet Entity Requirements

## Overview
The Pet entity represents animals available for adoption in the Purrfect Pets system.

## Attributes
- **name**: String - Pet's name
- **species**: String - Animal species (dog, cat, bird, etc.)
- **breed**: String - Pet breed
- **age**: Integer - Age in years
- **description**: String - Pet description and personality
- **medicalHistory**: String - Medical background and health status
- **adoptionFee**: Double - Fee required for adoption
- **arrivalDate**: LocalDate - Date when pet arrived at shelter

## Relationships
- Can be adopted by one Owner through an Adoption record
- Internal state managed via entity.meta.state (not in entity schema)

## Business Rules
- Pet must have valid name, species, and breed
- Age must be positive
- Adoption fee must be non-negative
- Medical history required for transparency
