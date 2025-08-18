# Functional Requirements

Last updated: 2025-08-18

This document describes the entities, state/workflow logic, processor responsibilities, API design rules, validation rules and operational concerns for the Pet adoption prototype.

> Summary of important changes from prior version:
> - Added a technicalId (system id) to all persisted entities and clarified difference from business id.
> - Explicitly declared a state/lifecycle field for Pet and User and improved state machine clarity (states and transitions including automatic vs manual transitions).
> - Expanded ImportJob status enumeration to include PROCESSING_PETS and NOTIFIED; added notificationSent flag for clarity and idempotency.
> - Added concurrency, idempotency and error handling guidance for processors and APIs.


## Table of contents
- 1. Entity Definitions
- 2. Enumerations
- 3. Entity Workflows (state machines)
- 4. Processor classes (pseudo-code and responsibilities)
- 5. API Endpoints & JSON formats
- 6. Persistence and Workflow Invocation rules
- 7. Validation, Concurrency, Error Handling and Idempotency


## 1. Entity Definitions
All entities persisted by the system must contain a system-assigned technicalId (UUID or database-generated id) in addition to any business id that comes from external sources. The technicalId is what API POST returns and GET uses.

Note: unless specified, String fields represent UTF-8 strings; timestamps use ISO-8601.

1. Pet
- technicalId: String (system id, returned on POST and used for GET)
- id: String? (optional business id from external systems, e.g., source feed)
- name: String
- species: String (e.g., "cat", "dog", etc.)
- breed: String
- age: Integer (years)
- gender: String (M/F/other/unknown)
- lifecycleState: String (CREATED/VALIDATING/AVAILABLE/PENDING/ADOPTED/ARCHIVED)
- status: String (available/adopted/pending) — retained for backward compatibility, but lifecycleState is authoritative
- description: String
- images: Array(String) (URLs)
- healthSummary: String
- createdAt: String (ISO-8601)
- updatedAt: String (ISO-8601)

2. User
- technicalId: String (system id)
- id: String? (business id)
- name: String
- email: String
- contact: String (phone)
- role: String (customer/admin/staff)
- favorites: Array(String) (pet technicalIds)
- lifecycleState: String (CREATED/PROFILE_COMPLETE/ACTIVE/SUSPENDED)
- createdAt: String (ISO-8601)
- updatedAt: String (ISO-8601)

3. ImportJob (orchestration job)
- technicalId: String (system id)
- jobId: String? (business id if provided)
- sourceUrl: String (Petstore API endpoint or other source)
- requestedBy: String (user technicalId)
- status: String (PENDING/RUNNING/PROCESSING_PETS/COMPLETED/FAILED)
- importedCount: Integer
- errorMessage: String? (last/summary error)
- notificationSent: Boolean (false by default; true when requester has been notified)
- createdAt: String (ISO-8601)
- updatedAt: String (ISO-8601)

Notes:
- All persisted entities must include technicalId, createdAt and updatedAt.
- Where a field is optional (e.g., business id), denote with ?.


## 2. Enumerations (recommended)
- Pet.lifecycleState: CREATED, VALIDATING, AVAILABLE, PENDING, ADOPTED, ARCHIVED
- User.lifecycleState: CREATED, PROFILE_COMPLETE, ACTIVE, SUSPENDED
- ImportJob.status: PENDING, RUNNING, PROCESSING_PETS, COMPLETED, FAILED

Processors and criteria will react to these enum values.


## 3. Entity Workflows (state machines)
Workflows are triggered by persistence events (save/create) as described in Section 6. Each transition notes whether it is automatic (processor runs without manual intervention) or manual (requires a user/explicit command).

3.1 Pet workflow
- Initial State: CREATED (set when Pet record persisted)
- VALIDATING: ValidatePetProcessor runs automatically after CREATED
- AVAILABLE: automatic transition if validation success
- PENDING: automatic transition if validation produced warnings/missing optional items OR if external feed marked pending
- ADOPTED: manual transition upon adoption action (AdoptPetProcessor)
- ARCHIVED: manual or automatic (cleanup) transition for retired/removed pets

