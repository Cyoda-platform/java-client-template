# Functional Requirements — Hacker News Importer

> Updated to reflect the current logic, default policies, and API/processing behavior.

Note: This document contains three primary entities (max entities = 3): HackerNewsItem, ImportJob, ImportEvent.

---

## 1. Entity Definitions

### 1.1. HackerNewsItem
- technicalId: string (opaque id returned by POST, format: `item-<uuid>`, required)
- id: number (Hacker News item id as in HN JSON, required)
- type: string (item type from HN JSON, required)
- originalJson: JSON (the full JSON payload as received; stored as final representation)
- importTimestamp: string (ISO8601 UTC, added when the item is enriched/persisted)
- status: string (processing status, enumerated values)
  - Allowed values: PENDING, PROCESSING, READY, FAILED
- createdAt: string (ISO8601 UTC when the item record was created in system)
- updatedAt: string (ISO8601 UTC last updated timestamp)
- sourceJobTechnicalId: string (optional: `job-<uuid>` of the ImportJob that dispatched/created this item)

Notes:
- There is one canonical HackerNewsItem per HN id by default. The system stores the latest persisted originalJson for that HN id. Historical records/versions are captured via ImportEvent (see below).
- `technicalId` is required for system-side operations (returned by POST) and is distinct from `id` (HN id).

### 1.2. ImportJob
- technicalId: string (opaque id returned by POST, format: `job-<uuid>`, required)
- jobName: string (human-readable name/description)
- requestedBy: string (username or identifier who requested the job)
- payload: JSON (the original payload submitted; single HN JSON object or array of HN JSON)
- createdAt: string (ISO8601 UTC when job record created)
- startedAt: string (ISO8601 UTC when processing started, optional)
- completedAt: string (ISO8601 UTC when job completed/failed, optional)
- status: string (PENDING, RUNNING, COMPLETED, FAILED)
- processedCount: integer (how many items processed or attempted)
- failureCount: integer (how many items failed)
- detailsUrl: string (optional link to per-item processing detail/audit)

Notes:
- POST /importJobs will create a job record with status PENDING and return only `technicalId` in the response. Subsequent processing updates status and counters.

### 1.3. ImportEvent
- eventId: string (opaque event id, format: `event-<uuid>`, required)
- itemTechnicalId: string (the `technicalId` of the HackerNewsItem this event pertains to, optional when event is about job-level errors)
- itemId: number (HN id associated with the event, optional but recommended)
- jobTechnicalId: string (the `technicalId` of the ImportJob that caused this event, optional)
- timestamp: string (ISO8601 UTC when the event was recorded)
- status: string (SUCCESS or FAILURE)
- errors: array of string (validation or processing errors; empty array for SUCCESS)
- metadata: JSON (optional freeform payload: e.g., reason, processor name, raw response from datastore)

Notes:
- ImportEvent is the immutable audit record for each attempted item import and for significant job lifecycle changes.

---

## 2. High-level Rules & Default Policies

- Default duplicate policy: OVERWRITE. If an incoming Hacker News item has the same HN `id` as an existing stored item, the system will, by default, overwrite the stored `originalJson` and update `importTimestamp`. Overwrite is configurable; alternative behaviors supported by configuration: SKIP (keep existing) or REJECT (fail the new item).
- Audit/history: The system guarantees audit by creating ImportEvent records for every item attempt. Even though there is one canonical HackerNewsItem per HN id (by default), ImportEvents provide a full history of imports/attempts and their statuses.
- Idempotency: POSTing the same payload multiple times will be idempotent at the level of ImportEvent creation and item storage when the duplicate policy is OVERWRITE or SKIP. The processor should detect highly similar payloads and avoid unnecessary writes when possible.
- Job reporting: ImportJob stores aggregated counters (processedCount, failureCount). Detailed per-item results are available via ImportEvent queries (or `detailsUrl` if implemented).
- Technical IDs: All POST-created resources return only `technicalId` in the immediate response. Full entity retrieval is done via GET endpoints using `technicalId` or HN id where supported.

---

## 3. Entity Workflows

