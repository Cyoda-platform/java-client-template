# Pet Entity Requirements

## Overview
The Pet entity represents animals available for adoption in the Purrfect Pets system.

## Attributes
- **petId** (String, required): Unique identifier for the pet
- **name** (String, required): Pet's name
- **species** (String, required): Type of animal (dog, cat, bird, etc.)
- **breed** (String): Specific breed information
- **age** (Integer): Age in years
- **gender** (String): Male/Female/Unknown
- **size** (String): Small/Medium/Large
- **color** (String): Primary color description
- **description** (String): Detailed description of the pet
- **healthStatus** (String): Current health condition
- **vaccinated** (Boolean): Vaccination status
- **spayedNeutered** (Boolean): Spay/neuter status
- **specialNeeds** (String): Any special care requirements
- **arrivalDate** (LocalDateTime): When pet arrived at shelter
- **adoptionFee** (Double): Fee for adoption

## Relationships
- Can be linked to multiple Adoption entities (adoption history)
- No direct relationship to Owner (linked through Adoption)

## Business Rules
- Pet ID must be unique
- Name is required for identification
- Species is mandatory for categorization
- Age should be non-negative
