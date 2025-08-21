### 1. Entity Definitions
```
Pet:
- id: String (business identifier)
- name: String (pet name)
- species: String (dog/cat/etc)
- breed: String (breed info)
- age: Integer (years or months)
- gender: String (M/F/unknown)
- size: String (small/medium/large)
- color: String (visual description)
- status: String (available/pending/adopted)
- photos: Array (URLs)
- location: String (shelter or city)
- source: String (PetstoreImported|Shelter|Owner)
- createdAt: String (ISO timestamp)
- updatedAt: String (ISO timestamp)

AdoptionRequest:
- id: String (business identifier)
- petId: String (references Pet.id)
- requesterId: String (user identifier)
- ownerId: String (owner or shelter id)
- status: String (submitted|under_review|approved|rejected|withdrawn)
- message: String (applicant message)
- applicationData: Object (questions and answers)
- submittedAt: String (ISO timestamp)
- decisionAt: String (ISO timestamp)

ImportJob:
- id: String (business identifier)
- sourceUrl: String (Petstore API endpoint)
- requestedBy: String (user id)
- status: String (queued|running|completed|failed)
- importedCount: Integer (number of pets imported)
- createdAt: String (ISO timestamp)
- completedAt: String (ISO timestamp)
```

### 2. Entity workflows

Pet workflow:
1. Initial State: created by ImportJob or Adoption approval -> AVAILABLE or PENDING
2. Verification: automatic duplicate check and validation
3. Enrichment: automatic add source metadata/photos normalization
4. Manual Review: staff may mark as featured or reject
5. Lifecycle: status may change to ADOPTED when adoption completes
```mermaid
stateDiagram-v2
    [*] --> "CREATED"
    "CREATED" --> "VERIFICATION" : DuplicateCheckCriterion, ImportPetProcessor, automatic
    "VERIFICATION" --> "ENRICHMENT" : ValidationCriterion, automatic
    "ENRICHMENT" --> "AVAILABLE" : EnrichProcessor, automatic
    "AVAILABLE" --> "PENDING" : ReceiveAdoptionHoldProcessor, manual
    "PENDING" --> "ADOPTED" : AdoptionConfirmedProcessor, automatic
    "AVAILABLE" --> "RETIRED" : ManualRetireProcessor, manual
    "ADOPTED" --> [*]
    "RETIRED" --> [*]
```
Processors/Criteria:
- DuplicateCheckCriterion
- ValidationCriterion
- ImportPetProcessor
- EnrichProcessor
- AdoptionConfirmedProcessor

AdoptionRequest workflow:
1. Initial State: SUBMITTED via POST
2. Screening: automatic checks (pet availability, requester blacklist)
3. Manual Review: owner/staff reviews application
4. Decision: approve/reject/withdraw
5. Post-Decision: if approved, trigger Pet.status update
```mermaid
stateDiagram-v2
    [*] --> "SUBMITTED"
    "SUBMITTED" --> "SCREENING" : ScreeningProcessor, automatic
    "SCREENING" --> "UNDER_REVIEW" : ScreeningCriterion passes, automatic
    "SCREENING" --> "REJECTED" : ScreeningCriterion fails, automatic
    "UNDER_REVIEW" --> "APPROVED" : ManualApproveProcessor, manual
    "UNDER_REVIEW" --> "REJECTED" : ManualRejectProcessor, manual
    "APPROVED" --> "COMPLETED" : AdoptionFinalizeProcessor, automatic
    "REJECTED" --> [*]
    "COMPLETED" --> [*]
```
Processors/Criteria:
- ScreeningCriterion
- ScreeningProcessor
- ManualApproveProcessor
- ManualRejectProcessor
- AdoptionFinalizeProcessor

ImportJob workflow:
1. Initial State: QUEUED after POST
2. Execution: fetch Petstore data, create Pet entities (each Pet creation is an EVENT)
3. Aggregation: count imported items, handle failures
4. Completion: status COMPLETED/FAILED and notify
```mermaid
stateDiagram-v2
    [*] --> "QUEUED"
    "QUEUED" --> "RUNNING" : StartImportProcessor, manual
    "RUNNING" --> "IMPORTING" : FetchCriterion, ImportBatchProcessor, automatic
    "IMPORTING" --> "AGGREGATING" : BatchCompleteCriterion, AggregateProcessor, automatic
    "AGGREGATING" --> "COMPLETED" : SuccessCriterion, NotifyProcessor, automatic
    "AGGREGATING" --> "FAILED" : FailureCriterion, automatic
    "COMPLETED" --> [*]
    "FAILED" --> [*]
```
Processors/Criteria:
- FetchCriterion
- ImportBatchProcessor
- AggregateProcessor
- NotifyProcessor
- SuccessCriterion / FailureCriterion

### 3. Pseudo code for processor classes (high-level)

ImportBatchProcessor.process(job):
- fetch page from job.sourceUrl
- for each record in page:
  - map to Pet entity
  - persist Pet (this triggers Pet workflow in Cyoda)
- update job.importedCount

EnrichProcessor.process(pet):
- normalize photos and sizes
- add source metadata
- persist pet.updatedAt

ScreeningProcessor.process(request):
- check pet.status == available
- check requesterId not in blacklist
- set request.status = under_review or rejected
- persist request

AdoptionFinalizeProcessor.process(request):
- set pet.status = adopted
- set request.decisionAt = now
- persist pet and request

NotifyProcessor.process(job):
- send summary notification to requestedBy (as event emitted)

### 4. API Endpoints Design Rules

Notes: All POST endpoints return only technicalId field in response.

Endpoints:
- POST /import-jobs
  - creates ImportJob (triggers ImportJob workflow)
  - Response:
```json
{ "technicalId": "job_12345" }
```
  - Request:
```json
{
  "sourceUrl": "https://petstore.example/api/pets",
  "requestedBy": "user_abc"
}
```
- GET /import-jobs/{technicalId}
```json
{
  "id":"job_123",
  "sourceUrl":"https://petstore.example/api/pets",
  "status":"completed",
  "importedCount":42,
  "createdAt":"2025-08-01T12:00:00Z",
  "completedAt":"2025-08-01T12:05:00Z"
}
```

- POST /pets
  - creates Pet (triggers Pet workflow)
  - Request:
```json
{
  "id":"pet_001",
  "name":"Mittens",
  "species":"cat",
  "breed":"tabby",
  "age":2,
  "gender":"F",
  "location":"Shelter A",
  "source":"Owner"
}
```
  - Response:
```json
{ "technicalId":"pet_5678" }
```
- GET /pets/{technicalId}
```json
{ "id":"pet_001", "name":"Mittens", "status":"available", "location":"Shelter A", "source":"Owner" }
```

- POST /adoption-requests
  - creates AdoptionRequest (triggers AdoptionRequest workflow)
  - Request:
```json
{
  "petId":"pet_001",
  "requesterId":"user_xyz",
  "message":"I have a fenced yard",
  "applicationData": { "homeType":"house", "otherPets":"no" }
}
```
  - Response:
```json
{ "technicalId":"req_9012" }
```
- GET /adoption-requests/{technicalId}
```json
{
  "id":"req_9012",
  "petId":"pet_001",
  "status":"under_review",
  "submittedAt":"2025-08-01T12:10:00Z"
}
```

Questions to finalize requirements:
- Do you want a User entity modeled explicitly (owners/adopters/staff), or references (ids) are sufficient?
- Any required application questions for adoption (forms) or document uploads?
- Do you want automatic publishing of imported pets or manual approval before listing?