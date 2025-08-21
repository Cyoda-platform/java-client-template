### 1. Entity Definitions
```
Pet:
- id: String (business id)
- name: String (pet name)
- species: String (dog/cat/etc)
- breed: String
- age: Number (years or months)
- gender: String
- photos: List<String> (urls)
- description: String
- status: String (available/reserved/adopted/unavailable)
- tags: List<String>
- shelterLocation: String
- medicalRecords: List<String>
- createdAt: String (timestamp)
- updatedAt: String (timestamp)

User:
- id: String (business id)
- name: String
- email: String
- phone: String
- role: String (customer/admin/vet)
- favorites: List<String> (pet ids)
- adoptionHistory: List<String> (order ids)
- address: String
- createdAt: String (timestamp)

AdoptionOrder:
- id: String (business id)
- petId: String
- userId: String
- requestedDate: String (timestamp)
- status: String (requested/approved/paid/scheduled/completed/cancelled/returned)
- fee: Number
- paymentConfirmed: Boolean
- meetAndGreetAt: String (timestamp)
- notes: String
- createdAt: String (timestamp)
```

### 2. Entity workflows

Pet workflow:
1. Initial State: Pet created (event) -> VALIDATION automatic
2. Validation: basic fields and required photos
3. Moderation: PhotoModerationProcessor; medical check
4. Indexing: update search/catalog
5. Availability: if cleared -> AVAILABLE; if issues -> UNAVAILABLE
6. Admin actions: manual status changes (reserve/adopt/return)
```mermaid
stateDiagram-v2
    [*] --> "CREATED"
    "CREATED" --> "VALIDATION" : PetValidationProcessor, automatic
    "VALIDATION" --> "MODERATION" : PhotoModerationProcessor, automatic
    "MODERATION" --> "INDEXED" : IndexUpdateProcessor, automatic
    "INDEXED" --> "AVAILABLE" : MedicalEligibilityCriterion
    "INDEXED" --> "UNAVAILABLE" : MedicalEligibilityCriterion
    "AVAILABLE" --> "RESERVED" : AdminReserveAction, manual
    "RESERVED" --> "ADOPTED" : AdminCompleteAdoption, manual
    "ADOPTED" --> "RETURNED" : AdminProcessReturn, manual
    "UNAVAILABLE" --> "REVIEW" : AdminReviewAction, manual
    "REVIEW" --> "AVAILABLE" : AdminApprove, manual
    "REVIEW" --> "UNAVAILABLE" : AdminReject, manual
    "AVAILABLE" --> [*]
    "ADOPTED" --> [*]
```
Processors: PetValidationProcessor, PhotoModerationProcessor, IndexUpdateProcessor, NotifySubscribersProcessor, MedicalEligibilityProcessor.
Criteria: PhotosApprovedCriterion, MedicalClearanceCriterion.
Example pseudocode (PetValidationProcessor):
```
process(pet):
  ensure required fields present
  mark pet.validationPassed = true or false
  emit outcome to next state
```

User workflow:
1. Initial State: User created (event)
2. Verification: send verification email, wait for verification (manual user action)
3. Profile completion: prompt for missing info
4. Active: ready to perform orders
```mermaid
stateDiagram-v2
    [*] --> "CREATED"
    "CREATED" --> "VERIFICATION" : SendVerificationProcessor, automatic
    "VERIFICATION" --> "PROFILE_COMPLETE" : EmailVerifiedCriterion
    "PROFILE_COMPLETE" --> "ACTIVE" : CompleteProfileProcessor, automatic
    "ACTIVE" --> "SUSPENDED" : AdminSuspendAction, manual
    "SUSPENDED" --> "ACTIVE" : AdminReinstateAction, manual
    "ACTIVE" --> [*]
```
Processors: SendVerificationProcessor, UserValidationProcessor, CompleteProfileProcessor, AccessGrantProcessor.
Criteria: EmailVerifiedCriterion, ProfileCompleteCriterion.
Example pseudocode (SendVerificationProcessor):
```
process(user):
  send verification email
  set user.verificationSentAt
```

