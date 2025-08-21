### 1. Entity Definitions
```
Pet:
- id: String (business id assigned by shelter)
- name: String (pet name)
- species: String (dog, cat, etc.)
- breed: String (breed description)
- age: Integer (years or months)
- sex: String (male/female/unknown)
- color: String (visual description)
- photos: Array(String) (urls to photos)
- status: String (current lifecycle state)
- description: String (notes about temperament/health)
- tags: Array(String) (search tags)
- createdAt: String (ISO timestamp)
- updatedAt: String (ISO timestamp)

Owner:
- id: String (business id for owner)
- fullName: String (owner name)
- email: String (contact email)
- phone: String (contact phone)
- address: String (mailing address)
- preferences: Array(String) (species/breed/age preferences)
- savedPets: Array(String) (pet ids saved)
- verificationStatus: String (verification state)
- createdAt: String (ISO timestamp)
- updatedAt: String (ISO timestamp)

AdoptionOrder:
- id: String (business id for order)
- petId: String (linked pet id)
- ownerId: String (linked owner id)
- requestedAt: String (ISO timestamp)
- status: String (order lifecycle state)
- notes: String (applicant notes)
- fees: Number (adoption fees)
- processedAt: String (ISO timestamp)
```

Notes: You did not specify more entities, so I used the default of 3 entities.

### 2. Entity workflows

Pet workflow:
1. Initial State: Pet created -> status = Created (automatic)
2. Validation: Validate required fields and photos (automatic)
3. Tagging: Auto-assign tags based on breed/species (automatic)
4. Listing: status -> Available (automatic) after validation
5. Reservation: Owner creates AdoptionOrder -> status -> Reserved (automatic)
6. Under Review: Shelter reviews reservation (manual)
7. Adoption Complete: Shelter approves -> status -> Adopted (manual)
8. Removal: Shelter marks pet Removed (manual)
```mermaid
stateDiagram-v2
    [*] --> "CREATED"
    "CREATED" --> "VALIDATING" : "ValidatePetProcessor, automatic"
    "VALIDATING" --> "TAGGING" : "PetValidationCriterion"
    "TAGGING" --> "AVAILABLE" : "AssignTagsProcessor, automatic"
    "AVAILABLE" --> "RESERVED" : "ReservePetProcessor, automatic"
    "RESERVED" --> "UNDER_REVIEW" : "NotifyShelterProcessor, manual"
    "UNDER_REVIEW" --> "ADOPTED" : "ApproveAdoptionProcessor, manual"
    "UNDER_REVIEW" --> "AVAILABLE" : "RejectReservationProcessor, manual"
    "ADOPTED" --> "REMOVED" : "ArchivePetProcessor, automatic"
    "REMOVED" --> [*]
```
Processors: ValidatePetProcessor, AssignTagsProcessor, ReservePetProcessor, NotifyShelterProcessor, ApproveAdoptionProcessor, ArchivePetProcessor  
Criteria: PetHasRequiredFieldsCriterion, PhotosQualityCriterion

Owner workflow:
1. Initial State: Owner created -> status = Registered (automatic)
2. Verification: Send verification email -> verification pending (automatic)
3. Verified: Owner verifies -> verificationStatus -> Verified (manual/automatic)
4. Eligibility Check: On adoption request, check eligibility (automatic)
5. Suspension/Deletion: Admin can suspend or delete owner (manual)
```mermaid
stateDiagram-v2
    [*] --> "REGISTERED"
    "REGISTERED" --> "VERIFICATION_PENDING" : "SendVerificationProcessor, automatic"
    "VERIFICATION_PENDING" --> "VERIFIED" : "OwnerVerifiedCriterion"
    "VERIFIED" --> "ELIGIBILITY_CHECK" : "OwnerEligibilityProcessor, automatic"
    "ELIGIBILITY_CHECK" --> "SUSPENDED" : "FlagOwnerProcessor, manual"
    "SUSPENDED" --> "DELETED" : "DeleteOwnerProcessor, manual"
    "VERIFIED" --> [*]
```
Processors: SendVerificationProcessor, OwnerEligibilityProcessor, FlagOwnerProcessor, DeleteOwnerProcessor  
Criteria: OwnerContactVerifiedCriterion, OwnerBackgroundCriterion

AdoptionOrder workflow:
1. Initial State: Order created -> status = Initiated (automatic)
2. Validation: Check pet availability and owner eligibility (automatic)
3. Pending Approval: Move to PendingApproval (automatic) if checks pass
4. Approval: Shelter approves (manual) -> Approved
5. Finalization: After approval, finalize payment/process fees and complete adoption -> Completed (automatic)
6. Rejection/Cancel: Shelter rejects or owner cancels -> Rejected/Cancelled (manual)
```mermaid
stateDiagram-v2
    [*] --> "INITIATED"
    "INITIATED" --> "VALIDATING" : "ValidateOrderProcessor, automatic"
    "VALIDATING" --> "PENDING_APPROVAL" : "OrderValidCriterion"
    "PENDING_APPROVAL" --> "APPROVED" : "ApproveAdoptionProcessor, manual"
    "APPROVED" --> "FINALIZING" : "FinalizeAdoptionProcessor, automatic"
    "FINALIZING" --> "COMPLETED" : "NotifyStakeholdersProcessor"
    "PENDING_APPROVAL" --> "REJECTED" : "RejectAdoptionProcessor, manual"
    "INITIATED" --> "CANCELLED" : "CancelOrderProcessor, manual"
    "COMPLETED" --> [*]
```
Processors: ValidateOrderProcessor, ApproveAdoptionProcessor, FinalizeAdoptionProcessor, NotifyStakeholdersProcessor, RejectAdoptionProcessor  
Criteria: PetAvailableCriterion, OwnerEligibleCriterion, OrderValidCriterion

