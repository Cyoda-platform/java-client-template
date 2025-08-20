# Functional Requirements

This document describes the canonical functional requirements for the Job/Activity/Report pipeline. The original document contained inconsistent state names and status enums. The content below unifies terminology, clarifies lifecycle transitions, and expands processors, criteria and API behaviour to reflect the latest logic.

## 1. Summary

- Three primary entities: Job, Activity, Report.
- Jobs orchestrate ingestion and processing runs.
- Activities are raw events ingested from external sources and flow through validation, deduplication, indexing and processing.
- Reports are assembled from processed Activities and delivered to recipients.
- All state names and status enums are standardized and consistent across definitions and workflows.

---

## 2. Entity Definitions

All timestamps are ISO-8601 strings (UTC unless otherwise specified). Types shown are high-level; concrete storage types (DB columns, JSON, etc.) can be defined in implementation.

### Job
- technical_id: String (UUID or stable unique token returned by POST /jobs) - required
- schedule_date: String (date for the run, e.g. 2025-08-21) - required
- timezone: String (IANA timezone or offset, default: UTC) - optional
- status: Enum {CREATED, VALIDATED, SCHEDULED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED} - required
- created_by: String (user or system) - required
- parameters: Object - required (example keys: window_start, window_end, fakerest_endpoint, retry_policy)
- started_at: String (timestamp) - optional
- completed_at: String (timestamp) - optional
- result_counts: Object (summary counts, e.g. {activities: 120, reports: 1}) - optional
- attempts: Integer (number of orchestration attempts so far) - optional

Notes:
- status values map to the Job workflow states (see section 3).
- retry_policy in parameters controls job-level retries (max_attempts, backoff_seconds).

### Activity
- activity_id: String (source id from Fakerest or other provider) - required
- user_id: String (owner of activity) - required
- timestamp: String (event time) - required
- activity_type: String (type of event) - optional
- payload: Object (raw event payload) - required
- ingestion_status: Enum {RAW, VALIDATED, DEDUPED, INDEXED, PROCESSED, FAILED} - required
- source_job_id: String (technical_id of Job that ingested this activity) - optional
- normalized_at: String (timestamp when normalization completed) - optional
- dedupe_key: String (computed key used for deduplication; e.g. activity_id + user_id) - optional

Notes:
- dedupe_key must be deterministic and consistent across retries to enable idempotent ingestion.

### Report
- report_id: String (report business id or generated UUID) - required
- job_id: String (technical_id of originating Job) - required
- date: String (reporting date) - required
- generated_at: String (timestamp) - required
- summary_items: Array of objects ({pattern_type, metrics, confidence, details?}) - required
- recipient_email: String - optional
- delivery_status: Enum {DRAFT, READY, PUBLISHING, SENT, FAILED, ARCHIVED} - required
- delivery_attempts: Integer - optional
- last_delivery_response: Object (provider response, error codes) - optional

---

## 3. Status & State mapping

- Job status enum is used to indicate orchestration stage. Implementation may keep a separate state machine for transient states (VALIDATING, INGESTION_TRIGGERED) but the canonical status enum above should be stored on the Job entity.
- Activity ingestion_status represents pipeline progress for each event.
- Report delivery_status maps to the report lifecycle.

---

## 4. Workflows

Each workflow below is authoritative and consistent with the entity status enums.

### Job workflow (canonical)

1. CREATED (record created by POST /jobs)
2. VALIDATING (Validate parameters, windows, endpoints)
3. SCHEDULED (if job has a future schedule_date) or IN_PROGRESS (if immediate)
4. IN_PROGRESS (ingestion started, activities emitted)
5. COMPLETED (all processors finished, report generated and delivered or archived)
6. FAILED (terminal on unrecoverable error)
7. CANCELLED (manual cancel before completion)

Mermaid state diagram (canonical):

stateDiagram-v2
    [*] --> CREATED
    CREATED --> VALIDATING : ValidateJobProcessor (auto)
    VALIDATING --> SCHEDULED : if schedule_date in future
    VALIDATING --> IN_PROGRESS : immediate run
    SCHEDULED --> IN_PROGRESS : schedule trigger
    IN_PROGRESS --> COMPLETED : on success (all criteria met)
    IN_PROGRESS --> FAILED : on unrecoverable error
    IN_PROGRESS --> IN_PROGRESS : on retry (retry policy)
    FAILED --> [*]
    COMPLETED --> [*]
    CREATED --> CANCELLED : user cancel
    SCHEDULED --> CANCELLED : user cancel

