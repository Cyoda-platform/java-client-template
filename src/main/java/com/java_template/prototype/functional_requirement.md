### 1. Entity Definitions
```
Pet:
- id: string (unique business id from source or generated)
- name: string (pet name)
- species: string (dog cat etc)
- breed: string (breed text)
- age: number (years)
- gender: string (male female unknown)
- status: string (new validated available reserved adopted archived)
- bio: string (description)
- photos: array[string] (URLs)
- location: string (city or shelter)
- adoptionRequests: array[object] (ownerId, requestedAt, notes, status)

Owner:
- id: string (business owner id)
- name: string (owner full name)
- contact: object (email phone)
- address: string (optional)
- favorites: array[string] (pet ids)
- adoptedPets: array[string] (pet ids)
- accountStatus: string (active suspended)
- createdAt: string (ISO timestamp)

PetIngestionJob:
- jobId: string (job business id)
- source: string (Petstore API url or descriptor)
- requestedBy: string (user id or system)
- startedAt: string (ISO timestamp)
- completedAt: string (ISO timestamp)
- status: string (pending running completed failed)
- importedCount: number
- errors: array[string]
```

### 2. Entity workflows

Pet workflow:
1. Initial State: persisted (new)
2. Validation: automatic checks (required fields, photo accessibility)
3. Enrichment: automatic enrichment from Petstore metadata
4. Available: visible to visitors
5. Reserved: manual transition when owner requests adoption (creates adoptionRequests entry)
6. Adopted: manual admin approval after validation
7. Post Adoption: automatic notifications and archive candidate
8. Archived: manual/automatic cleanup

```mermaid
stateDiagram-v2
    [*] --> "NEW"
    "NEW" --> "VALIDATION" : ProcessPetValidationProcessor, automatic
    "VALIDATION" --> "ENRICHMENT" : EnrichPetProcessor, automatic
    "ENRICHMENT" --> "AVAILABLE" : if valid
    "AVAILABLE" --> "RESERVED" : CreateAdoptionRequestProcessor, manual
    "RESERVED" --> "ADOPTED" : ApproveAdoptionProcessor, manual
    "ADOPTED" --> "POST_ADOPTION" : PostAdoptionProcessor, automatic
    "POST_ADOPTION" --> "ARCHIVED" : ArchivePetProcessor, automatic
    "VALIDATION" --> "ARCHIVED" : ValidationFailedProcessor, automatic
```

Pet workflow processors and criteria:
- Processors: ProcessPetValidationProcessor, EnrichPetProcessor, CreateAdoptionRequestProcessor, ApproveAdoptionProcessor, PostAdoptionProcessor, ArchivePetProcessor, ValidationFailedProcessor
- Criteria: PetValidCriterion, AdoptionRequestCompleteCriterion

Owner workflow:
1. Created: owner created via POST (pending verification)
2. Verified: automatic/email verification or manual review
3. Active: can request adoptions and favorites
4. Suspended: manual/admin action

```mermaid
stateDiagram-v2
    [*] --> "CREATED"
    "CREATED" --> "VERIFIED" : VerifyOwnerProcessor, automatic
    "VERIFIED" --> "ACTIVE" : ActivateOwnerProcessor, automatic
    "ACTIVE" --> "SUSPENDED" : SuspendOwnerProcessor, manual
    "SUSPENDED" --> "ACTIVE" : ReinstateOwnerProcessor, manual
```

Owner processors and criteria:
- Processors: VerifyOwnerProcessor, ActivateOwnerProcessor, SuspendOwnerProcessor, ReinstateOwnerProcessor
- Criteria: OwnerContactValidCriterion

PetIngestionJob workflow:
1. Created: POST job triggers event
2. Running: job fetches Petstore data and creates Pet entities (each Pet persisted triggers Pet workflow)
3. Completed/Failed: job sets final status and summary

