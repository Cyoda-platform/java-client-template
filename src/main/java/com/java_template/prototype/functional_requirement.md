# Functional Requirements — Nobel Laureates Ingestion & Notification

This document defines the functional requirements for a Java-based application that ingests Nobel laureates data from the OpenDataSoft API, processes and persists laureate records, and notifies subscribers according to subscription filters. It models three primary entities (Job, Laureate, Subscriber) and describes the event-driven workflows, processors, criteria, APIs, events and error-handling behaviors.

---

## 0. Summary of updates (what changed)
- Consolidated duplicate sections and removed repeated overview sentence.
- Clarified distinction between business id (id) and datastore technicalId returned by POST endpoints.
- Enumerated Job lifecycle states and clarified the conditions that cause transitions (including how retries and failures affect state transitions).
- Clarified that notification failures do not automatically fail a Job; they are recorded per-subscriber and influence subscriber lifecycle only.
- Specified retry/backoff semantics (configurable per Job and per Subscriber) including exponential backoff option.
- Clarified Laureate source id vs internal technicalId and ensured enrichment/deduplication logic is explicit about merge semantics.
- Clarified how events drive processing (what emits what) and which processors update which entities.

---

## Data Source
- Primary API Endpoint (ingestion):
  - https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records
  - The system must support pagination (limit, offset or cursor) and rate limiting handling (retry with backoff on HTTP 429).
  - Records are JSON objects; the ingestion processor maps source fields to Laureate model fields.

---

## 1. Entities

Note: Each persisted entity has two distinct ids:
- id — business id coming from input (for Jobs and Subscribers this may be user-supplied; for Laureate this is the source laureate id).
- technicalId — datastore-generated unique identifier returned by POST endpoints.

### Job
- Fields
  - technicalId: String (datastore id returned on creation; opaque to clients)
  - id: String (business job identifier, optional; if provided must be unique per tenant)
  - name: String
  - schedule: String (cron expression or human descriptor — used by scheduler)
  - sourceEndpoint: String (default: OpenDataSoft endpoint)
  - parameters: Map<String,Object> (limit, offset, filters, pageSize, etc.)
  - status: Enum (SCHEDULED, INGESTING, VALIDATING, TRANSFORMING, PERSISTING, SUCCEEDED, FAILED, NOTIFYING, NOTIFIED_SUBSCRIBERS, CANCELLED)
  - createdAt, startedAt, completedAt: String (ISO-8601)
  - processedRecordsCount: Integer
  - lastError: String (last error message/stack trace, optional)
  - attemptCount: Integer (number of times the Job ingestion has been attempted)
  - maxAttempts: Integer (max retry attempts for the Job)
  - subscriberFilters: Map<String,Object> (used to pre-filter subscribers for notifications)
  - retryPolicy: Map<String,Object> (per-job retry settings; e.g., { "maxRetries": 3, "initialBackoffSeconds": 60, "exponential": true })

- Notes
  - status reflects the overall orchestration state. Individual laureate processing status is tracked on the Laureate entity.
  - Notifications are triggered after persistence phase (see workflows).


### Laureate
- Fields
  - technicalId: String (internal datastore id)
  - id: Integer (source laureate id from OpenDataSoft — primary business id)
  - firstname: String
  - surname: String
  - name: String (full name/affiliation where applicable)
  - born: String (ISO-8601 or null)
  - died: String (ISO-8601 or null)
  - gender: String
  - borncountry: String
  - borncountrycode: String
  - borncity: String
  - year: String (award year)
  - category: String
  - motivation: String
  - city: String (affiliation city)
  - country: String (affiliation country)
  - ageAtAward: Integer (enriched — computed if born present)
  - normalizedCountryCode: String (enriched ISO code)
  - sourceFetchedAt: String (ISO-8601 timestamp when fetched)
  - status: Enum (RECEIVED, VALIDATED, ENRICHED, DEDUPLICATED, STORED, PUBLISHED, REJECTED)
  - duplicateOf: String (technicalId of record merged into, nullable)
  - validations: Map<String,String> (field -> message)

- Notes
  - Laureates are created only by Job processing (no POST /laureates endpoint).
  - status is updated by processors and determines further processing steps.
  - If deduplication identifies a duplicate, the system either merges fields or links to the canonical record and sets duplicateOf.