State diagram (informal):
- [*] -> CREATED
- CREATED -> VALIDATING : ValidatePetProcessor (automatic)
- VALIDATING -> AVAILABLE : ValidationSuccessCriterion (automatic)
- VALIDATING -> PENDING : ValidationWarningCriterion (automatic)
- AVAILABLE -> ADOPTED : AdoptPetProcessor (manual)
- ADOPTED -> ARCHIVED : ArchivePetProcessor (manual)
- PENDING -> AVAILABLE : ResolvePendingProcessor (manual or automatic when issues cleared)
- ARCHIVED -> [*]

Processors/Criterions required: ValidatePetProcessor, AdoptPetProcessor, ArchivePetProcessor, ValidationSuccessCriterion, ValidationWarningCriterion, ResolvePendingProcessor.

Notes:
- The authoritative state is lifecycleState; the free-form status field (available/adopted/pending) may be maintained for compatibility but must be updated to reflect lifecycleState.
- Validation should be idempotent and tolerant of partial data; repeated runs on the same pet should converge.


3.2 User workflow
- Initial State: CREATED
- PROFILE_COMPLETE: manual transition when user completes profile via CompleteProfileProcessor
- ACTIVE: automatic transition on criterion ProfileCompleteCriterion (or when created by an admin with required fields)
- SUSPENDED: manual transition via SuspendUserProcessor

State diagram (informal):
- [*] -> CREATED
- CREATED -> PROFILE_COMPLETE : CompleteProfileProcessor (manual)
- PROFILE_COMPLETE -> ACTIVE : ActivateUserProcessor (automatic)
- ACTIVE -> SUSPENDED : SuspendUserProcessor (manual)
- SUSPENDED -> ACTIVE : ReinstateUserProcessor (manual)

Processors required: CompleteProfileProcessor, ActivateUserProcessor, SuspendUserProcessor, ReinstateUserProcessor. Criterion: ProfileCompleteCriterion.

Notes:
- A user must have a valid email to reach ACTIVE. Activation may also check for email verification depending on configuration.


3.3 ImportJob workflow (orchestration)
- Initial State: PENDING (job created via POST)
- RUNNING: StartImportProcessor transitions job to RUNNING
- PROCESSING_PETS: FetchAndTransformProcessor transitions or continues processing; during this state pets are created/updated (each creation may trigger Pet workflow)
- COMPLETED or FAILED: processors set job to COMPLETED or FAILED depending on outcome
- Notification: NotifyRequesterProcessor sends notification to requester; notificationSent flag ensures idempotent notifications

State diagram (informal):
- [*] -> PENDING
- PENDING -> RUNNING : StartImportProcessor (automatic)
- RUNNING -> PROCESSING_PETS : FetchAndTransformProcessor (automatic)
- PROCESSING_PETS -> COMPLETED : AllItemsImportedCriterion (automatic)
- PROCESSING_PETS -> FAILED : ImportErrorCriterion (automatic)
- COMPLETED -> (NotifyRequesterProcessor runs) and set notificationSent = true
- FAILED -> (NotifyRequesterProcessor runs) and set notificationSent = true

Processors/Criterions required: StartImportProcessor, FetchAndTransformProcessor, NotifyRequesterProcessor, AllItemsImportedCriterion, ImportErrorCriterion.

Notes:
- A job should be resumable/retryable. importedCount must be accurate even if job restarts; processors must be idempotent.
- The notification stage is an action rather than a persistent state; we track notificationSent to avoid duplicate notifications.


## 4. Processor classes (concise pseudo-code & responsibilities)
Each processor is responsible for a focused task and must be idempotent, log progress, and persist any changes to associated entities. Processors should follow a retry/backoff policy and surface errors via entity.errorMessage or job.errorMessage.

