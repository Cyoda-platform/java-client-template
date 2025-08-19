# Functional Requirements

This document defines the current functional requirements for the CSV ingestion, analysis orchestration, and subscriber notification prototype. It replaces/updates the earlier definition to reflect the latest logic and behaviors (idempotency, retries, reporting lifecycle, notification channels, API semantics, and operational concerns).

---

## Table of Contents

- 1. Entity Definitions
  - CSVFile
  - AnalysisJob
  - Subscriber
  - (Optional) Report
- 2. Entity Workflows (states and transitions)
  - CSVFile workflow
  - AnalysisJob workflow
  - Subscriber workflow
- 3. Processors, Criteria and Retry Policies
  - Processor responsibilities and pseudocode updates
- 4. API Endpoints and Rules
  - Semantic rules (HTTP status, idempotency, errors)
  - Endpoints and example request/response shapes
- 5. Operational and Non-functional Rules
  - Security, quotas, retention, monitoring
- 6. Decisions & Open Questions

---

## 1. Entity Definitions

All entities use `id` as a UUID string (technicalId). Timestamps are UTC ISO-8601 strings.

1. CSVFile

- id: string (UUID, technicalId)
- version: integer (optimistic concurrency control)
- source_type: string (upload | url | s3 | email | recurring_remote) -- indicates origin and ingestion semantics
- source_location: string (upload path or URL or email metadata or remote schedule identifier)
- source_metadata: map (provider keys, e.g. s3 bucket/key, email headers, remote polling id)
- filename: string (original filename)
- content_type: string (e.g. text/csv, application/vnd.ms-excel)
- size_bytes: long
- checksum: string (optional e.g. sha256 for dedup/idempotency)
- detected_schema: map (column name -> inferred type and sample values)
- row_count: integer (rows counted after ingest)
- sampled_rows: array (small sample used for schema detection)
- status: string (PENDING | VALIDATING | STORED | INVALID | ARCHIVED | DELETED)
- validation_errors: list of strings (detailed validation/error messages)
- ingestion_attempts: integer
- last_attempt_at: datetime
- uploaded_at: datetime (first ingestion attempt)
- stored_at: datetime (when stored successfully)
- archived_at: datetime (when archived)
- retention_policy_days: integer (optional)
- storage_uri: string (where the canonical file is stored, e.g. s3://...)

Notes:
- `PENDING` is initial. `VALIDATING` is actively validated. `STORED` indicates successful ingestion and persisted canonical storage. `INVALID` contains reasons. `ARCHIVED` indicates moved to long-term store; `DELETED` terminal.

2. AnalysisJob

- id: string (UUID)
- job_name: string
- csvfile_id: string (linked CSVFile id)
- analysis_type: string (summary | timeseries | anomaly | custom)
- parameters: map (group_by, metrics, date_range, thresholds, custom_spec)
- schedule: string (on_upload | manual | cron | recurring_remote) -- `cron` must be a validated cron expression
- trigger_type: string (on_upload | schedule | manual | api) -- used to decide scheduling behavior
- priority: integer (0..N; higher executes earlier)
- status: string (PENDING | VALIDATING | QUEUED | SCHEDULED | WAITING_FOR_CSV | RUNNING | RETRYING | COMPLETED | FAILED | CANCELLED | ARCHIVED)
- validation_errors: list of strings
- retries: integer (configured maximum retries)
- retry_count: integer (current retry attempts)
- backoff_strategy: string (none | fixed | exponential)
- next_run_at: datetime (calculated for scheduled jobs)
- started_at: datetime
- last_attempt_at: datetime
- completed_at: datetime
- report_id: string (optional, see Report entity)
- report_location: string (URI where the generated report is stored)
- report_status: string (PENDING | GENERATING | READY | FAILED)
- report_summary: string
- created_at: datetime
- archived_at: datetime

Notes:
- Jobs point to a CSVFile but may be scheduled or triggered. If CSV not available or not in STORED state, job moves to WAITING_FOR_CSV and optionally retries or fails depending on configuration.

3. Subscriber

- id: string (UUID)
- email: string (nullable if using webhook/channel only)
- name: string
- channels: list of strings (email | webhook | s3_push) -- extensible delivery channels
- webhook_url: string (optional)
- subscribed_jobs: list of string (job ids or job patterns / tags)
- preferred_format: string (pdf | html | csv | json)
- frequency: string (immediate | daily | weekly | digest) -- affects batching
- status: string (PENDING_VERIFICATION | ACTIVE | UNSUBSCRIBED | BOUNCED | SUSPENDED)
- verified: boolean
- verification_token: string (for email verification / expiry)
- verification_sent_at: datetime
- bounced_count: integer
- last_delivery_at: datetime
- delivery_preferences: map (e.g. timezone, business_hours_only)
- created_at: datetime

Notes:
- `PENDING_VERIFICATION` used if verification required. `BOUNCED` triggers delivery backoff or suspension.

4. (Optional) Report entity (recommended to separate report lifecycle)

- id: string (UUID)
- analysis_job_id: string
- status: string (GENERATING | READY | UPLOADING | FAILED | EXPIRED)
- location: string (URI)
- content_type: string
- size_bytes: integer
- summary: string
- generated_at: datetime
- expires_at: datetime
- delivery_attempts: integer
- created_at: datetime

Notes:
- Separating Report decouples job execution from report storage and notification workflows.

---

## 2. Entity workflows

High-level rules:
- All state transitions are driven by processors and events. Events are emitted for important state changes (e.g. CSV STORED, Job COMPLETED, Report READY).
- Each state transition should be idempotent. Use version or compare-and-swap for updates to avoid duplicate transitions.
- Retries and backoff are policy-driven and configurable at job or system level.

### CSVFile workflow

1. Initial: On POST /csvfiles (or remote polling), CSVFile is created with status = PENDING and ingestion_attempts = 0.
2. VALIDATING: CSVIngestProcessor starts validation (parsing, schema detection, size checks, content type checks, dedup via checksum).
3. Outcomes:
   - VALID -> STORED: persisted canonical copy written, `storage_uri` set, `stored_at` set, status -> STORED, emit event CSV_STORED. Record row_count and detected_schema.
   - INVALID -> INVALID: detailed validation_errors captured, status -> INVALID, emit CSV_INVALID.
   - TRANSIENT FAILURE -> PENDING/VALIDATING with incremented ingestion_attempts and last_attempt_at, apply retry/backoff. If attempts exceed threshold -> INVALID.
4. From STORED -> ARCHIVED (after retention window or housekeeping) or DELETED (manual or retention policy).

State diagram (conceptual):

[PENDING] -> VALIDATING -> { STORED | INVALID }
STORED -> ARCHIVED -> DELETED
VALIDATING -> PENDING (on transient failure + retry)

Processors and criteria (updated):
- Processors: CSVIngestProcessor, CSVValidationProcessor, SchemaDetectionProcessor, DeduplicationProcessor, StorageWriterProcessor, ValidationFailureProcessor
- Criteria: IsSchemaValidCriterion, IsFileParsableCriterion, IsChecksumDuplicateCriterion, IsWithinSizeLimitCriterion

Key behaviors:
- deduplicate using checksum and source metadata to support idempotent re-uploads
- enforce max size and accepted content types
- provide sampled rows and schema for downstream job validation

### AnalysisJob workflow

1. Initial: Job created via API with status = PENDING (or SCHEDULED immediately if schedule provided). Creation validates request syntactically.
2. VALIDATING: AnalysisValidationProcessor validates parameters and verifies CSV existence and readiness, or sets WAITING_FOR_CSV.
3. If validation passes -> QUEUED or SCHEDULED (depending on trigger/schedule). If fails -> FAILED with reasons.
4. QUEUED/SCHEDULED -> RUNNING when executor picks the job (respect priority, concurrency limits).
5. RUNNING -> COMPLETED with report generated and report entity/uri populated, or -> FAILED with errors.
6. On transient failures, job may move to RETRYING (respecting retries/backoff). After max retries -> FAILED.
7. COMPLETED -> triggers NotificationProcessor to deliver reports per Subscriber config. After notification and retention, job may be ARCHIVED.

State diagram (conceptual):

PENDING -> VALIDATING -> { QUEUED | FAILED | WAITING_FOR_CSV }
QUEUED -> SCHEDULED -> RUNNING -> { COMPLETED -> NOTIFICATION -> ARCHIVED, FAILED }
RUNNING -> RETRYING -> RUNNING (until retries exhausted)

Processors and criteria (updated):
- Processors: AnalysisValidationProcessor, ValidationSuccessProcessor, SchedulerProcessor, AnalysisExecutorProcessor, ResultAggregatorProcessor, ReportGeneratorProcessor, NotificationProcessor, FailureHandlerProcessor, RetryProcessor
- Criteria: IsCsvAvailableCriterion, CheckReportReadyCriterion, IsScheduleDueCriterion, IsRetryAllowedCriterion

Key behaviors:
- If CSVFile is not in STORED state: job goes to WAITING_FOR_CSV and subscribes to CSV_STORED event to resume.
- Jobs respect configured priority and concurrency limits.
- Job execution is idempotent: repeated runs should detect existing report (via job.report_id or checksum) and avoid duplicate report generation.
- Report generation may be async: job.status may be COMPLETED while report_status == GENERATING; notifications wait for report_status == READY.

### Subscriber workflow

1. On POST /subscribers, Subscriber is created in PENDING_VERIFICATION (if verification required) or ACTIVE.
2. If verification required: send verification email/webhook and set verification_token and verification_sent_at. On success -> ACTIVE; on repeated failures -> UNSUBSCRIBED or SUSPENDED.
3. ACTIVE -> UNSUBSCRIBED (manual) or BOUNCED/SUSPENDED (delivery failures). Optionally re-verify or allow re-subscription.
4. Notifications follow delivery preferences (frequency, channels, business hours). Batched deliveries are supported for daily/weekly digests.

State diagram (conceptual):

PENDING_VERIFICATION -> ACTIVE -> { UNSUBSCRIBED | BOUNCED | SUSPENDED }

Processors and criteria:
- Processors: EmailVerificationProcessor, ManualUnsubscribeProcessor, DeliveryFailureProcessor, SubscriberPreferencesProcessor
- Criteria: EmailVerifiedCriterion, DeliveryRetryCriterion

Notification delivery logic:
- Skip subscribers that are UNSUBSCRIBED or not verified.
- On delivery failure, increment bounced_count and apply retry/backoff. After threshold, mark BOUNCED/SUSPENDED and stop attempts.
- Respect frequency: immediate triggers per job; daily/weekly aggregated deliveries include multiple job reports into single digest.

---

## 3. Processors, Criteria and Pseudo-code (updated)

General rules for processors:
- Processors must be idempotent and safe to run multiple times.
- Emit domain events for major state transitions.
- Use optimistic concurrency/versioning for updates.
- Handle transient errors with retries according to policy; permanent errors should be recorded.

CSVValidationProcessor (improved)

```
class CSVValidationProcessor {
  void process(CSVFile csv) {
    if csv.ingestion_attempts >= MAX_ATTEMPTS {
      csv.status = INVALID
      csv.validation_errors.add("max attempts exceeded")
      persist(csv)
      emitEvent(csv.id, "CSV_INVALID")
      return
    }

    csv.status = VALIDATING
    csv.ingestion_attempts += 1
    csv.last_attempt_at = now()
    persist(csv)

    if not isSupportedContentType(csv.content_type) then
      recordValidationError(csv, "unsupported content type")
      csv.status = INVALID
      persist(csv)
      emitEvent(csv.id, "CSV_INVALID")
      return
    if not parseable(csv.source_location) then
      maybeRetryOrInvalidate(csv, "cannot parse")
      return

    csv.detected_schema = detectSchema(csv)
    csv.sampled_rows = sampleRows(csv)
    csv.row_count = countRows(csv)

    if isChecksumDuplicate(csv.checksum) then
      // idempotency: link to existing storage rather than duplicate
      csv.storage_uri = lookupExistingStorage(csv.checksum)
      csv.status = STORED
      csv.stored_at = now()
      persist(csv)
      emitEvent(csv.id, "CSV_STORED")
      return
    }

    csv.storage_uri = writeToCanonicalStorage(csv)
    csv.stored_at = now()
    csv.status = STORED
    persist(csv)
    emitEvent(csv.id, "CSV_STORED")
  }
}
```

AnalysisExecutorProcessor (improved with idempotency and retries)

```
class AnalysisExecutorProcessor {
  void process(AnalysisJob job) {
    if job.status == COMPLETED and job.report_id != null then return // idempotent

    csv = fetchCsv(job.csvfile_id)
    if csv == null or csv.status != STORED {
      job.status = WAITING_FOR_CSV
      subscribeToEvent(job.csvfile_id, "CSV_STORED")
      persist(job)
      return
    }

    job.status = RUNNING
    job.started_at = now()
    persist(job)

    try {
      report = runAnalysis(csv, job.parameters)
      job.report_id = storeReportEntity(report, job.id)
      job.report_location = report.storage_uri
      job.report_summary = summarize(report)
      job.report_status = READY
      job.status = COMPLETED
      job.completed_at = now()
      persist(job)
      emitEvent(job.id, "JOB_COMPLETED")
    } catch (TransientException e) {
      scheduleRetry(job)
    } catch (PermanentException e) {
      job.status = FAILED
      job.validation_errors.add(e.message)
      persist(job)
      emitEvent(job.id, "JOB_FAILED")
    }
  }
}
```

NotificationProcessor (improved batching, channels, and retry)

```
class NotificationProcessor {
  void process(AnalysisJob job) {
    if job.status != COMPLETED and job.report_status != READY then return

    subs = findSubscribersMatching(job.id)
    for s in subs {
      if s.status != ACTIVE or not s.verified then continue

      if s.frequency == immediate {
        deliverToSubscriber(s, job)
      } else {
        addToDigest(s, job)
      }
    }

    logDelivery(job.id, subs)
  }
}
```

Delivery worker:
```
deliverToSubscriber(Subscriber s, AnalysisJob job) {
  for attempt in 0..s.delivery_retries {
    ok = sendToChannel(s, job.report_location, s.preferred_format)
    if ok {
      s.last_delivery_at = now()
      persist(s)
      return true
    }
    sleep(backoff(attempt))
  }
  s.bounced_count += 1
  if s.bounced_count >= BOUNCE_THRESHOLD then s.status = BOUNCED
  persist(s)
  return false
}
```

Criteria helpers (examples):
- IsCsvAvailableCriterion: csv != null && csv.status == STORED
- IsSchemaValidCriterion: detected_schema != null && columns meet expectations
- IsRetryAllowedCriterion: job.retry_count < job.retries

---

## 4. API Endpoints and Rules (updated)

API principles:
- Use HTTP status codes appropriately.
  - 201 Created for successful POST that creates resource; include Location header and returned body { technicalId: "..." }.
  - 200 OK for GET; 202 Accepted for requests that are accepted but not completed (e.g. async ingestion triggered).
  - 400 for validation errors with details; 404 for not found; 409 for conflict (e.g. idempotency conflict); 422 for semantic validation failures.
- POST endpoints should accept an Idempotency-Key header to prevent duplicate creations from retries. If not provided, use checksum-based deduplication for files.
- Responses for error should use a consistent error shape: { code: "ERR_CODE", message: "human message", details: [...] }
- For long-running operations, API clients should poll resource GETs or subscribe to events/webhooks.
- All POST-created entities return a body with technicalId and a Location header.
- List endpoints must support pagination, filtering, and sorting. Provide cursor-based or page-based pagination.

Endpoints (examples)

1) POST /csvfiles
- Request JSON:
{
  "source_type":"upload",
  "source_location":"s3://bucket/file.csv",
  "filename":"file.csv",
  "checksum":"<optional>"
}
- Behavior: validate content-type and size; create CSVFile with status PENDING and schedule validation asynchronously. Support Idempotency-Key: if same checksum or key used, return existing technicalId and 200 or 409 depending on idempotency semantics.
- Response: HTTP 201 Created
Headers: Location: /csvfiles/{technicalId}
Body:
{ "technicalId": "<generated-id>" }

