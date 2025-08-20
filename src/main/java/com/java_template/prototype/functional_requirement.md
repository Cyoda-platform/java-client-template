# Functional Requirements

This document specifies the canonical functional requirements for the ingestion system that fetches external activity records (from Fakerest), creates Activity entities, maintains User entities, and records IngestionRun orchestration metadata. It clarifies entities, workflows (state machines), processors, criteria, pseudocode, and API contract. The content here is intended to be the single source of truth for implementation.

---

## 1. Entities and Schemas

All timestamps MUST be ISO 8601 (UTC) strings. Every persisted entity MUST include a generated immutable `technicalId` (UUID) used internally. External ids from the source are stored as separate fields.

1. User
- technicalId: String (UUID, internal)
- userId: String (external user id from Fakerest, may be null if unknown)
- name: String (display name, optional)
- email: String (contact, optional)
- status: String (ACTIVE / INACTIVE)
- createdAt: String (ISO timestamp when user record created)
- lastSeenAt: String (ISO timestamp of last activity linked)
- metadata: Object (optional key/value map)

2. Activity
- technicalId: String (UUID, internal)
- activityId: String (external activity id from Fakerest, optional)
- userTechnicalId: String (internal technicalId link to User) OR userId: String (external) depending on storage model
- type: String (activity type as received)
- normalizedType: String (normalized/mapped type/category)
- startTime: String (ISO timestamp)
- endTime: String (ISO timestamp)
- durationSec: Number (computed; endTime - startTime in seconds)
- sourceFetchedAt: String (ISO timestamp when fetched from source)
- dedupHint: String (fingerprint used for deduplication)
- anomalyFlag: Boolean (true if anomaly detected)
- status: String (CREATED / INVALID / DUPLICATE / SKIPPED / ENRICHED / STORED / REVIEW)
- errors: Array&lt;String&gt; (collection of validation/enrichment errors)
- createdAt: String (ISO timestamp when activity persisted)

Notes:
- `status` tracks processing state for observability and retry logic. `DUPLICATE` indicates a detected duplicate that may or may not be persisted; `SKIPPED` indicates was intentionally not stored.

3. IngestionRun
- technicalId: String (UUID, internal)
- runId: String (user-supplied id for idempotency, e.g., "run-2025-08-20")
- scheduledAt: String (time job was scheduled to start)
- startedAt: String (actual start time when processing started)
- finishedAt: String (end time when processing finished)
- status: String (PENDING / IN_PROGRESS / PROCESSING_ACTIVITIES / AGGREGATING / COMPLETED / PARTIAL / FAILED / PUBLISHED)
- recordsFetched: Number
- recordsStored: Number
- errorsSummary: String (concise summary)
- createdAt: String
- publishedAt: String (optional, when publish step completed)

Notes:
- `PUBLISHED` is an optional post-processing state that indicates reports/outputs were successfully published.

---

## 2. State Machines and Workflows

### 2.1 IngestionRun Workflow
States and meanings:
- PENDING: created via POST /ingestionRuns. Awaiting scheduler/processor.
- IN_PROGRESS: processing has started (startedAt set).
- PROCESSING_ACTIVITIES: actively creating / processing Activity entities.
- AGGREGATING: aggregating results and computing final counters.
- COMPLETED: run finished successfully; all fetched records were processed and stored (or deterministically skipped as duplicates without errors).
- PARTIAL: some records were stored but one or more records failed non-recoverably.
- FAILED: run failed to store any records due to a major error.
- PUBLISHED: optional final step after COMPLETED where reports were published.

Transitions (high level):
- POST -> PENDING
- PENDING -> IN_PROGRESS (StartIngestionProcessor)
- IN_PROGRESS -> PROCESSING_ACTIVITIES (CreateActivityEntitiesProcessor)
- PROCESSING_ACTIVITIES -> AGGREGATING (when all activity persistence tasks complete)
- AGGREGATING -> COMPLETED (if AllRecordsStoredCriterion OR deterministic skips only)
- AGGREGATING -> PARTIAL (if some records stored, some failed)
- AGGREGATING -> FAILED (if no records stored due to major failure)
- COMPLETED -> PUBLISHED (PublishReportsProcessor) [optional]
- PARTIAL -> FAILED (manual review may escalate)

Definitions:
- AllRecordsStoredCriterion: every non-duplicate and valid fetched record persisted successfully.
- HasFetchedRecordsCriterion: fetched count &gt; 0.

Manual operations:
- Manual retry or manual review may transition PARTIAL -> IN_PROGRESS or PARTIAL -> FAILED depending on outcome.

