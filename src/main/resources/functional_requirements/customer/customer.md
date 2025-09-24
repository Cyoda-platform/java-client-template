# Customer Entity Requirements

## Overview
The Customer entity represents people who can adopt or purchase pets from the Purrfect Pets system.

## Attributes
- **customerId** (String, required): Unique identifier for the customer
- **firstName** (String, required): Customer's first name
- **lastName** (String, required): Customer's last name
- **email** (String, required): Email address for communication
- **phone** (String): Contact phone number
- **address** (Address): Home address information
- **preferredSpecies** (List<String>): Preferred pet species
- **experienceLevel** (String): Pet ownership experience (beginner, intermediate, expert)
- **housingType** (String): Type of housing (apartment, house, farm)
- **hasYard** (Boolean): Whether customer has a yard
- **otherPets** (Boolean): Whether customer has other pets

## Nested Classes
### Address
- **street** (String): Street address
- **city** (String): City name
- **state** (String): State/province
- **zipCode** (String): Postal code
- **country** (String): Country

## Relationships
- Can have multiple Orders (one-to-many)
- Managed through workflow states for verification and approval

## Validation Rules
- customerId must be unique and non-null
- firstName, lastName, and email are required
- email must be valid format