### 3.1. HackerNewsItem workflow (detailed)
1. Creation: Item created with status = PENDING and a `technicalId`. It may be created directly via POST /hackerNewsItems or indirectly by DispatchItemsProcessor from an ImportJob.
2. Validation (automatic): ValidationCriterion ensures `id` (numeric) and `type` (string) exist and any required fields match expected types. Failures create ImportEvent(status=FAILURE) and set item.status = FAILED.
3. Duplicate check (automatic): IsDuplicateCriterion detects existing item with same HN `id`.
   - Default behavior (OVERWRITE): proceed to Enrichment and Persist; previous stored item gets replaced by the new `originalJson`.
   - SKIP policy: mark item as READY without modifying stored item; create ImportEvent(status=SUCCESS or SKIPPED) as appropriate.
   - REJECT policy: create ImportEvent(status=FAILURE with error `duplicate-rejected`) and set item.status = FAILED.
4. Enrichment (automatic): add `importTimestamp` = now_utc() into entity and into originalJson. Set status = PROCESSING.
5. Persist (automatic): Persist final `originalJson` to primary datastore (and any indexes); set status = READY, updatedAt, and emit ImportEvent(status=SUCCESS).
6. On any persistence failure: set status = FAILED; create ImportEvent(status=FAILURE) with errors.

Mermaid state diagram (conceptual):

stateDiagram-v2
    [*] --> PENDING
    PENDING --> VALIDATING : ProcessStartProcessor
    VALIDATING --> FAILED : ValidationCriterion false
    VALIDATING --> DUPLICATE_CHECK : ValidationCriterion true
    DUPLICATE_CHECK --> FAILED : IsDuplicateCriterion && policy == REJECT
    DUPLICATE_CHECK --> SKIP : IsDuplicateCriterion && policy == SKIP
    DUPLICATE_CHECK --> ENRICHING : not duplicate || policy == OVERWRITE
    SKIP --> READY : mark skipped (no write)
    ENRICHING --> PERSISTING : EnrichItemProcessor
    PERSISTING --> READY : PersistItemProcessor
    PERSISTING --> FAILED : PersistFailureCriterion
    READY --> [*]
    FAILED --> [*]


### 3.2. ImportJob workflow (detailed)
1. Creation: Job created via POST /importJobs with status = PENDING and `technicalId` returned.
2. Start: StartJobProcessor sets status = RUNNING and records `startedAt`.
3. Parse payload: ParsePayloadProcessor validates payload is object or array. On invalid payload, mark job FAILED and record ImportEvent(s) as appropriate.
4. Dispatch items: DispatchItemsProcessor iterates payload items and creates a HackerNewsItem per payload.
   - Each created HackerNewsItem receives `sourceJobTechnicalId` referencing the job.
   - Item creation triggers HackerNewsItem workflow asynchronously.
5. Aggregate results: AggregateResultsProcessor monitors ImportEvents filtered by this jobTechnicalId (or by polling/notification from item workflows) and updates processedCount and failureCount.
6. Completion: When all items have ImportEvents recorded (or after timeout/policy), set job.status = COMPLETED (if failureCount == 0) or FAILED (if failureCount > 0). Set completedAt.
7. Notification (optional): Notify requester with summary results.

Mermaid state diagram (conceptual):

stateDiagram-v2
    [*] --> PENDING
    PENDING --> RUNNING : StartJobProcessor
    RUNNING --> DISPATCHING : ParsePayloadProcessor
    DISPATCHING --> AGGREGATING : DispatchItemsProcessor
    AGGREGATING --> COMPLETED : AllItemsProcessedCriterion true && failureCount == 0
    AGGREGATING --> FAILED : AllItemsProcessedCriterion true && failureCount > 0
    COMPLETED --> NOTIFYING : MarkJobCompleteProcessor
    FAILED --> NOTIFYING : MarkJobFailedProcessor
    NOTIFYING --> [*]


### 3.3. ImportEvent workflow (audit)
1. Creation: CreateImportEventProcessor persists an immutable ImportEvent whenever an item is processed or a job transitions into an error/completed state.
2. Indexing/Retention: IndexImportEventProcessor writes the event into audit store / index for queries and retention policies.
3. Retention/Archival: Optional TTL or archival process for older ImportEvents as defined by data-retention configuration.

