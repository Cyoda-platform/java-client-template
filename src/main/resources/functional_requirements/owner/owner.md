# Owner Entity Requirements

## Overview
The Owner entity represents individuals interested in adopting pets from the Purrfect Pets system.

## Attributes
- **ownerId** (String, required): Unique identifier for the owner
- **firstName** (String, required): Owner's first name
- **lastName** (String, required): Owner's last name
- **email** (String, required): Contact email address
- **phone** (String): Contact phone number
- **address** (Address): Home address information
- **dateOfBirth** (LocalDate): Owner's birth date
- **occupation** (String): Current occupation
- **housingType** (String): Apartment/House/Condo
- **hasYard** (Boolean): Whether residence has a yard
- **hasOtherPets** (Boolean): Current pet ownership status
- **petPreferences** (PetPreferences): Preferred pet characteristics
- **registrationDate** (LocalDateTime): When owner registered
- **verificationStatus** (String): Background check status

## Nested Classes
- **Address**: line1, line2, city, state, zipCode, country
- **PetPreferences**: preferredSpecies, preferredSize, preferredAge, maxAdoptionFee

## Relationships
- Can be linked to multiple Adoption entities (adoption history)
- No direct relationship to Pet (linked through Adoption)

## Business Rules
- Owner ID must be unique
- Email must be valid format
- First and last names are required
