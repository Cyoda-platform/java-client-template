# Pet Entity Requirements

## Overview
The Pet entity represents animals available for adoption or sale in the Purrfect Pets system.

## Attributes
- **petId** (String, required): Unique identifier for the pet
- **name** (String, required): Pet's name
- **species** (String, required): Animal species (dog, cat, bird, etc.)
- **breed** (String): Specific breed information
- **age** (Integer): Age in years
- **weight** (Double): Weight in pounds
- **color** (String): Primary color/markings
- **description** (String): Detailed description of the pet
- **price** (Double): Adoption/sale price
- **isVaccinated** (Boolean): Vaccination status
- **isNeutered** (Boolean): Spay/neuter status
- **specialNeeds** (String): Any special care requirements

## Relationships
- Can be associated with multiple Orders (one-to-many)
- Tracked through workflow states for availability management

## Validation Rules
- petId must be unique and non-null
- name and species are required fields
- price must be positive if specified
- age must be non-negative if specified
