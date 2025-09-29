# Customer Entity Requirements

## Overview
The Customer entity represents people who can adopt or purchase pets from the Purrfect Pets store.

## Attributes
- **customerId** (String, required): Unique identifier for the customer
- **firstName** (String, required): Customer's first name
- **lastName** (String, required): Customer's last name
- **email** (String, required): Email address
- **phone** (String, optional): Phone number
- **address** (Address, optional): Customer's address
- **dateOfBirth** (LocalDate, optional): Date of birth
- **preferredPetType** (String, optional): Preferred pet species
- **adoptionHistory** (List<String>, optional): List of previously adopted pet IDs

## Nested Classes
### Address
- **street** (String): Street address
- **city** (String): City
- **state** (String): State/province
- **zipCode** (String): Postal code
- **country** (String): Country

## Relationships
- Referenced by Order entity for purchases/adoptions

## Validation Rules
- customerId, firstName, lastName, and email are required
- email must be valid format