### 2.2 Activity Workflow
States and meanings:
- CREATED: activity persisted by ingestion run with initial status.
- VALIDATED: passed field-level validation.
- INVALID: failed validation and requires human/automated resolution; not stored as STORED.
- DEDUPLICATED: deduplication check completed. If duplicate found and the existing record is authoritative, mark as SKIPPED or DUPLICATE accordingly.
- ENRICHED: enrichment completed (duration computed, normalizedType set, dedupHint computed).
- REVIEW: flagged for manual review (e.g., anomaly flagged or ambiguous data).
- STORED: final persisted, visible to downstream consumers.
- SKIPPED: intentionally not stored because duplicate or other deterministic reason.

Transitions:
- CREATED -> VALIDATED (ValidateActivityProcessor)
- VALIDATED -> INVALID (if validation fails)
- VALIDATED -> DEDUPLICATED (CheckDuplicateCriterion)
- DEDUPLICATED -> SKIPPED / DUPLICATE (if existing record supersedes)
- DEDUPLICATED -> ENRICHED (if not duplicate)
- ENRICHED -> REVIEW (if DetectAnomalyCriterion triggered)
- ENRICHED -> STORED (StoreActivityProcessor)
- REVIEW -> STORED (after ManualApproveProcessor)

Deduplication policy:
- dedupHint = hash(userId + normalizedType + startTime + endTime) (or other stable fingerprint)
- If an activity with same dedupHint already exists:
  - If incoming record is identical or older, mark incoming as SKIPPED/DUPLICATE (do not replace)
  - If incoming record is newer/corrects data, allow update/merge based on policy (explicitly defined per implementation)

Anomaly policy (examples):
- durationSec &lt;= 0 OR &gt; threshold (e.g., 24 * 3600 seconds) => anomalyFlag true
- Unrecognized type mapping => anomalyFlag true
- Out-of-range timestamps (future dates far ahead) => anomalyFlag true

### 2.3 User Workflow
States and meanings:
- CREATED: user record created automatically when first activity references a user.
- ENRICHED: profile fields populated from source or enrichment services.
- ACTIVE: user marked active when RecentActivityCriterion satisfied.
- INACTIVE: user marked inactive after NoActivityCriterion or retention rules.

Transitions:
- CREATED -> ENRICHED (EnrichUserProcessor)
- ENRICHED -> ACTIVE (RecentActivityCriterion)
- ENRICHED -> INACTIVE (NoActivityCriterion)
- ACTIVE <-> INACTIVE can be toggled manually (ReactivateProcessor/DeactivateProcessor)

---

## 3. Processors and Criteria (Descriptions)

Processors (concise):
- StartIngestionProcessor: validate runId idempotency; set startedAt; initiate fetch tasks.
- CreateActivityEntitiesProcessor: for each fetched record, persist initial Activity entity with status CREATED and sourceFetchedAt.
- ValidateActivityProcessor: check required fields (user, startTime, endTime, or acceptable mapping). On fail, set status INVALID and add error.
- CheckDuplicateProcessor: compute dedupHint and check activity store for matching fingerprint.
- EnrichActivityProcessor: compute durationSec, normalizedType, dedupHint; set anomalyFlag if criteria met.
- StoreActivityProcessor: attempt to persist or update Activity; set status STORED or SKIPPED/DUPLICATE.
- AggregateResultsProcessor: compute recordsStored, errorsSummary, set finishedAt and status of IngestionRun.
- PublishReportsProcessor: optional; produce daily/user-level summaries and set publishedAt/status.
- ManualApproveProcessor: used by reviewers to move REVIEW items to STORED or INVALID.

Criteria (examples):
- ValidActivityCriterion: startTime and endTime parseable; startTime <= endTime (or allowed tolerance), required fields present.
- CheckDuplicateCriterion: dedupHint matches existing record.
- DetectAnomalyCriterion: duration out of bounds or missing/invalid mappings.
- RecentActivityCriterion: lastSeenAt within X days.
- NoActivityCriterion: lastSeenAt older than retention threshold.

---

## 4. Pseudocode (Updated, concise)

StartIngestionProcessor.run(run):
- if existsByRunId(run.runId) return existing technicalId (idempotent)
- run.startedAt = now()
- fetch = Fakerest.fetch(since = run.scheduledAt)
- run.recordsFetched = fetch.count
- for each r in fetch:
  - activity = persistInitialActivity(r, sourceFetchedAt = now()) // status = CREATED
  - schedule processors for activity (validation/enrichment)