Processors/Criteria for Job:
- Processors: ValidateJobProcessor, StartIngestionProcessor, MonitorIngestionCriterion, AssembleReportProcessor, PublishReportProcessor, ArchiveProcessor
- Criteria: IngestionCompleteCriterion (all expected events persisted and indexed), JobFailureCriterion, RetryPolicyCriterion

Behavioral notes:
- Jobs are idempotent: repeated POST with same idempotency key should not create duplicate runs.
- A Job may be retried automatically according to retry_policy; attempts count must be tracked.

### Activity workflow

1. RAW (ingested but not validated)
2. VALIDATED (schema validated & normalized)
3. DEDUPED (duplicates removed or flagged)
4. INDEXED (prepared for pattern detection / analytics)
5. PROCESSED (consumed by pattern detection/aggregation)
6. FAILED (validation/dedup or storage failures)

Mermaid diagram:

stateDiagram-v2
    [*] --> RAW
    RAW --> VALIDATED : ValidateActivityProcessor
    VALIDATED --> DEDUPED : DedupProcessor / DedupCriterion
    DEDUPED --> INDEXED : IndexForPatternProcessor
    INDEXED --> PROCESSED : PatternDetectionProcessor
    PROCESSED --> [*]
    RAW --> FAILED : validation error
    VALIDATED --> FAILED : dedupe/store error

Processors/Criteria for Activity:
- Processors: ValidateActivityProcessor, NormalizeProcessor, DedupProcessor, IndexForPatternProcessor, PatternDetectionProcessor
- Criteria: DedupCriterion (deterministic dedupe_key), StoredCriterion (persistence success)

Behavioral notes:
- Deduplication must be deterministic and idempotent using dedupe_key (activity_id + user_id by default).
- Activities should include source_job_id for traceability.
- Failed activities may be retained in a dead-letter store for later reprocessing.

### Report workflow

1. DRAFT (assembled but not ready)
2. READY (all items attached)
3. PUBLISHING (attempting delivery)
4. SENT (success)
5. FAILED (delivery failures; retry according to policy)
6. ARCHIVED (long-term storage)

Mermaid diagram:

stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> READY : AssembleReportProcessor
    READY --> PUBLISHING : PublishReportProcessor
    PUBLISHING --> SENT : on success
    PUBLISHING --> FAILED : on error (retryable)
    FAILED --> PUBLISHING : retry (if policy allows)
    SENT --> ARCHIVED : ArchiveProcessor / ArchiveCriterion
    ARCHIVED --> [*]

Processors/Criteria for Report:
- Processors: AssembleReportProcessor, PublishReportProcessor, ArchiveProcessor
- Criteria: DeliverySuccessCriterion (provider response 2xx), ArchiveCriterion

Behavioral notes:
- PublishReportProcessor should be idempotent. Use report_id and provider message-id to avoid duplicate sends.
- Delivery attempts and last_delivery_response must be recorded for observability.

---

## 5. Processor pseudo-code (updated)

ValidateJobProcessor:

function process(job){
  if missing(job.parameters) throw ValidationError
  if invalid window range or schedule_date format throw ValidationError
  job.status = VALIDATED
  return job
}

StartIngestionProcessor:

function process(job){
  // prepare ingestion: compute window, idempotency key
  events = fetchEvents(job.parameters)
  for each event in events {
    activity = buildActivityFromEvent(event, source_job_id=job.technical_id)
    // publish to ingestion queue/topic (guarantee at-least-once)
    publishToQueue(activity)
  }
  job.started_at = now()
  job.status = IN_PROGRESS
  return job
}

ValidateActivityProcessor / NormalizeProcessor:

function process(activity){
  try {
    validateSchema(activity.payload)
    activity.payload = normalize(activity.payload)
    activity.ingestion_status = VALIDATED
    activity.dedupe_key = computeDedupeKey(activity)
    persistActivityMetadata(activity)
  } catch (e) {
    activity.ingestion_status = FAILED
    writeToDeadLetter(activity, e)
  }
  return activity
}

DedupProcessor:

function process(activity){
  if existsInStore(activity.dedupe_key) {
    markAsDuplicate(activity)
    activity.ingestion_status = DEDUPED
    // either drop payload or keep reference to original
  } else {
    persistUniqueActivity(activity)
    activity.ingestion_status = DEDUPED
  }
  return activity
}