### 3. Pseudo code for processor classes

ValidatePetProcessor:
```
class ValidatePetProcessor {
  process(pet) {
    if not PetHasRequiredFieldsCriterion.check(pet) then
      pet.status = "VALIDATION_FAILED"
      emit event PetValidationFailed
      return
    end
    pet.status = "VALIDATED"
    persist(pet)
  }
}
```

AssignTagsProcessor:
```
class AssignTagsProcessor {
  process(pet) {
    tags = deriveTags(pet.species, pet.breed, pet.description)
    pet.tags = mergeUnique(pet.tags, tags)
    persist(pet)
  }
}
```

ReservePetProcessor:
```
class ReservePetProcessor {
  process(pet, adoptionOrder) {
    if PetAvailableCriterion.check(pet) then
      pet.status = "RESERVED"
      persist(pet)
      emit event PetReserved
    else
      emit event PetReserveFailed
    end
  }
}
```

ValidateOrderProcessor:
```
class ValidateOrderProcessor {
  process(order) {
    if not PetAvailableCriterion.check(order.petId) then
      order.status = "REJECTED"
      persist(order)
      return
    end
    if not OwnerEligibleCriterion.check(order.ownerId) then
      order.status = "PENDING_APPROVAL"
      persist(order)
      emit event OwnerNeedsReview
      return
    end
    order.status = "PENDING_APPROVAL"
    persist(order)
  }
}
```

FinalizeAdoptionProcessor:
```
class FinalizeAdoptionProcessor {
  process(order) {
    processFees(order.fees)
    updatePetStatus(order.petId, "ADOPTED")
    updateOwnerRecords(order.ownerId, order.petId)
    order.status = "COMPLETED"
    persist(order)
    emit event AdoptionCompleted
  }
}
```

Criteria pseudo:
- PetHasRequiredFieldsCriterion.check(pet) -> verifies name, species, photos present
- PetAvailableCriterion.check(petId) -> verifies pet.status == AVAILABLE
- OwnerEligibleCriterion.check(ownerId) -> checks verificationStatus and background rules

### 4. API Endpoints Design Rules

General rules:
- Every POST that creates an entity triggers Cyoda processing workflows.
- POST responses MUST return only technicalId field.
- GET by technicalId returns the stored entity (including business id fields).
- No extra POST endpoints for business entities are required beyond creation; business processing happens in workflows.

Endpoints and request/response examples:

1) Create Pet
POST /pets
Request:
```json
{
  "id": "PET-123",
  "name": "Mittens",
  "species": "Cat",
  "breed": "Tabby",
  "age": 2,
  "sex": "Female",
  "color": "Brown",
  "photos": ["https://.../1.jpg"],
  "description": "Friendly indoor cat",
  "tags": []
}
```
Response (must contain only technicalId):
```json
{
  "technicalId": "tech-pet-0001"
}
```

GET pet by technicalId
GET /pets/{technicalId}
Response:
```json
{
  "technicalId": "tech-pet-0001",
  "id": "PET-123",
  "name": "Mittens",
  "species": "Cat",
  "breed": "Tabby",
  "age": 2,
  "sex": "Female",
  "color": "Brown",
  "photos": ["https://.../1.jpg"],
  "status": "AVAILABLE",
  "description": "Friendly indoor cat",
  "tags": ["indoor","tabby"],
  "createdAt": "2025-08-20T12:00:00Z",
  "updatedAt": "2025-08-20T12:05:00Z"
}
```

2) Create Owner
POST /owners
Request:
```json
{
  "id": "OWN-55",
  "fullName": "Alex Doe",
  "email": "alex@example.com",
  "phone": "+1-555-0000",
  "address": "123 Main St",
  "preferences": ["Cat"]
}
```
Response:
```json
{
  "technicalId": "tech-owner-0001"
}
```

GET owner by technicalId
GET /owners/{technicalId}
Response:
```json
{
  "technicalId": "tech-owner-0001",
  "id": "OWN-55",
  "fullName": "Alex Doe",
  "email": "alex@example.com",
  "phone": "+1-555-0000",
  "address": "123 Main St",
  "verificationStatus": "VERIFICATION_PENDING",
  "createdAt": "2025-08-20T12:01:00Z",
  "updatedAt": "2025-08-20T12:01:00Z"
}
```

3) Create AdoptionOrder
POST /adoptions
Request:
```json
{
  "id": "ORD-900",
  "petId": "PET-123",
  "ownerId": "OWN-55",
  "requestedAt": "2025-08-20T12:10:00Z",
  "notes": "Has fenced yard",
  "fees": 50.0
}
```
Response:
```json
{
  "technicalId": "tech-adopt-0001"
}
```

GET adoption by technicalId
GET /adoptions/{technicalId}
Response:
```json
{
  "technicalId": "tech-adopt-0001",
  "id": "ORD-900",
  "petId": "PET-123",
  "ownerId": "OWN-55",
  "requestedAt": "2025-08-20T12:10:00Z",
  "status": "PENDING_APPROVAL",
  "notes": "Has fenced yard",
  "fees": 50.0,
  "processedAt": null
}
```

If you want, I can:
- Expand to include ShelterStaff, Payments, or VetAppointments (up to 10 entities).
- Add GET by condition endpoints (e.g., search pets by species/breed).
Which additional entities or search endpoints should I add next?