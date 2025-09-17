# Pet Entity Requirements

## Entity: Pet

**Purpose**: Represents individual pets available for adoption in the Purrfect Pets system.

**Attributes**:
- `petId` (String, required): Unique identifier for the pet
- `name` (String, required): Pet's name
- `species` (String, required): Type of animal (dog, cat, bird, etc.)
- `breed` (String, optional): Specific breed of the pet
- `age` (Integer, required): Age in years
- `color` (String, optional): Pet's color/markings
- `size` (String, optional): Size category (small, medium, large)
- `healthStatus` (String, required): Current health condition
- `description` (String, optional): Additional details about the pet
- `arrivalDate` (LocalDateTime, required): When pet arrived at the shelter

**Relationships**:
- Can be associated with one Owner through Adoption entity
- Referenced by Adoption entities for tracking adoption process

**Business Rules**:
- Pet must have valid petId, name, species, age, and healthStatus
- Age must be positive integer
- Health status indicates if pet is ready for adoption