2) GET /csvfiles/{technicalId}
- Response: full CSVFile entity JSON (fields defined above)
- 404 if not found

3) POST /analysisjobs
- Request JSON:
{
  "job_name":"Weekly Report",
  "csvfile_id":"<csv-id>",
  "analysis_type":"summary",
  "parameters":{ "group_by":"country" },
  "schedule":"on_upload",
  "retries": 3,
  "priority": 10
}
- Behavior: Validate parameters (including that csvfile_id exists or set job to WAITING_FOR_CSV if CSV not yet STORED and schedule is on_upload). Enqueue or schedule execution.
- Response: HTTP 201 Created
Body:
{ "technicalId":"<generated-id>" }

4) GET /analysisjobs/{technicalId}
- Response: full AnalysisJob entity JSON

5) POST /subscribers
- Request JSON:
{
  "email":"user@example.com",
  "name":"Alice",
  "subscribed_jobs":[ "<job-id>" ],
  "preferred_format":"pdf",
  "frequency":"immediate",
  "channels": ["email"]
}
- Behavior: create Subscriber in PENDING_VERIFICATION unless verification disabled. Send verification if required.
- Response: HTTP 201 Created
Body:
{ "technicalId":"<generated-id>" }

6) GET /subscribers/{technicalId}
- Response: full Subscriber entity JSON