### Subscriber
- Fields
  - technicalId: String
  - id: String (business subscriber id, optional)
  - name: String
  - email: String (optional)
  - webhookUrl: String (optional)
  - channels: List<String> (EMAIL, WEBHOOK)
  - active: Boolean
  - filters: Map<String,Object> (categories, years, countries, etc.)
  - createdAt, verifiedAt, lastNotifiedAt: String (ISO-8601)
  - retryPolicy: Map<String,Object> (e.g., { "maxRetries": 3, "initialBackoffSeconds": 60, "exponential": true })
  - deliveryFailures: Integer (count of recent consecutive failures)

- Notes
  - Subscriber verification may be optional but recommended. Verified subscribers have verifiedAt populated.
  - Delivery failures influence subscriber lifecycle (see Subscriber workflow).

---

## 2. Workflows and state transitions

General rules:
- Workflows are event-driven. Each transition is driven by a processor that emits events and/or updates entity status.
- Automatic transitions are executed by the system. Manual transitions (CANCEL, RESTART, MANUAL_PUBLISH) require user action.
- When a processor fails mid-workflow, the Job.lastError must be set, attemptCount incremented, and retry policy consulted.
- Notification failures are recorded per-subscriber and per-delivery attempt and do not by themselves mark the Job as FAILED.


### Job lifecycle (high-level)
1. CREATED / SCHEDULED — Job created and scheduled. POST /jobs persists Job and returns technicalId.
2. INGESTING — IngestProcessor starts fetching records from sourceEndpoint.
3. VALIDATING / TRANSFORMING — Laureate-specific processors run (ValidationProcessor, EnrichProcessor, DeduplicateCriterion).
4. PERSISTING — PersistLaureateProcessor persists records to the datastore.
5. SUCCEEDED or FAILED — Determined by persistence/processing outcomes and applied business rules (see criteria).
6. NOTIFYING — NotifySubscribersProcessor sends notifications to matched subscribers (runs even if Job FAILED, unless explicitly cancelled).
7. NOTIFIED_SUBSCRIBERS — Notification attempts completed and Job records notification summary (success/failure counts).
8. CANCELLED — Manual cancellation by user; Job transitions out of active workflow.

State diagram (textual) — canonical status set: SCHEDULED -> INGESTING -> VALIDATING/TRANSFORMING -> PERSISTING -> {SUCCEEDED|FAILED} -> NOTIFYING -> NOTIFIED_SUBSCRIBERS

Transitions and behaviors:
- On ingestion start: set startedAt, status=INGESTING.
- While ingesting: IngestProcessor pages sourceEndpoint and emits LaureateReceived events for each record.
- After all laureates processed: evaluate AllRecordsPersistedCriterion. If satisfied -> SUCCEEDED; else -> FAILED.
- After SUCCEEDED or FAILED (processing complete), automatically start notifications unless job was CANCELLED.
- If FAILED and attemptCount < maxAttempts, RetryJobProcessor will schedule a re-run (increment attemptCount), applying backoff from retryPolicy.


### Laureate lifecycle
1. RECEIVED — emitted by IngestProcessor when a source record is parsed.
2. VALIDATED — ValidationProcessor verifies required fields; if invalid -> REJECTED.
3. ENRICHED — EnrichProcessor computes ageAtAward, normalizes country codes, resolves missing/corrected fields.
4. DEDUPLICATED — DeduplicateCriterion compares with existing records and either merges or marks duplicate.
5. STORED — PersistLaureateProcessor writes the laureate to the dataset (status = STORED).
6. PUBLISHED — PublishProcessor marks laureate visible to subscribers when business rules permit.
7. REJECTED — terminal state for records failing validation or business rules.

Notes:
- Validation errors are stored in laureate.validations; small issues may be warnings rather than rejection depending on rules.
- Deduplication merge semantics: canonical record is chosen; non-null fields from the incoming record may overwrite or be appended depending on merge strategy; duplicateOf points to the canonical technicalId.


### Subscriber lifecycle
1. CREATED — POST /subscribers persists subscriber and triggers verification (if configured).
2. VERIFYING — SubscriberVerificationProcessor sends verification (email or webhook). On success -> ACTIVE.
3. ACTIVE — subscriber can receive notifications.
4. SUSPENDED — repeated delivery failures or manual action suspends subscriber.
5. DELETED — manual deletion; system stops notifications and marks as deleted.

