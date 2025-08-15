### 1. Entity Definitions

Note: The project contains 3 entities used by the ingestion and processing pipelines: SyncJob (orchestration), Pet (business), Owner (business).

```
Pet:
- petId: String (external ID from Petstore API or source system; business identifier)
- name: String (pet name)
- species: String (e.g., cat, dog)
- breed: String (breed description)
- age: Integer (age in years)
- status: String (availability status; e.g., available, reserved, adopted)
- location: String (shelter or store location)
- ownerExternalId: String (external owner id if present in source)
- ownerTechnicalId: String (datastore technicalId of associated Owner, nullable; serialized UUID as String)
- source: String (data source identifier, e.g., Petstore API)
- lastSyncedAt: String (ISO-8601 timestamp when this entity was last synced)
- createdAt: String (ISO-8601)
- updatedAt: String (ISO-8601)
 Do not use enum - not supported temporarily.
```

```
Owner:
- ownerId: String (external owner id from source systems; business identifier)
- firstName: String
- lastName: String
- email: String
- phone: String
- address: String
- source: String (data source identifier, e.g., Petstore API)
- petExternalIds: List<String> (list of external petIds associated in source)
- petTechnicalIds: List<String> (list of persisted Pet technicalIds associated, nullable; serialized UUIDs as Strings)
- createdAt: String (ISO-8601)
- updatedAt: String (ISO-8601)
 Do not use enum - not supported temporarily.
```

```
SyncJob:
- jobName: String (human-friendly name for the sync job, e.g., Petstore_Ingest_2025_08)
- sourceUrl: String (e.g., Petstore API base URL or OpenAPI endpoint)
- sourceType: String (e.g., PetstoreAPI)
- jobParameters: Map<String,Object> (free-form parameters: filters, maxRecords, concurrency, etc.)
- scheduleCron: String (optional cron expression if scheduled)
- status: String (PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED)
- startedAt: String (ISO-8601)
- finishedAt: String (ISO-8601)
- processedCount: Integer
- persistedCount: Integer
- failedCount: Integer
- errors: List<String> (human readable error messages)
- createdAt: String (ISO-8601)
- updatedAt: String (ISO-8601)
 Do not use enum - not supported temporarily.
```

---

### 2. Workflow design adjustments

To adhere to the Workflow Design Rules and to simplify implementation, the workflows have been adjusted as follows. The business logic and state transitions are preserved; processor counts have been reduced where possible by consolidating logically related operations into single processors. No loops were introduced; transitions that branch include criteria to choose the path.

Summary of adjustments applied to workflows in src/main/resources/workflow:
- Pet: validation -> duplicate check -> persist. Enrichment and notification responsibilities are consolidated into the persistence processor to limit the processor count. Manual transitions remain for merge decisions and availability changes.
- Owner: validation -> duplicate check -> persist. Merge decisions are manual. Notifications and activation are handled inside processors where appropriate.
- SyncJob: start -> fetch+transform (combined) -> enqueue -> monitor -> finalize. Fetch and transform are combined into a single processor (FetchAndTransformProcessor) to limit processors and keep the orchestration simple.

These simplified workflows preserve the original business logic (validation, duplicate detection, merging, persistence, notifications) while complying with the constraints of 1-2 processors per workflow and no transition loops.

Detailed notes:
- Processors and criteria remain the same in semantics (e.g., PetValidationProcessor, DuplicatePetCriterion, PetPersistenceProcessor), but some processors may encapsulate enrichment or transformation to reduce count.
- Manual transitions remain for cases that require human decision (merge resolution, reserve/adopt actions, owner deactivation).
- Criteria-based branching is used whenever a state has multiple outgoing transitions (e.g., validation pass/fail, duplicate found/not found, final persistence success/failure).

---

### 3. Entities

No changes were required to the entity POJOs. The current Java classes under src/main/java/com/java_template/application/entity/* reflect the fields and validation logic required by the workflows:
- Pet.java (version_1)
- Owner.java (version_1)
- SyncJob.java (version_1)

Each entity implements CyodaEntity and includes an isValid() method that checks required fields. Foreign key references use String types (serialized UUIDs) as required.

If you want any entity fields added or validation rules tightened, specify which entity and which fields/validations to change.

---

### 4. API

The API design rules remain as previously documented. No changes were made to the API endpoints in the repository. Current design highlights:
- Orchestration entity SyncJob has a POST endpoint to create and trigger ingestion: POST /api/v1/sync-jobs. The POST should return only the technicalId string.
- GET by technicalId endpoints exist for stored results and are used to retrieve SyncJob, Pet, and Owner by their technicalId: GET /api/v1/sync-jobs/{technicalId}, GET /api/v1/pets/{technicalId}, GET /api/v1/owners/{technicalId}.
- Business entities (Pet, Owner) are created by the SyncJob pipeline; there are no direct POST endpoints for Pet or Owner in this design. If manual creation is needed later, a POST endpoint can be added and must return only technicalId.

If you would like explicit HTTP status codes, error response formats, or to add manual POST endpoints for Pet/Owner, indicate which option you prefer and I will update the functional requirements and add example API docs.

---

### 5. Files updated

The workflow JSON files under src/main/resources/workflow/* were adjusted to conform to the simplified design: combine certain processors, reduce processor count, ensure criteria on branching transitions, and remove loops.

---

If you confirm these adjustments are acceptable, I can:
- Add explicit API response codes and error samples to the functional requirements.
- Modify any entity fields or validation rules.
- Revert any workflow change or further split processors if desired.

Once you are happy with the functional requirements and workflows, I will call finish_discussion.