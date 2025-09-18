# Pet Entity Requirements

## Overview
The Pet entity represents animals available for adoption or purchase in the Purrfect Pets store.

## Attributes
- **name**: String - Pet's name
- **species**: String - Animal species (Dog, Cat, Bird, etc.)
- **breed**: String - Pet breed
- **age**: Integer - Pet age in months
- **description**: String - Pet description and characteristics
- **price**: BigDecimal - Adoption/purchase price
- **imageUrl**: String - URL to pet's photo

## Relationships
- **ownerId**: String - Reference to Owner entity (nullable, set when adopted)
- **orderId**: String - Reference to Order entity (nullable, set when order is placed)

## Notes
- Pet state is managed internally via `entity.meta.state` (Available, Reserved, Adopted)
- Price must be positive value
- Age must be positive integer