Mermaid state diagram (conceptual):

stateDiagram-v2
    [*] --> RECORDED
    RECORDED --> INDEXED : IndexImportEventProcessor
    INDEXED --> [*]

---

## 4. Processors, Criteria & Pseudocode

Notes: The pseudocode below is concise, focusing on responsibilities and side effects (events, datastore writes, status updates). All processors must be resilient and idempotent where feasible.

### 4.1. Criteria
- ValidationCriterion
  - checks presence and types for `id` (number) and `type` (string) in originalJson
- IsDuplicateCriterion
  - checks primary datastore for an existing HackerNewsItem with the same HN `id`
- AllItemsProcessedCriterion (ImportJob)
  - all dispatched items have at least one ImportEvent recorded OR job-level timeout exceeded
- AnyFailuresCriterion (ImportJob)
  - any ImportEvent for this job has status == FAILURE


### 4.2. HackerNewsItem processors

- ProcessStartProcessor
  - input: HackerNewsItem record
  - action: set status = PROCESSING; execute ValidationCriterion; route based on result

- EnrichItemProcessor
  - input: validated HackerNewsItem
  - pseudocode:
    - now = now_utc()
    - entity.importTimestamp = now
    - if entity.originalJson is object: entity.originalJson.importTimestamp = now
    - entity.updatedAt = now
    - return entity

- PersistItemProcessor
  - input: enriched HackerNewsItem
  - pseudocode:
    - attempt write of entity.originalJson to primary datastore keyed by HN `id`
      - if duplicate policy == OVERWRITE: upsert record for HN id
      - if SKIP: no write if existing; mark entity.status = READY and create ImportEvent(status=SUCCESS, metadata={skipped:true})
      - if REJECT: abort and create ImportEvent(status=FAILURE, errors=["duplicate-rejected"])
    - on successful write:
      - entity.status = READY
      - entity.updatedAt = now_utc()
      - persist HackerNewsItem metadata record (technicalId, id, type, importTimestamp, createdAt/updatedAt, sourceJobTechnicalId)
      - emit ImportEvent(status=SUCCESS, itemTechnicalId, itemId, jobTechnicalId)
    - on failure:
      - entity.status = FAILED
      - emit ImportEvent(status=FAILURE, errors=[...])

- CreateImportEventProcessor
  - input: event details
  - pseudocode:
    - construct ImportEvent(eventId, timestamp=now_utc(), itemTechnicalId, itemId, jobTechnicalId, status, errors, metadata)
    - persist ImportEvent into audit store

- NotifyProcessor (optional)
  - send notifications to subscribers or job requesters based on configuration


### 4.3. ImportJob processors

- StartJobProcessor
  - set job.status = RUNNING; job.startedAt = now_utc(); persist job record

- ParsePayloadProcessor
  - validate job.payload is JSON object or array
  - if object: payloadList = [object]
  - if array: payloadList = array
  - if invalid: mark job as FAILED, create ImportEvent(jobTechnicalId, status=FAILURE, errors=["invalid-payload"]) and abort
  - return payloadList

- DispatchItemsProcessor
  - for each payload in payloadList (process can be parallelized):
    - create HackerNewsItem with fields: technicalId, originalJson=payload, id=payload.id, type=payload.type, status=PENDING, createdAt=now_utc(), sourceJobTechnicalId = job.technicalId
    - persist HackerNewsItem record (may be minimal record to represent attempt)
    - enqueue (or trigger) HackerNewsItem workflow (ProcessStartProcessor)
  - persist interim job.processedCount (number of dispatched items)

- AggregateResultsProcessor
  - query ImportEvents where jobTechnicalId == job.technicalId
  - job.processedCount = number of ImportEvents (or number of unique itemTechnicalId events depending on reporting policy)
  - job.failureCount = count of ImportEvents with status == FAILURE
  - persist job

- MarkJobCompleteProcessor
  - job.completedAt = now_utc()
  - job.status = COMPLETED if failureCount == 0 else FAILED
  - persist job
  - (optionally) create a summary ImportEvent for the job

---

## 5. API Endpoints and Contracts

