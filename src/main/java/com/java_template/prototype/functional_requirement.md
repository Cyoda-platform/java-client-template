### 1. Entity Definitions
```
Pet:
- id: String (business id from source, optional)
- name: String (pet name)
- species: String (dog/cat/etc)
- breed: String (breed)
- age: Number (years)
- status: String (workflow-driven status)
- tags: String[] (labels)
- photos: String[] (urls)
- source: String (petstore/original source)
- importedAt: String (timestamp)

AdoptionRequest:
- applicantName: String
- applicantContact: String
- petId: String (references Pet.id)
- message: String
- submittedAt: String
- status: String (workflow-driven status)
- adminNotes: String

PetImportJob:
- requestedBy: String
- sourceUrl: String
- filter: String (species/breed)
- startedAt: String
- finishedAt: String
- status: String
- resultSummary: Object
```

### 2. Entity workflows

Pet workflow:
1. Initial State: Pet created (CREATED) when persisted
2. Enrichment: Auto call to enrich/normalize fields (automatic)
3. Validation: Auto validate required fields (automatic)
4. Available: Move to AVAILABLE if valid
5. Adoption Hold: Move to PENDING_ADOPTION when request received (manual/automatic)
6. Adopted: Manual approval completes adoption (manual)
7. Archived: Automated archival (automatic)

```mermaid
stateDiagram-v2
    [*] --> "CREATED"
    "CREATED" --> "ENRICHING" : EnrichFromPetstoreProcessor, automatic
    "ENRICHING" --> "VALIDATING" : TransformPetDataProcessor, automatic
    "VALIDATING" --> "AVAILABLE" : IsValidPetCriterion
    "AVAILABLE" --> "PENDING_ADOPTION" : AdoptionRequested, automatic
    "PENDING_ADOPTION" --> "ADOPTED" : ApproveAdoptionProcessor, manual
    "AVAILABLE" --> "ARCHIVED" : ArchiveProcessor, automatic
    "ADOPTED" --> "ARCHIVED" : ArchiveProcessor, automatic
    "ARCHIVED" --> [*]
```

Processors: EnrichFromPetstoreProcessor, TransformPetDataProcessor, ArchiveProcessor, NotifyAdminsProcessor  
Criteria: IsValidPetCriterion, AdoptionPendingCriterion

AdoptionRequest workflow:
1. CREATED when user submits
2. REVIEW: Manual review by admin (manual)
3. BACKGROUND_CHECK: Automatic background check (automatic)
4. APPROVED -> PAYMENT_PENDING (automatic/manual) -> COMPLETED on payment  
5. REJECTED or COMPLETED end states

```mermaid
stateDiagram-v2
    [*] --> "CREATED"
    "CREATED" --> "UNDER_REVIEW" : ValidateApplicantProcessor, automatic
    "UNDER_REVIEW" --> "BACKGROUND_CHECK" : StartBackgroundCheckProcessor, automatic
    "BACKGROUND_CHECK" --> "APPROVED" : IsEligibleCriterion
    "APPROVED" --> "PAYMENT_PENDING" : RequestPaymentProcessor, automatic
    "PAYMENT_PENDING" --> "COMPLETED" : PaymentCompletedCriterion
    "UNDER_REVIEW" --> "REJECTED" : RejectRequestAction, manual
    "COMPLETED" --> [*]
    "REJECTED" --> [*]
```

Processors: ValidateApplicantProcessor, StartBackgroundCheckProcessor, RequestPaymentProcessor, NotifyApplicantProcessor  
Criteria: IsEligibleCriterion, PaymentCompletedCriterion

PetImportJob workflow:
1. CREATED when job posted
2. FETCHING: retrieve from Petstore (automatic)
3. TRANSFORMING: normalize records (automatic)
4. PERSISTING: create Pet entities (automatic)
5. COMPLETED or FAILED

```mermaid
stateDiagram-v2
    [*] --> "CREATED"
    "CREATED" --> "FETCHING" : FetchFromPetstoreProcessor, manual
    "FETCHING" --> "TRANSFORMING" : FetchSuccessCriterion
    "TRANSFORMING" --> "PERSISTING" : TransformPetDataProcessor
    "PERSISTING" --> "COMPLETED" : PersistPetProcessor
    "PERSISTING" --> "FAILED" : PersistFailureCriterion
    "FAILED" --> [*]
    "COMPLETED" --> [*]
```

Processors: FetchFromPetstoreProcessor, TransformPetDataProcessor, PersistPetProcessor, RetryProcessor  
Criteria: FetchSuccessCriterion, PersistFailureCriterion

### 3. Pseudo code for processor classes
PetImportJob - FetchFromPetstoreProcessor
```java
class FetchFromPetstoreProcessor {
  void process(PetImportJob job) {
    data = httpGet(job.sourceUrl, job.filter)
    if data.empty throw FetchFailed
    job.payload = data
    mark job state TRANSFORMING
  }
}
```
TransformPetDataProcessor
```java
class TransformPetDataProcessor {
  void process(PetImportJob job) {
    job.payload = normalizeFields(job.payload)
    mark job state PERSISTING
  }
}
```
PersistPetProcessor
```java
class PersistPetProcessor {
  void process(PetImportJob job) {
    for item in job.payload {
      pet = mapToPet(item)
      persist(pet) // triggers Pet workflow event
    }
    mark job COMPLETED
  }
}
```
ValidateApplicantProcessor (AdoptionRequest)
```java
class ValidateApplicantProcessor {
  void process(AdoptionRequest req) {
    if missingFields reject
    run basic checks
    move to BACKGROUND_CHECK or UNDER_REVIEW
  }
}
```

### 4. API Endpoints Design Rules

Rules applied:
- POST creates entity (triggers workflow) and returns only technicalId.
- GET by technicalId returns stored result.
- No GET by condition included.
- Orchestration entity PetImportJob has POST and GET by technicalId.

Endpoints (examples)

1) Create Pet (persisted event)
POST /pets
Request:
```json
{
  "id":"p123",
  "name":"Mittens",
  "species":"cat",
  "breed":"Tabby",
  "age":2,
  "photos":["https://..."]
}
```
Response:
```json
{ "technicalId":"tx_pet_0001" }
```
GET /pets/{technicalId}
Response (example):
```json
{
  "technicalId":"tx_pet_0001",
  "pet": { "id":"p123","name":"Mittens","status":"AVAILABLE", "species":"cat" }
}
```

2) Create AdoptionRequest
POST /adoption-requests
Request:
```json
{ "applicantName":"Alex","applicantContact":"alex@example.com","petId":"p123","message":"I love cats" }
```
Response:
```json
{ "technicalId":"tx_req_0001" }
```
GET /adoption-requests/{technicalId} -> returns full adoption request and status.

3) Create PetImportJob
POST /jobs/import-pets
Request:
```json
{ "requestedBy":"admin","sourceUrl":"https://petstore/api/pets","filter":"cats" }
```
Response:
```json
{ "technicalId":"tx_job_0001" }
```
GET /jobs/{technicalId} -> returns job status and summary.

Would you like me to expand to 5 entities (add User and Review) or add GET-by-condition endpoints?