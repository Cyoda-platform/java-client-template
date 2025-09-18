# Pet Entity Requirements

## Overview
The Pet entity represents animals available for adoption or purchase in the Purrfect Pets store.

## Attributes
- **name**: String - Pet's name
- **species**: String - Animal species (dog, cat, bird, etc.)
- **breed**: String - Specific breed within species
- **age**: Integer - Age in months
- **price**: BigDecimal - Sale/adoption price
- **description**: String - Pet description and characteristics
- **imageUrl**: String - URL to pet's photo
- **healthStatus**: String - Current health condition
- **vaccinated**: Boolean - Vaccination status

## Relationships
- Belongs to a Store (storeId reference)
- Can be ordered by an Owner through Order entity

## Business Rules
- Pet name must be unique within a store
- Age must be positive
- Price must be non-negative
- Health status affects availability for adoption

## Notes
Entity state is managed internally via `entity.meta.state` and should not appear in the entity schema.
