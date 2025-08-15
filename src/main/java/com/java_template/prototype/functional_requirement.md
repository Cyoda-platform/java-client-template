# Functional Requirements (Updated)

Last updated: 2025-08-15

This document describes the entities, workflows, processors/criteria, and API semantics for ingesting Hacker News items (HNItem), orchestration via IngestJob, and producing ValidationRecord artifacts. It reconciles entity field definitions, canonical workflow states, processor behavior, duplicate handling, and API responses.

---

## 1. Entity Definitions (canonical)

- HNItem
  - technicalId: String (system-generated unique id for this stored record)
  - hnId: Long (Hacker News item id from incoming JSON; may be null if not present)
  - type: String (item type as provided in incoming JSON)
  - rawJson: String (original JSON payload as received; stored unchanged)
  - importTimestamp: String (ISO8601 timestamp added by system when enriched)
  - status: String (one of: RECEIVED, VALIDATING, INVALID, VALIDATED, ENRICHED, STORED)
  - errorMessage: String (validation or processing error, if any)
  - createdAt: String (ISO8601, when the technical record was created)
  - updatedAt: String (ISO8601, last update time)
  - version: Integer (incremented on updates)

- IngestJob
  - technicalId: String (system-generated id for the job)
  - source: String (origin of job, e.g. "manual", "api", "scheduled")
  - payload: String (optional bulk JSON payload as received)
  - createdAt: String (ISO8601)
  - updatedAt: String (ISO8601)
  - status: String (one of: CREATED, SPLITTING, ENQUEUED, MONITORING, COMPLETED, FAILED)
  - createdItemTechnicalIds: List[String] (technicalIds of HNItem entities created or updated by this job)
  - resultSummary: Map (optional summary: counts of stored/invalid/updated items)

- ValidationRecord
  - technicalId: String (system-generated id for this validation record)
  - hnId: Long? (if present in incoming data)
  - hnItemTechnicalId: String? (reference to HNItem.technicalId when available)
  - isValid: Boolean
  - missingFields: List[String]
  - checkedAt: String (ISO8601)
  - message: String (human readable explanation)
  - createdAt: String (ISO8601)

Notes:
- technicalId fields are returned to API clients and used internally to reference entities. hnId corresponds to Hacker News numeric id field in payloads when present.
- The HNItem.status enumerations are canonical and used by processors and monitor criteria.

---

## 2. Canonical Workflows and State Machines

High-level description: HNItem and IngestJob have state machines that describe automatic processors and manual actions. The state names used here are consistent across the system.

### HNItem workflow (canonical)

Lifecycle steps: RECEIVED -> VALIDATING -> (INVALID | VALIDATED) -> ENRICHED -> STORED

- Initial state: RECEIVED (when POST creates HNItem or when EnqueueItemsProcessor creates an HNItem for ingest)
- VALIDATING: ValidateRequiredFieldsProcessor runs and creates a ValidationRecord.
- INVALID: if required fields missing; HNItem.errorMessage set and remains until manual fix or reingest.
- VALIDATED: item passed validation (basic required fields present)
- ENRICHED: EnrichImportTimestampProcessor adds importTimestamp and other computed metadata
- STORED: PersistItemProcessor saves data to persistence layer and sets technical id if newly created or updates existing record

State diagram (conceptual):

```
[*] --> RECEIVED
RECEIVED --> VALIDATING : onCreate / enqueue
VALIDATING --> INVALID : validationFailed
VALIDATING --> VALIDATED : validationPassed
VALIDATED --> ENRICHED : enrichment
ENRICHED --> STORED : persist
STORED --> [*]
```

Processors/Criteria for HNItem (summary):
- ValidateRequiredFieldsProcessor: Runs automatically in VALIDATING; creates ValidationRecord and routes to INVALID or VALIDATED.
- EnrichImportTimestampProcessor: Runs automatically when VALIDATED; sets importTimestamp, updatedAt and moves to ENRICHED.
- PersistItemProcessor: Persists HNItem (create or update) and moves to STORED.
- ManualFixAction: Operator/UI triggered action to update rawJson, then re-run VALIDATING.

Notes about statuses:
- 'VALID' in older documentation is replaced by 'VALIDATED' to avoid ambiguity with boolean flags.
- HNItem.status should always reflect the last completed processing step.

### IngestJob workflow (canonical)

Lifecycle steps: CREATED -> SPLITTING -> ENQUEUED -> MONITORING -> (COMPLETED | FAILED)

- CREATED: job created and persisted
- SPLITTING: SplitPayloadProcessor runs to extract individual item payloads
- ENQUEUED: EnqueueItemsProcessor creates HNItem entities (each HNItem starts its own workflow)
- MONITORING: MonitorItemsCriterion polls item statuses to detect completion or failure
- COMPLETED: When all tracked items are STORED (or updated as per duplicate handling)
- FAILED: If timeout occurs or job-level failure

State diagram (conceptual):