```mermaid
stateDiagram-v2
    [*] --> "PENDING"
    "PENDING" --> "RUNNING" : StartIngestionProcessor, manual
    "RUNNING" --> "COMPLETED" : CompleteIngestionProcessor, automatic if success
    "RUNNING" --> "FAILED" : FailIngestionProcessor, automatic if errors
    "COMPLETED" --> [*]
    "FAILED" --> [*]
```

PetIngestionJob processors and criteria:
- Processors: StartIngestionProcessor, FetchPetstoreProcessor, CreatePetProcessor, CompleteIngestionProcessor, FailIngestionProcessor
- Criteria: SourceReachableCriterion, ImportThresholdCriterion

### 3. Pseudo code for processor classes (concise)

ProcessPetValidationProcessor:
```
class ProcessPetValidationProcessor {
  void process(Pet pet) {
    if (pet.name empty or pet.species empty) { pet.status = "archived"; emit ValidationFailed }
    else pet.status = "validated"
  }
}
```

EnrichPetProcessor:
```
class EnrichPetProcessor {
  void process(Pet pet) {
    // enrich with breed normalization, image checks
    pet.status = "available"
  }
}
```

StartIngestionProcessor / FetchPetstoreProcessor:
```
class StartIngestionProcessor {
  void process(PetIngestionJob job) {
    job.status = "running"
    for each record from source {
      CreatePetProcessor.process(record)
      job.importedCount++
    }
    job.status = "completed"
  }
}
```

CreatePetProcessor:
```
class CreatePetProcessor {
  void process(sourceRecord) {
    Pet pet = mapRecordToPet(sourceRecord)
    persist pet
    // Cyoda starts Pet workflow automatically on persist
  }
}
```

ApproveAdoptionProcessor:
```
class ApproveAdoptionProcessor {
  void process(Pet pet, adoptionRequest) {
    pet.adoptionRequests.update(request->status = approved)
    pet.status = "adopted"
    // update owner.adoptedPets via Owner update processor
  }
}
```

### 4. API Endpoints Design Rules

Rules applied:
- POST endpoints return only technicalId string.
- POST endpoints start Cyoda workflows (events).
- GET endpoints only retrieve stored results.
- GET by technicalId present for entities created via POST.

Endpoints:

1) Create Pet Ingestion Job (orchestration)
POST /jobs/ingestPets
Request:
```json
{
  "source": "https://petstore.example/api/pets",
  "requestedBy": "user_123"
}
```
Response:
```json
{
  "technicalId": "job_abc123"
}
```

GET job by technicalId
GET /jobs/ingestPets/{technicalId}
Response:
```json
{
  "jobId":"job_abc123",
  "source":"https://petstore.example/api/pets",
  "status":"completed",
  "importedCount":24,
  "errors":[]
}
```

2) Create Owner (signup)
POST /owners
Request:
```json
{
  "name":"Alex Doe",
  "contact":{"email":"alex@example.com","phone":"555-0100"},
  "address":"City"
}
```
Response:
```json
{
  "technicalId":"owner_xyz789"
}
```

GET owner by technicalId
GET /owners/{technicalId}
Response:
```json
{
  "id":"owner_xyz789",
  "name":"Alex Doe",
  "contact":{"email":"alex@example.com","phone":"555-0100"},
  "favorites":[],
  "adoptedPets":[],
  "accountStatus":"active"
}
```

3) Read Pet (results)
GET /pets/{id}
Response:
```json
{
  "id":"pet_001",
  "name":"Mittens",
  "species":"cat",
  "breed":"Tabby",
  "age":2,
  "status":"available",
  "location":"Shelter A"
}
```

Notes and assumptions:
- Creating pets is normally done by PetIngestionJob; Pet persistence triggers Pet workflow automatically in Cyoda.
- Adoption requests are stored inside Pet.adoptionRequests (created via manual transition from Owner action).
- If you want separate AdoptionRequest as its own POST-created entity, I can expand to 4 entities on request (within the 10-entity limit).