Additional endpoints (recommended):
- GET /csvfiles?status=STORED&limit=... (paginated)
- GET /analysisjobs?status=QUEUED&limit=... (paginated)
- POST /analysisjobs/{id}/cancel (cancel a running or queued job)
- POST /csvfiles/{id}/retry (request revalidation/reingest)
- GET /reports/{reportId} (if Report entity used)

---

## 5. Operational and Non-functional Rules

- Security & Authorization
  - All endpoints require authentication (API key, OAuth, JWT). Authorization rules determine which tenants or users can access entities.
  - Validate and sanitize webhook urls and stored metadata to avoid SSRF/XSS.

- Quotas & Limits
  - Max file size (configured) enforced during ingest; reject larger files with 413 Payload Too Large.
  - Rate limit POST /csvfiles and POST /analysisjobs per API client.

- Observability
  - Emit structured events for major transitions (CSV_STORED, JOB_COMPLETED, REPORT_READY, SUBSCRIBER_BOUNCED).
  - Track metrics: ingestion success/failure rates, job success/failure, subscriber delivery success, processing latency.

- Retention & Archiving
  - Default retention 30 days (configurable). After retention, CSVFile moves to ARCHIVED and then optionally to DELETED.
  - Reports should have expiry/cleanup policy; archived resources access via separate flow and longer retrieval time.

- Reliability
  - Use persistent queuing for async tasks. Ensure idempotency for retried operations.
  - Backoff and retry policies for transient failures (e.g. network, downstream services).

- Data integrity
  - Store checksum and verify storage writes.
  - Use optimistic concurrency control for entity updates.

---

## 6. Decisions & Open Questions

- Report entity: recommended to separate report lifecycle from job. Confirm if required.
- Verification: confirm whether subscribers must verify email (affects UX and states).
- Recurring remote sources: confirm support for polling remote endpoints or using cron for remote CSV ingestion.
- Multi-tenant considerations: how to partition resources and authorization across tenants.
- Delivery channels: currently email + webhook; confirm any other channel (Slack, SFTP, etc.).

---

If you want, I can also:
- produce an updated mermaid diagram file to visualize updated workflows,
- generate example JSON Schemas for request/response payloads,
- or split this into separate Markdown files per entity.

