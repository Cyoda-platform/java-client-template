# Adoption Entity Requirements

## Overview
The Adoption entity represents the adoption process linking pets with potential owners in the Purrfect Pets system.

## Attributes
- **adoptionId** (String, required): Unique identifier for the adoption
- **petId** (String, required): Reference to the pet being adopted
- **ownerId** (String, required): Reference to the potential owner
- **applicationDate** (LocalDateTime, required): When application was submitted
- **applicationNotes** (String): Additional notes from applicant
- **staffNotes** (String): Internal staff notes
- **homeVisitRequired** (Boolean): Whether home visit is needed
- **homeVisitDate** (LocalDateTime): Scheduled home visit date
- **homeVisitPassed** (Boolean): Home visit result
- **adoptionFee** (Double): Final adoption fee
- **adoptionDate** (LocalDateTime): When adoption was completed
- **contractSigned** (Boolean): Whether adoption contract is signed
- **followUpDate** (LocalDateTime): Scheduled follow-up date
- **returnReason** (String): Reason if pet was returned

## Relationships
- References Pet entity via petId
- References Owner entity via ownerId
- Links pets and owners through the adoption process

## Business Rules
- Adoption ID must be unique
- Pet ID and Owner ID are required
- Application date is mandatory
- Only one active adoption per pet at a time
