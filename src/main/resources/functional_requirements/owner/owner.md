# Owner Entity Requirements

## Entity: Owner

**Purpose**: Represents potential or current pet owners in the Purrfect Pets system.

**Attributes**:
- `ownerId` (String, required): Unique identifier for the owner
- `firstName` (String, required): Owner's first name
- `lastName` (String, required): Owner's last name
- `email` (String, required): Contact email address
- `phone` (String, required): Contact phone number
- `address` (OwnerAddress, required): Home address information
- `experience` (String, optional): Previous pet ownership experience
- `preferences` (OwnerPreferences, optional): Pet preferences for matching

**Nested Classes**:
- `OwnerAddress`: street, city, state, zipCode, country
- `OwnerPreferences`: preferredSpecies, preferredSize, preferredAge

**Relationships**:
- Can have multiple Adoption entities (adoption history)
- Associated with pets through adoption process

**Business Rules**:
- Owner must have valid ownerId, firstName, lastName, email, phone, and address
- Email must be unique in the system
- Phone number must be valid format