```
[*] --> CREATED
CREATED --> SPLITTING : SplitPayloadProcessor
SPLITTING --> ENQUEUED : EnqueueItemsProcessor
ENQUEUED --> MONITORING : begin monitoring
MONITORING --> COMPLETED : allStored
MONITORING --> FAILED : timeout or failure
```

Processors/Criteria for IngestJob:
- SplitPayloadProcessor
- EnqueueItemsProcessor
- MonitorItemsCriterion
- AllItemsStoredCriterion
- TimeoutCriterion

Notes:
- createdItemTechnicalIds will contain the technicalId for every HNItem created or updated for the job. If an incoming HN payload matches an existing stored hnId, behavior described in the "Duplicate handling" section below applies and the existing technicalId will be referenced.

### ValidationRecord workflow

Lifecycle: CREATED -> STORED -> (NOTIFIED)


```
[*] --> CREATED
CREATED --> STORED : PersistValidationRecordProcessor
STORED --> NOTIFIED : NotifyOperatorProcessor (optional/manual)
NOTIFIED --> [*]
```

Processors/Criteria for ValidationRecord:
- PersistValidationRecordProcessor
- NotifyOperatorProcessor (optional / operator workflow)

---

## 3. Processor pseudocode (updated and canonical)

The pseudocode below uses the canonical statuses and implements the duplicate handling and API response rules described later.

ValidateRequiredFieldsProcessor
```
process(hnItem):
  set hnItem.status = "VALIDATING"
  payload = hnItem.rawJson
  parsed = parseJson(payload)
  missing = []
  if parsed.get("id") is missing or null:
    missing.add("id")
  else:
    hnItem.hnId = asLong(parsed.get("id"))
  if parsed.get("type") is missing or null:
    missing.add("type")
  if missing not empty:
    record = new ValidationRecord(
      hnId = hnItem.hnId,
      hnItemTechnicalId = hnItem.technicalId,
      isValid = false,
      missingFields = missing,
      checkedAt = now_iso(),
      message = "Missing required fields: " + join(missing, ", ")
    )
    persist(record)
    hnItem.status = "INVALID"
    hnItem.errorMessage = record.message
    persist(hnItem)
    emitEvent("ValidationFailed", hnItem.technicalId)
  else:
    record = new ValidationRecord(... isValid=true ...)
    persist(record)
    hnItem.status = "VALIDATED"
    hnItem.errorMessage = null
    persist(hnItem)
    emitEvent("ValidationPassed", hnItem.technicalId)
```

EnrichImportTimestampProcessor
```
process(hnItem):
  // assumes status == "VALIDATED"
  hnItem.importTimestamp = now_iso()
  hnItem.updatedAt = now_iso()
  // keep existing status to indicate enrichment step completed
  hnItem.status = "ENRICHED"
  persist(hnItem)
  emitEvent("Enriched", hnItem.technicalId)
```

PersistItemProcessor
```
process(hnItem):
  // key decision: duplicate handling is idempotent by hnId
  if hnItem.hnId is not null:
    existing = findByHnId(hnItem.hnId)
    if existing exists:
      // update existing record (idempotent):
      existing.rawJson = hnItem.rawJson
      existing.type = hnItem.type
      existing.importTimestamp = hnItem.importTimestamp
      existing.updatedAt = now_iso()
      existing.version = existing.version + 1
      existing.status = "STORED"
      persist(existing)
      technicalId = existing.technicalId
      hnItem.technicalId = technicalId
      emitEvent("Persisted", technicalId)
      return technicalId
  // otherwise create new record
  hnItem.createdAt = now_iso()
  hnItem.updatedAt = now_iso()
  hnItem.version = 1
  hnItem.status = "STORED"
  if hnItem.technicalId is null: hnItem.technicalId = generateTechnicalId()
  persist(hnItem)
  emitEvent("Persisted", hnItem.technicalId)
  return hnItem.technicalId
```

SplitPayloadProcessor (for IngestJob)
```
process(ingestJob):
  ingestJob.status = "SPLITTING"
  persist(ingestJob)
  items = parseBulkPayload(ingestJob.payload) // expects array of JSON objects
  technicalIds = []
  for itemJson in items:
    hnItem = new HNItem(
      technicalId = generateTechnicalId(),
      rawJson = itemJson,
      hnId = tryExtractId(itemJson),
      status = "RECEIVED",
      createdAt = now_iso()
    )
    persist(hnItem)
    technicalIds.add(hnItem.technicalId)
    emitEvent("HNItemCreated", hnItem.technicalId)
  ingestJob.createdItemTechnicalIds = technicalIds
  ingestJob.status = "ENQUEUED"
  ingestJob.updatedAt = now_iso()
  persist(ingestJob)
  emitEvent("ItemsEnqueued", ingestJob.technicalId)
```

EnqueueItemsProcessor either included in SplitPayloadProcessor or implemented to create HNItems and trigger their workflows.