AdoptionOrder workflow:
1. Initial State: Order created (event)
2. Validation: check pet availability and user eligibility
3. Approval: auto if simple rules or manual by admin
4. Payment: wait for paymentConfirmed
5. Fulfillment: schedule meet & greet, complete adoption
6. Closed: completed/cancelled/returned
```mermaid
stateDiagram-v2
    [*] --> "REQUESTED"
    "REQUESTED" --> "VALIDATING" : OrderValidationProcessor, automatic
    "VALIDATING" --> "APPROVAL_PENDING" : AvailabilityCheckProcessor, automatic
    "APPROVAL_PENDING" --> "APPROVED" : ApprovalProcessor, manual
    "APPROVAL_PENDING" --> "REJECTED" : ApprovalProcessor, manual
    "APPROVED" --> "AWAITING_PAYMENT" : PaymentProcessor, automatic
    "AWAITING_PAYMENT" --> "SCHEDULED" : PaymentCompletedCriterion
    "SCHEDULED" --> "COMPLETED" : FulfillmentProcessor, manual
    "COMPLETED" --> [*]
    "REJECTED" --> "CANCELLED" : automatic
    "CANCELLED" --> [*]
```
Processors: OrderValidationProcessor, AvailabilityCheckProcessor, ApprovalProcessor, PaymentProcessor, FulfillmentProcessor, NotificationProcessor.
Criteria: PetAvailableCriterion, UserEligibleCriterion, PaymentCompletedCriterion.
Example pseudocode (OrderValidationProcessor):
```
process(order):
  load pet and user
  if pet.status != available then fail
  if user has pending flags then mark for manual review
```

### 3. Pseudo code for processor classes (concise)

Pet processors:
```
class PhotoModerationProcessor:
  process(pet):
    for url in pet.photos:
      result = moderate(url)
      if result.block then pet.photosFlagged = true
    emit next

class IndexUpdateProcessor:
  process(pet):
    update search index with pet fields
    emit next
```

Order processors:
```
class AvailabilityCheckProcessor:
  process(order):
    pet = loadPet(order.petId)
    if pet.status == available then mark pass else mark fail

class PaymentProcessor:
  process(order):
    if order.fee == 0 then mark paymentConfirmed
    else wait for payment event and mark paymentConfirmed
```

User processors:
```
class CompleteProfileProcessor:
  process(user):
    if required fields present then user.profileComplete = true
```

Additional examples referenced earlier:
```
class PetValidationProcessor:
  process(pet):
    ensure required fields present
    mark pet.validationPassed = true or false
    emit outcome to next state
```
```
class OrderValidationProcessor:
  process(order):
    load pet and user
    if pet.status != available then fail
    if user has pending flags then mark for manual review
```

### 4. API Endpoints Design Rules
- POST endpoints return only technicalId (string). Each POST triggers Cyoda to start workflow for that entity.
- GET by technicalId for each entity returns stored entity.

Pet create:
POST /pets
Request:
```json
{
  "id":"pet-001",
  "name":"Mittens",
  "species":"cat",
  "breed":"Tabby",
  "age":2,
  "gender":"female",
  "photos":["https://..."]
}
```
Response:
```json
{
  "technicalId":"tch-pet-0001"
}
```
GET /pets/{technicalId} response:
```json
{ "technicalId":"tch-pet-0001", "entity": { ...Pet fields... } }
```

User create:
POST /users
Request/response same pattern (returns technicalId). GET /users/{technicalId} returns user.

AdoptionOrder create:
POST /orders
Request:
```json
{
  "id":"order-001",
  "petId":"pet-001",
  "userId":"user-001",
  "requestedDate":"2025-08-21T10:00:00Z",
  "fee":50
}
```
Response:
```json
{ "technicalId":"tch-order-0001" }
```
GET /orders/{technicalId} returns order entity.

Notes:
- Each POST is an event: Cyoda will start the entity workflow automatically (validation, processors, criteria).
- GET by non-technical fields not included (only implement if requested).