IndexForPatternProcessor / PatternDetectionProcessor:

function process(activity_or_batch){
  indexForQueries(activity_or_batch) // e.g., store in analytics DB
  activity_or_batch.status = PROCESSED
}

AssembleReportProcessor:

function process(job){
  activities = readValidatedActivities(job.parameters.window)
  summaries = detectPatterns(activities)
  report = createReport(job_id=job.technical_id, summary_items=summaries, generated_at=now())
  report.delivery_status = READY
  persistReport(report)
  return report
}

PublishReportProcessor:

function process(report){
  if report.delivery_status == SENT then return report // idempotent
  response = mailProvider.send(report.recipient_email, report.payload)
  if response.success {
    report.delivery_status = SENT
  } else {
    report.delivery_status = FAILED
    recordDeliveryError(report, response)
    if shouldRetry(report) scheduleRetry(report)
  }
  persistReport(report)
  return report
}

---

## 6. Criteria examples (clarified)

- IngestionCompleteCriterion: true when a) all expected activities in the configured window are persisted and indexed OR b) a timeout has expired and partial results allowed per job parameters.
- DedupCriterion: two events considered duplicate when dedupe_key (default activity_id+user_id normalized) matches and timestamp within allowed duplicate window (configurable).
- DeliverySuccessCriterion: provider response code in 2xx and returned message-id present.
- RetryPolicyCriterion: consult job.parameters.retry_policy (max_attempts, backoff strategy) to decide requeue.

---

## 7. API Endpoints (design / behaviour)

POST /jobs
- Creates Job orchestration (triggers EDA run or schedules it).
- Idempotency: callers should send Idempotency-Key header. If a job with same key exists, return existing technical_id and 200/202 depending on state.
- Request (example):
{
  "schedule_date":"2025-08-21",
  "timezone":"UTC",
  "created_by":"system",
  "parameters":{"window_start":"2025-08-20T00:00:00Z","window_end":"2025-08-20T23:59:59Z","fakerest_endpoint":"https://fakerest/api/events","retry_policy":{"max_attempts":3}}
}
- Response: 202 Accepted (if scheduled or queued) with body:
{ "technicalId":"job-abc-123", "status":"SCHEDULED" }

GET /jobs/{technicalId}
- Returns canonical Job entity view.
- Example response:
{
  "technicalId":"job-abc-123",
  "schedule_date":"2025-08-21",
  "status":"COMPLETED",
  "started_at":"2025-08-21T00:00:00Z",
  "completed_at":"2025-08-21T00:10:00Z",
  "result_counts":{"activities":120,"reports":1}
}

GET /activities/{activity_id}
- Returns Activity metadata and ingestion_status.
- Example response:
{
  "activity_id":"evt-1",
  "user_id":"user-7",
  "timestamp":"2025-08-20T12:00:00Z",
  "activity_type":"login",
  "ingestion_status":"VALIDATED",
  "dedupe_key":"evt-1_user-7"
}

GET /reports/{report_id}
- Returns Report and delivery status.
- Example response:
{
  "report_id":"rpt-2025-08-20",
  "job_id":"job-abc-123",
  "date":"2025-08-20",
  "generated_at":"2025-08-21T01:00:00Z",
  "delivery_status":"SENT",
  "summary_items":[{"pattern_type":"peak_usage","metrics":{"peak_hour":12},"confidence":0.9}]
}

Notes:
- POST returns 202 if work is asynchronous; include Location header for GET endpoint.
- API must surface sufficient info for observability (status, counts, timestamps) but not necessarily full payloads by default (support ?includePayload=true for heavy items).

---

## 8. Observability, Errors and Retries

- All processors must emit structured events (success/failure) to monitoring and tracing.
- Job-level and report-level retry policies must be configurable and recorded.
- Dead-letter queues for activity-level failures must be supported.
- Idempotency keys and dedupe_keys are primary mechanisms to avoid duplicates.

---

## 9. Data retention and archival

- Reports older than X days (configurable) should be archived and delivery_status set to ARCHIVED.
- Activities may be retained in raw form for a configurable retention period; after that, store aggregated indices only.

---

## 10. Extension notes

- If a separate PatternSummary entity is required for more detailed analytics, up to 10 entities can be introduced (PatternSummary, Task, Worker, IngestionBatch, etc.).



End of functional requirements.