Delivery rules:
- On each notification attempt the DeliveryAttemptProcessor updates deliveryFailures.
- When deliveryFailures >= retryPolicy.maxRetries the subscriber enters SUSPENDED. Manual reactivation may clear counters.

---

## 3. Processors (responsibilities & pseudo behavior)

### IngestProcessor
- Purpose: retrieve data from sourceEndpoint (respecting parameters), parse JSON, and for each record:
  - map to Laureate payload,
  - persist initial Laureate with status=RECEIVED,
  - emit event LaureateReceived(technicalId or id)
- Must support pagination and rate limiting.

Pseudo:
```
class IngestProcessor {
  void process(Job job) {
    job.startedAt = now();
    job.status = INGESTING;
    int total = 0;
    for page in fetchPages(job.sourceEndpoint, job.parameters) {
      for item in page.items {
        Laureate l = map(item);
        l.sourceFetchedAt = now();
        saveInitial(l); // sets status RECEIVED
        emitEvent("LaureateReceived", l.technicalId);
        total++;
      }
    }
    job.processedRecordsCount = total;
  }
}
```


### ValidationProcessor
- Purpose: validate critical fields (id, firstname/surname or name, year, category) and date formats. Populate laureate.validations.
- Criteria for rejection vs warning are configurable.


### EnrichProcessor
- Purpose: compute ageAtAward when born present (ageAtAward = awardYear - bornYear), normalize country codes (ISO-3166), enrich missing affiliation fields where possible.


### DeduplicateCriterion / DeduplicateProcessor
- Purpose: apply deterministic matching (exact id) and fuzzy matching (name + year + category + affiliation similarity).
- On duplicate detection: choose canonical record, merge per merge strategy, set duplicateOf for incoming record, do not duplicate notifications for duplicates (unless policy allows).


### PersistLaureateProcessor
- Purpose: persist validated/enriched laureate. If record exists, merge or update depending on differencing rules.
- Emits LaureatePersisted event (including success/failure).

Pseudo:
```
class PersistLaureateProcessor {
  void process(Laureate l) {
    try {
      upsert(l);
      emitEvent("LaureatePersisted", l.technicalId);
    } catch (Exception e) {
      emitEvent("LaureatePersisted", l.technicalId, status=FAILED, error=e.message);
      throw e;
    }
  }
}
```


### NotifySubscribersProcessor
- Purpose: determine target subscribers (apply job.subscriberFilters and subscriber.filters), create per-subscriber payloads and deliver via preferred channels asynchronously.
- Behavior: record NotificationSent events per delivery attempt (success/failure). Failures do not mark the Job as FAILED but are summarized in job notification summary.
- Must respect subscriber.retryPolicy for retries on delivery failures.

Pseudo:
```
class NotifySubscribersProcessor {
  void process(Job job, List<Laureate> laureates) {
    List<Subscriber> subs = querySubscribers(job.subscriberFilters);
    for (Subscriber s : subs) {
      if (!s.active) continue;
      if (!matchesFilters(s, laureates)) continue;
      for (channel : s.channels) {
        deliverAsync(s, channel, payload);
      }
    }
    job.lastNotifiedAt = now();
  }
}
```


## 4. API Endpoints (functional behavior & contracts)

General rules:
- POST endpoints create orchestration/management entities and return technicalId only.
- Each POST triggers an event that starts the relevant process (via Cyoda process method).
- All POST-created entities support GET by technicalId.


Endpoints:

- POST /jobs
  - Purpose: Create and schedule an ingestion Job.
  - Request JSON: id (optional), name, schedule, sourceEndpoint (optional), parameters, maxAttempts, retryPolicy, subscriberFilters.
  - Response JSON: { "technicalId": "string" }
  - Behavior: persist Job with status=SCHEDULED, emit JobCreated event. If schedule indicates immediate run, scheduler or orchestrator will invoke Job.process automatically.

- GET /jobs/{technicalId}
  - Purpose: retrieve job, including status and metadata.
  - Response: full Job model (see entity definition).

- POST /subscribers
  - Purpose: register a subscriber and start verification workflow.
  - Request JSON: name, email (optional), webhookUrl (optional), channels, filters, retryPolicy.
  - Response JSON: { "technicalId": "string" }
  - Behavior: persist Subscriber with status=CREATED, emit SubscriberCreated event.

- GET /subscribers/{technicalId}
  - Purpose: retrieve subscriber details and status.

