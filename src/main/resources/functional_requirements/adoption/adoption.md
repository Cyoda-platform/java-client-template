# Adoption Entity Requirements

## Overview
The Adoption entity represents the adoption process linking pets and owners with adoption details.

## Attributes
- **petId**: String - UUID of the adopted pet
- **ownerId**: String - UUID of the adopting owner
- **applicationDate**: LocalDate - Date when adoption application was submitted
- **adoptionDate**: LocalDate - Date when adoption was completed
- **adoptionFee**: Double - Fee paid for adoption
- **notes**: String - Additional notes about the adoption
- **returnDate**: LocalDate - Date when pet was returned (if applicable)
- **returnReason**: String - Reason for return (if applicable)

## Relationships
- Links one Pet to one Owner
- Internal state managed via entity.meta.state (not in entity schema)

## Business Rules
- Must have valid petId and ownerId references
- Application date required when created
- Adoption fee must match pet's adoption fee
- Return date only set if pet is returned
- Notes provide additional context for adoption process