API design rules reiterated:
- POST endpoints return only `technicalId` in the immediate response.
- GET endpoints return full stored entity representations.
- Both `technicalId` and HN `id` retrieval are supported for HackerNewsItem (GET by technical id and GET by HN id).

1) Create import job (orchestrator)
- POST /importJobs
- Request body:
  {
    "jobName": "single_item_import",
    "requestedBy": "alice",
    "payload": { ... }   // single HN JSON or array of HN JSON
  }
- Response:
  {
    "technicalId": "job-<uuid>"
  }

2) Get import job by technicalId
- GET /importJobs/technical/{technicalId}
- Response (example):
  {
    "technicalId": "job-<uuid>",
    "jobName": "...",
    "requestedBy": "...",
    "createdAt": "...",
    "startedAt": "...",
    "completedAt": "...",
    "status": "COMPLETED",
    "processedCount": 10,
    "failureCount": 1
  }

3) Create single HackerNewsItem (alternate endpoint)
- POST /hackerNewsItems
- Request body: full Hacker News JSON payload
- Response:
  {
    "technicalId": "item-<uuid>"
  }

4) Get HackerNewsItem by technicalId
- GET /hackerNewsItems/technical/{technicalId}
- Response (example):
  {
    "technicalId": "item-<uuid>",
    "id": 12345,
    "type": "story",
    "originalJson": { ... includes importTimestamp if persisted ... },
    "importTimestamp": "2025-08-18T12:34:56Z",
    "status": "READY",
    "createdAt": "...",
    "updatedAt": "...",
    "sourceJobTechnicalId": "job-<uuid>"
  }

5) Get HackerNewsItem by HN id (explicit requirement)
- GET /hackerNewsItems/by-id/{id}
- Response: the stored original JSON plus metadata, e.g.:
  {
    "technicalId": "item-<uuid>",
    "id": 12345,
    "type": "story",
    "by": "alice",
    "text": "...",
    "importTimestamp": "2025-08-18T12:34:56Z"
  }

6) Get ImportEvents for a job or item (audit)
- GET /importEvents?jobTechnicalId={job-<uuid>}
- GET /importEvents?itemTechnicalId={item-<uuid>}
- Response: list of ImportEvent objects (see entity schema)

---

## 6. Configuration & Extensibility

- Duplicate handling: configurable (OVERWRITE | SKIP | REJECT). Default: OVERWRITE.
- Per-job override: an ImportJob payload may include a `duplicatePolicy` field to override system default for that job.
- Retention policy: ImportEvent retention and archival TTL configurable.
- Parallelism: DispatchItemsProcessor may execute item workflow calls in parallel. AggregateResultsProcessor must cope with eventual consistency and timeouts.
- Observability: Log processors must emit structured logs (processor name, technical ids, durations, errors).

---

## 7. Errors, Status Codes & Failure Modes

- Validation errors for an item create ImportEvent(status=FAILURE) with error codes such as `validation-missing-id`, `validation-missing-type`, `invalid-json`.
- Job-level invalid payload -> HTTP 400 on POST /importJobs; job not created unless accepted. Alternatively, if accepted, job.status = FAILED with ImportEvent.
- Duplicate rejected -> item ImportEvent(status=FAILURE, errors=["duplicate-rejected"]) and item.status = FAILED.
- Persistence failure -> retry policy at processor level; if exhausted, ImportEvent(status=FAILURE) and item.status = FAILED.

---

## 8. Open Questions (resolved by defaults in this document)
1. On duplicate HN id: default set to OVERWRITE (configurable). If you prefer SKIP or REJECT as system default, please confirm and the workflows will be updated accordingly.
2. Audit/history: enabled — ImportEvent provides full history. Only one canonical HackerNewsItem per HN id is stored by default, but all attempts are auditable.
3. Bulk import reporting: ImportJob exposes aggregated counters (processedCount, failureCount). Detailed per-item failures are available via ImportEvent queries. If you want the GET /importJobs to embed per-item failure details by default, confirm and it will be added.

---

If you want any policy changed (duplicate default, job-level behaviors, or retention rules) say which policy to change and I will update the workflows and processors accordingly.