- GET /laureates/{id}
  - Purpose: retrieve stored laureate by source id (business id). Returns latest canonical merged record.
  - Note: No POST /laureates. Created only via Job processing.

---

## 5. Events and EDA specifics

Event list (core):
- JobCreated (payload: job technicalId, minimal metadata)
- JobStarted (job technicalId)
- LaureateReceived (payload: laureate technicalId, source metadata)
- LaureateValidated (payload: laureate technicalId, validation result)
- LaureatePersisted (payload: laureate technicalId, status)
- JobCompleted (payload: job technicalId, summary)
- SubscriberCreated (payload: subscriber technicalId)
- NotificationSent (payload: subscriber technicalId, laureate technicalId, channel, status, error?)

Flow rules:
- POST /jobs -> persist Job -> emit JobCreated -> orchestrator schedules Job.process(jobTechnicalId).
- IngestProcessor -> emits LaureateReceived per item.
- LaureateReceived -> triggers Validation/Enrich/Deduplicate -> leads to PersistLaureateProcessor -> emits LaureatePersisted.
- When all LaureatePersisted events are accounted, AllRecordsPersistedCriterion evaluates Job to SUCCEEDED or FAILED and emits JobCompleted.
- SubscriberCreated -> triggers SubscriberVerificationProcessor and subsequent lifecycle steps.

---

## 6. Criteria (checks and acceptance rules)
- ValidationPassedCriterion: laureate.validations empty or only warnings.
- AllRecordsPersistedCriterion: number of successful LaureatePersisted events == job.processedRecordsCount.
- PersistErrorCriterion: any persistence operation failing after retries.
- DeduplicateCriterion: matching rules and similarity thresholds determine duplicates.
- NotificationCompleteCriterion: all notification delivery attempts completed (success/failure recorded).
- VerificationSucceededCriterion: verification callback or token confirmation seen.
- DeliveryFailureCriterion: subscriber.deliveryFailures >= retryPolicy.maxRetries.

---

## 7. Error handling and retry semantics
- Any failed automatic transition must:
  - record job.lastError (message and optional stack trace),
  - increment job.attemptCount,
  - consult job.retryPolicy and either schedule a retry or transition job to FAILED when attempts exhausted.
- RetryJobProcessor behavior:
  - initialBackoffSeconds from policy; if exponential=true then backoff = initialBackoffSeconds * 2^(attemptCount-1).
  - schedule re-run using scheduler; maintain idempotency (e.g., by using job.attemptCount and/or lastError fingerprint).
- Notification failures:
  - recorded via NotificationSent events containing channel, status, and error.
  - subscriber.deliveryFailures incremented for consecutive failures; when reaching retryPolicy.maxRetries subscriber is SUSPENDED.
  - Job notification phase records totals but does not change job.status to FAILED for subscriber delivery failures.
- Persist/DB errors:
  - Should trigger immediate retry attempts per Job.retryPolicy.
  - If persistent failures occur beyond maxAttempts, mark Job FAILED and emit JobCompleted (with failure summary).

---

## 8. Implementation notes (tech stack & patterns)
- Language: Java
- JSON parsing: Jackson (recommended) or Gson
- Scheduling: Quartz or Spring Scheduler; Job.schedule stores cron expression where applicable.
- Asynchronous processing: use executors / message queues / non-blocking HTTP clients for ingestion and delivery.
- HTTP client: resilient client with retries and rate-limit handling (e.g., configurable timeouts, backoff on 429/5xx).
- Persistence: relational or document DB; upsert semantics for laureate persistence required.
- Logging & monitoring: all processors should emit structured logs and metrics (ingest rate, failures, notification counts).

---

## 9. Acceptance criteria / sanity checks
- POST /jobs returns technicalId and scheduling starts according to schedule.
- Job ingestion must page across the OpenDataSoft endpoint and produce LaureateReceived events for each record.
- Laureates with missing critical fields are REJECTED and do not appear in GET /laureates.
- Deduplication must prevent duplicate published records in typical duplication cases (same name, year, category).
- Notification system respects subscriber filters and records NotificationSent events for each attempt.
- Retry policies for jobs and subscribers must be configurable and observable via GET endpoints.

---

If you want changes to field names, lifecycle states, or event names, list specifics to update. Otherwise this file represents the latest clarified logic and will be used as the authoritative functional requirement for implementation.