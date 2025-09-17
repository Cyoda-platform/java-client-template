# Adoption Entity Requirements

## Entity: Adoption

**Purpose**: Manages the adoption process between pets and potential owners in the Purrfect Pets system.

**Attributes**:
- `adoptionId` (String, required): Unique identifier for the adoption
- `petId` (String, required): Reference to the pet being adopted
- `ownerId` (String, required): Reference to the potential owner
- `applicationDate` (LocalDateTime, required): When adoption application was submitted
- `approvalDate` (LocalDateTime, optional): When adoption was approved
- `completionDate` (LocalDateTime, optional): When adoption was finalized
- `notes` (String, optional): Additional notes about the adoption process
- `fee` (Double, optional): Adoption fee amount

**Relationships**:
- References one Pet entity (petId)
- References one Owner entity (ownerId)
- Links pets and owners through adoption workflow

**Business Rules**:
- Adoption must have valid adoptionId, petId, ownerId, and applicationDate
- Pet must be available for adoption when application is created
- Owner must be verified before adoption can be approved
- Only one active adoption per pet at a time