4.1 ValidatePetProcessor (automatic after Pet CREATED)
Responsibilities:
- Fetch pet fields and images (URLs) metadata
- Verify required fields exist (name/species or business rules)
- Check reachability of image URLs (optional configurable: only verify presence vs full HEAD request)
- Apply validation rules and set lifecycleState to AVAILABLE or PENDING
- Persist pet and updated timestamps; do not delete data on validation failures

Pseudo:
class ValidatePetProcessor {
  process(pet) {
    // idempotent checks
    validationOk = checkRequiredFields(pet) && checkImages(pet.images)
    if (validationOk) pet.lifecycleState = "AVAILABLE"
    else pet.lifecycleState = "PENDING"
    pet.updatedAt = now()
    save(pet)
  }
}

4.2 AdoptPetProcessor (manual)
Responsibilities:
- Ensure pet is in a state that allows adoption (e.g., AVAILABLE)
- Perform business checks (user eligibility, reservation, payments, etc.) — out of scope for this file but listed as hooks
- Set lifecycleState to ADOPTED and update status field
- Persist and emit domain event if needed

Pseudo:
class AdoptPetProcessor {
  process(pet, adopter) {
    if (pet.lifecycleState != "AVAILABLE") throw error
    pet.lifecycleState = "ADOPTED"
    pet.status = "adopted"
    pet.updatedAt = now()
    save(pet)
  }
}

4.3 ArchivePetProcessor (manual/automatic)
Responsibilities:
- Mark pet as ARCHIVED, optionally remove or anonymize PII/images per retention policy
- Persist

4.4 ResolvePendingProcessor (manual or automatic)
Responsibilities:
- Re-run validation or apply manual corrections
- Move lifecycleState PENDING -> AVAILABLE when issues resolved

4.5 StartImportProcessor
Responsibilities:
- Move ImportJob.status from PENDING -> RUNNING
- Set startedAt (optional), updatedAt
- Persist

Pseudo:
class StartImportProcessor { process(job) { job.status = "RUNNING"; job.updatedAt = now(); save(job) } }

4.6 FetchAndTransformProcessor
Responsibilities:
- Fetch items from sourceUrl (paginated where applicable)
- Transform items into Pet entities (mapping fields, sanitization)
- Persist each Pet (each PET persist triggers the Pet workflow)
- Update job.importedCount incrementally and persist progress frequently
- Handle partial failures: if item import fails, log and continue where appropriate, capturing errors in job.errorMessage and marking job FAILED only if unrecoverable
- Support resume by checking previously imported items (match by business id, hash or dedup key)

Pseudo:
class FetchAndTransformProcessor {
  process(job) {
    for each page in fetchPages(job.sourceUrl) {
      for each item in page.items {
        try {
          pet = mapToPet(item)
          // deduplicate: if pet with business id exists, update instead
          persistPetIdempotent(pet)
          job.importedCount++
          save(job)
        } catch (e) {
          appendJobError(job, e)
          // continue importing remaining items unless an unrecoverable error occurs
        }
      }
    }
    // set job status to COMPLETED when done or FAILED depending on unrecoverable conditions
    save(job)
  }
}

4.7 NotifyRequesterProcessor
Responsibilities:
- Send notification (email, webhook or message) to job.requestedBy with summary of results (importedCount, status, errorMessage)
- Ensure idempotency (do not re-send if notificationSent == true)
- Persist notificationSent = true and updatedAt

Pseudo:
class NotifyRequesterProcessor {
  process(job) {
    if (job.notificationSent) return
    sendNotification(job.requestedBy, summaryFrom(job))
    job.notificationSent = true
    job.updatedAt = now()
    save(job)
  }
}

4.8 Criteria classes (example)
- ValidationSuccessCriterion: returns true when pet passes validation rules
- ValidationWarningCriterion: returns true when pet has warnings causing PENDING
- AllItemsImportedCriterion: returns true when import stream/pages exhausted with no unrecoverable errors
- ImportErrorCriterion: returns true when job encountered fatal errors