- wait for activity processing tasks to complete (or track asynchronously)
- aggregate results (AggregateResultsProcessor)
- set run.finishedAt and run.status = COMPLETED / PARTIAL / FAILED accordingly

ValidateActivityProcessor.run(activity):
- if missing required fields or timestamps unparsable:
  - activity.status = INVALID; activity.errors += ...; persist
  - return
- activity.status = VALIDATED; persist

EnrichActivityProcessor.run(activity):
- activity.durationSec = secondsBetween(activity.startTime, activity.endTime)
- activity.dedupHint = hash(activity.userId + activity.normalizedType + activity.startTime + activity.endTime)
- activity.normalizedType = mapType(activity.type)
- if durationSec <= 0 or durationSec > ANOMALY_MAX or normalizedType == UNKNOWN:
  - activity.anomalyFlag = true
- activity.status = ENRICHED; persist

StoreActivityProcessor.run(activity):
- if CheckDuplicateCriterion(activity):
  - decide SKIPPED vs replace based on dedup policy; set status DUPLICATE or SKIPPED; persist
  - return
- attempt to save to activity store (transactional if required)
- if success: activity.status = STORED; persist
- else: activity.errors += 'store failure' and surface for retry

AggregateResultsProcessor.run(run):
- recordsStored = count activities with status STORED linked to run
- errorsSummary = summarize activity.errors and run-level errors
- run.finishedAt = now()
- if recordsStored == 0 and run.recordsFetched > 0: run.status = FAILED
- else if some failures exist: run.status = PARTIAL
- else run.status = COMPLETED
- persist run

PublishReportsProcessor.run(run):
- if run.status != COMPLETED: skip (unless manual override)
- build per-user summaries for the run window
- mark run.publishedAt = now(); run.status = PUBLISHED

---

## 5. API Endpoints and Rules

General rules:
- All POST endpoints MUST be idempotent where appropriate. For the orchestration entity IngestionRun we use `runId` for idempotency.
- All timestamps in input and responses MUST be ISO 8601 strings (UTC).
- All POST responses MUST return only { "technicalId": "<generated-id>" }
- GET returns full entity JSON including `technicalId`, external ids, status fields, counters, and timestamps.
- Activities and Users cannot be created directly via public POST (they are produced by ingestion). They are readable via GET.

Endpoints:
1) POST /ingestionRuns
- Purpose: create an IngestionRun orchestration record and trigger ingestion workflow.
- Request JSON (required fields):
  - runId: String (required, used for idempotency)
  - scheduledAt: String (ISO timestamp, required)
- Example request:
  { "runId": "run-2025-08-20", "scheduledAt": "2025-08-20T02:00:00Z" }
- Response (on create or existing):
  { "technicalId": "<generated-or-existing-uuid>" }
- Behavior: if runId already exists, the system should return existing technicalId and not create a duplicate. A client MAY request a retry by creating a new runId.

2) GET /ingestionRuns/{technicalId}
- Returns the full stored IngestionRun JSON.

3) GET /activities/{technicalId}
- Returns Activity by technicalId. Includes status, anomalyFlag, errors, and timestamps.

4) GET /users/{technicalId}
- Returns User by technicalId. Includes lastSeenAt and status.

Notes:
- Only POST allowed for orchestration entity is /ingestionRuns. Activities and Users are produced by the processors executing the ingestion run.
- POST responses must return only the { "technicalId" } object to conform to orchestration rules.

---

## 6. Error handling, retries, and observability

- Each processor should record errors on the entity (`errors` array) and emit structured logs/events for traceability.
- Transient failures (e.g., DB timeouts) should be retried with exponential backoff by the processor orchestration. If after retries the entity still fails, mark run PARTIAL/FAILED appropriately.
- IngestionRun status mapping for retry decisions:
  - PARTIAL: allow selective reprocessing of failed activities.
  - FAILED: major error; manual intervention recommended.

---

## 7. Operational considerations

- Concurrency: ensure deduplication checks and store operations are safe under concurrent ingestion runs (use unique constraint on dedupHint + user + time window, or transactional compare-and-set).
- Idempotency: POST /ingestionRuns must be idempotent on runId. Duplicate submission of the same runId returns the same technicalId and SHOULD NOT launch a second ingestion process.
- Retention: decide retention policy for activities and user records (e.g., keep raw fetch payloads for N days for forensic analysis).

---

## 8. Appendix: Definitions
- technicalId: internal UUID assigned to persisted entities
- external id: id as supplied by upstream source (Fakerest)
- dedupHint: fingerprint used to detect duplicate activities