MonitorItemsCriterion
```
evaluate(ingestJob):
  ingestJob.status = "MONITORING"
  persist(ingestJob)
  for tid in ingestJob.createdItemTechnicalIds:
    state = fetch HNItem.status by technicalId tid
    if state == "INVALID":
      // a job-level decision: treat INVALID as blocking until manual fix
      return false
    if state in ["RECEIVED", "VALIDATING", "VALIDATED", "ENRICHED"]:
      return false
  // All items are STORED (or no tracked items)
  return true
```

Notes on Monitor behavior:
- Monitor will consider an item complete when status == STORED.
- If any item is INVALID, Monitor will block completion until manual fix or a configured policy (e.g. allow incomplete) is applied.

---

## 4. API Endpoints and Response Rules (definitive)

Rules:
- POST endpoints create or enqueue entities and return technicalId(s) only in the response.
- GET endpoints by technicalId available for all POST-created entities.
- GET by condition is available for HNItem (e.g. query by hnId) and returns stored results only.
- GET responses include the original rawJson unchanged and a separate metadata object containing importTimestamp, status, errorMessage, createdAt, updatedAt, version, and technicalId.
- Only entities in STORED (HNItem) or terminal job states (COMPLETED/FAILED) should be returned by GET endpoints. For in-flight items, GET by technicalId will return current state and metadata.

Endpoints (examples):

- POST /hn-items
  - Request JSON: { "rawJson": "{...}" }
  - Response JSON: { "technicalId": "string" }
  - Behavior: creates an HNItem with status RECEIVED and triggers validation/enrichment/persist processors.

- GET /hn-items/{technicalId}
  - Response JSON example:
    {
      "technicalId": "...",
      "metadata": {
        "hnId": 12345,
        "type": "story",
        "importTimestamp": "2025-08-15T12:00:00Z",
        "status": "STORED",
        "errorMessage": null,
        "createdAt": "2025-08-15T11:59:00Z",
        "updatedAt": "2025-08-15T12:00:00Z",
        "version": 1
      },
      "rawJson": "{ ... }"
    }

- GET /hn-items?itemId=12345
  - Behavior: query by hnId (Hacker News id). Returns list of matching stored HNItems (most recent first), each with metadata + rawJson as above.
  - Response JSON: array of the objects described in GET by technicalId. If the system is configured to maintain one record per hnId, then at most one result will be returned.

- POST /ingest-jobs
  - Request JSON: { "source": "manual", "payload": "[{...},{...}]" }
  - Response JSON: { "technicalId": "string" }
  - Behavior: creates an IngestJob and starts SPLITTING -> ENQUEUED -> MONITORING.

- GET /ingest-jobs/{technicalId}
  - Response JSON example:
    {
      "technicalId": "...",
      "status": "COMPLETED",
      "createdItemTechnicalIds": ["tid-1", "tid-2"],
      "resultSummary": { "stored": 2, "invalid": 0 }
    }

Notes / Decisions implemented in this document:
- API returns original rawJson unchanged and exposes importTimestamp and other computed fields in a separate metadata object.
- Duplicate handling (hnId collisions): The system is idempotent by hnId. If an incoming item has a hnId that already exists in the system, PersistItemProcessor will update the existing stored record (incrementing version, updating rawJson/importTimestamp/updatedAt) and return the existing technicalId. This avoids unbounded duplicates for the same Hacker News id while preserving a single canonical technicalId for that hnId. This behavior can be changed to "always create new technicalId" or to "reject duplicates" by configuration; the default in this document is update-in-place.

---

## 5. Operator/Manual actions and alerts

- ManualFixAction: operator updates rawJson or corrects missing fields on an INVALID HNItem, and triggers re-validation (transition back to VALIDATING).
- NotifyOperatorProcessor: optional notification when ValidationRecord is stored for INVALID items. The system should support administrative alerts for repeated failures or for ingestion job-level failures.

---

## 6. Open questions / optional configurations (for stakeholders)

1. Duplicate strategy configuration: The document currently defines update-in-place (idempotent by hnId). Stakeholders may require alternative behavior (reject duplicates or create new versioned records). Implement as a configuration switch if desired.
2. Validation strictness: Additional validations (schema conformance, type checks) can be added to ValidateRequiredFieldsProcessor or as separate processors.
3. Monitor policy: Whether a job should fail if any item is INVALID, or whether it should complete with a summary listing invalid items. Current default: block completion until all items are STORED (operator must fix invalid items) — consider a configurable tolerance/flag on IngestJob.
4. Retention/versioning: If update-in-place is used, do you need full version history of rawJson? If yes, persist previous versions in a separate store or enable versioned records.

---

If you confirm (or request changes to) the two previously-open questions:
- whether responses should return rawJson only or rawJson + metadata: this document fixes that to return rawJson + metadata (rawJson unchanged) — confirm if you want a different shape.
- how to handle duplicate hnId on save: this document sets default to update-in-place (idempotent) — confirm if you want reject or create-new behavior.

Once confirmed, the entity definitions, transitions, and API examples above are final and can be translated to the Cyoda model and implementation code.