## 5. API Endpoints & JSON formats
General rules:
- POST creates entity and triggers the configured workflows (persistence triggers processors). POST returns only the technicalId and 201 Created.
- GET endpoints return the full stored entity by technicalId.
- All POST-created entities must be retrievable via GET by technicalId.
- ImportJob must support POST (to create job) and GET (to query job progress/result) by technicalId.
- GET endpoints are read-only and reflect persisted state; they must not trigger side-effects or workflow progress.

5.1 Pet
POST /pets
- Request body: { id?, name, species, breed?, age?, gender?, description?, images?, healthSummary? }
- Response: 201 { technicalId }

GET /pets/{technicalId}
- Response: 200 { technicalId, id?, name, species, breed, age, gender, lifecycleState, status, description, images, healthSummary, createdAt, updatedAt }

5.2 ImportJob
POST /importJobs
- Request body: { sourceUrl, requestedBy }
- Response: 201 { technicalId }

GET /importJobs/{technicalId}
- Response: 200 { technicalId, jobId?, sourceUrl, requestedBy, status, importedCount, errorMessage, notificationSent, createdAt, updatedAt }

5.3 User
POST /users
- Request body: { id?, name, email, contact?, role? }
- Response: 201 { technicalId }

GET /users/{technicalId}
- Response: 200 { technicalId, id?, name, email, contact, role, favorites, lifecycleState, createdAt, updatedAt }

Notes:
- For POST, server returns technicalId only to keep clients decoupled from internal representation. Clients can subsequently GET by technicalId to retrieve the stored entity.
- API error responses should follow a consistent error format { code, message, details? }.

Example sequences (short):
- POST /pets -> 201 { technicalId }
- GET /pets/{technicalId} -> 200 { ...complete pet... }

- POST /importJobs -> 201 { technicalId }
- GET /importJobs/{technicalId} -> 200 { status: RUNNING, importedCount: 42 }


## 6. Persistence and Workflow Invocation rules
- When an entity is persisted (created or updated), the system will enqueue/trigger the associated workflow processors for the new/changed entity instance. This is the primary mechanism for automatic transitions.
- Processors must read the persisted entity (or the event payload) and may update the entity state.
- Processors should be designed to be idempotent; a processor may be executed more than once for the same entity due to retries or duplicate events.
- Long-running work should be done asynchronously (e.g., import fetching). Short synchronous validation is acceptable if quick.

Concurrency guidance:
- Use optimistic locking (version/timestamp) where available to prevent lost updates during concurrent processor runs.
- When multiple processors could update the same entity, enforce ordering or use compare-and-set style updates.


## 7. Validation, Concurrency, Error Handling and Idempotency
Validation
- Required fields and business rules should be enforced by processors (e.g., ValidatePetProcessor) and by API input validation.
- Provide clear validation messages in job.errorMessage or field-level metadata on GET responses if desired.

Concurrency
- Persisted entities should include an updatedAt timestamp and optional version number.
- Processors must detect concurrent updates and either retry or fail with a clear error message.

Error handling
- Processors should capture exceptions, append to entity.job.errorMessage or Pet.errorMessage as appropriate, and set job.status to FAILED if unrecoverable.
- Partial failures in imports should be captured on the job errorMessage and should not necessarily mark the job FAILED unless a configurable threshold is reached.

Idempotency
- POST operations that create jobs/pets should be idempotent for retry scenarios. Provide support by using optional client-supplied idempotency keys (recommended) or by deduping using business ids when provided.
- Notification sending must be guarded by notificationSent flag.

Security & Privacy
- Only disclose data appropriate to the caller's role (e.g., admin may see more fields). This document does not enumerate the full RBAC rules but the API implementation must enforce them.


## Appendix: Change log
- 2025-08-18: Add technicalId to all entities, clarify lifecycleState, add notificationSent to ImportJob, add concurrency and idempotency guidance.


End of document.
