# Pet Entity Requirements

## Overview
The Pet entity represents animals available for adoption or sale in the Purrfect Pets store.

## Attributes
- **petId** (String, required): Unique identifier for the pet
- **name** (String, required): Pet's name
- **species** (String, required): Animal species (dog, cat, bird, etc.)
- **breed** (String, optional): Specific breed
- **age** (Integer, required): Age in months
- **color** (String, optional): Pet's color/markings
- **size** (String, optional): Size category (small, medium, large)
- **price** (Double, required): Adoption/sale price
- **description** (String, optional): Detailed description
- **healthStatus** (String, optional): Health condition notes
- **vaccinated** (Boolean, required): Vaccination status
- **neutered** (Boolean, required): Spay/neuter status

## Relationships
- Referenced by Order entity for purchases/adoptions

## Validation Rules
- petId, name, species, age, price, vaccinated, and neutered are required
- age must be positive
- price must be non-negative
