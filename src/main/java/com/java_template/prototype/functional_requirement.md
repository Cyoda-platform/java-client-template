# Functional Requirements — Data Ingest & Reporting Prototype

This document defines the entities, workflows, processors, criteria, events, and API surface for the DataIngestJob -> AnalysisReport -> Subscriber domain. It updates and normalizes prior logic to ensure consistent status values, event names, processor behaviors, and delivery semantics.

---

## 1. Entities (Definitions and canonical fields)
All status/state names are uppercase for consistency. Timestamps use ISO8601.

- DataIngestJob
  - technicalId: string (datastore id returned by POST)
  - source_url: string (URL to download London houses CSV)
  - scheduled_at: datetime (ISO8601 or null for immediate)
  - triggered_by: string (user or system)
  - status: string (one of: CREATED, SCHEDULED, VALIDATING, DOWNLOADING, ANALYZING, DELIVERING, COMPLETED, FAILED)
  - created_at: datetime (when job was created)
  - last_updated_at: datetime

- AnalysisReport
  - technicalId: string
  - job_technicalId: string (link to originating DataIngestJob)
  - generated_at: datetime (when analysis finished)
  - summary_metrics: json (computed metrics, aggregates, distributions)
  - record_count: integer (rows analyzed)
  - report_link: string (location of report artifact — PDF/HTML/JSON)
  - status: string (one of: CREATED, VALIDATING, READY, FAILED, RETRY_PENDING, ARCHIVED)
  - created_at: datetime
  - last_updated_at: datetime

- Subscriber
  - technicalId: string
  - email: string (recipient address)
  - name: string (optional recipient name)
  - subscription_status: string (one of: CREATED, VERIFYING, ACTIVE, UNVERIFIED, UNSUBSCRIBED)
  - preferred_format: string (one of: HTML, PDF, BOTH)
  - created_at: datetime
  - last_verified_at: datetime or null

Notes:
- Status/state values are canonicalized and used across workflows and processor logic.
- All entities expose technicalId and created_at for retrieval and audit.

---

## 2. Workflows, processors, and criteria
Workflows are designed as state machines. Processors are responsible for performing idempotent operations, updating entity status, persisting state, and emitting events. Criteria are stateless checks that decide transition paths; events use normalized names.

### 2.1 DataIngestJob workflow
States: CREATED -> (SCHEDULED) -> VALIDATING -> DOWNLOADING -> ANALYZING -> DELIVERING -> COMPLETED / FAILED

Transitions (state diagram):
- [*] -> CREATED
- CREATED -> VALIDATING (ValidateJobProcessor, automatic)
- CREATED -> SCHEDULED (if scheduled_at in the future; scheduling subsystem will set SCHEDULED and re-enter VALIDATING at scheduled time)
- VALIDATING -> FAILED (ValidationFailed event / ValidationFailedCriterion)
- VALIDATING -> DOWNLOADING (ValidationPassed event / ValidationPassedCriterion)
- DOWNLOADING -> ANALYZING (DownloadCompleted event)
- DOWNLOADING -> FAILED (DownloadFailed event)
- ANALYZING -> DELIVERING (AnalysisCompleted/ReportCreated event)
- ANALYZING -> FAILED (AnalysisFailed event)
- DELIVERING -> COMPLETED (DeliveryCompleted event)
- DELIVERING -> FAILED (DeliveryFailed event)

Processors:
- ValidateJobProcessor
- DownloadProcessor
- AnalyzeProcessor
- DeliveryProcessor
- (Scheduler/TimingProcessor to handle SCHEDULED -> VALIDATING)

Criteria (transition checks):
- ValidationPassedCriterion
- ValidationFailedCriterion
- DownloadFailedCriterion
- DeliveryFailedCriterion

Events (canonical):
- ValidationPassed (jobTechnicalId)
- ValidationFailed (jobTechnicalId)
- DownloadCompleted (jobTechnicalId)
- DownloadFailed (jobTechnicalId)
- ReportCreated (reportTechnicalId, jobTechnicalId)
- AnalysisFailed (jobTechnicalId)
- DeliveryCompleted (jobTechnicalId)
- DeliveryFailed (jobTechnicalId)

### 2.2 AnalysisReport workflow
States: CREATED -> VALIDATING -> READY -> ARCHIVED
Failures/Retry: VALIDATING -> FAILED -> RETRY_PENDING -> VALIDATING

Transitions:
- [*] -> CREATED (when report persisted by AnalyzeProcessor)
- CREATED -> VALIDATING (ValidateReportProcessor, automatic)
- VALIDATING -> READY (ReportCompleteCriterion)
- VALIDATING -> FAILED (ReportIncompleteCriterion)
- FAILED -> RETRY_PENDING (RetryProcessor, manual or automatic based on policy)
- RETRY_PENDING -> VALIDATING (RetryProcessor triggers re-validation/analysis)
- READY -> ARCHIVED (ArchiveProcessor, manual/automatic retention policy)

Processors:
- ValidateReportProcessor
- ArchiveProcessor
- RetryProcessor

Criteria:
- ReportCompleteCriterion
- ReportIncompleteCriterion

Events (canonical):
- ReportValidated (reportTechnicalId)
- ReportReady (reportTechnicalId)
- ReportFailed (reportTechnicalId)
- ReportArchived (reportTechnicalId)
- ReportRetryRequested (reportTechnicalId)

### 2.3 Subscriber workflow
States: CREATED -> VERIFYING -> ACTIVE / UNVERIFIED -> UNSUBSCRIBED

Transitions:
- [*] -> CREATED
- CREATED -> VERIFYING (VerifySubscriberProcessor, automatic)
- VERIFYING -> ACTIVE (VerificationPassed event / VerificationPassedCriterion)
- VERIFYING -> UNVERIFIED (VerificationFailed event / VerificationFailedCriterion)
- UNVERIFIED -> ACTIVE (ManualApproveProcessor, manual)
- ACTIVE -> UNSUBSCRIBED (UnsubscribeProcessor, manual)

Processors:
- VerifySubscriberProcessor
- UnsubscribeProcessor
- ManualApproveProcessor

Criteria:
- VerificationPassedCriterion
- VerificationFailedCriterion

Events (canonical):
- SubscriberVerificationPassed (subscriberTechnicalId)
- SubscriberVerificationFailed (subscriberTechnicalId)
- SubscriberUnsubscribed (subscriberTechnicalId)

---

## 3. Processor pseudocode (reconciled & normalized)
General rules applied to pseudocode:
- All processors must be idempotent.
- Processors update entity.status to the canonical state names.
- Processors persist changes before emitting events.
- Events use canonical names (see Events list above).
- Processors implement retry/backoff for transient errors and emit failure events for permanent errors.

ValidateJobProcessor:
```
class ValidateJobProcessor:
    process(job):
        # idempotent entry
        job.status = "VALIDATING"
        persist(job)

        if scheduled_in_future(job.scheduled_at):
            job.status = "SCHEDULED"
            persist(job)
            # scheduling subsystem will re-queue/trigger validation at scheduled time
            return

        if not reachable(job.source_url) or source_empty(job.source_url):
            job.status = "FAILED"
            persist(job)
            emit Event ValidationFailed with job.technicalId
        else:
            job.status = "DOWNLOADING"
            persist(job)
            emit Event ValidationPassed with job.technicalId
```

DownloadProcessor:
```
class DownloadProcessor:
    process(job):
        # idempotent - check if snapshot already stored for job.technicalId
        job.status = "DOWNLOADING"
        persist(job)

        try:
            snapshot = fetch_csv(job.source_url)
        except TransientNetworkError:
            # allow retry; keep job in DOWNLOADING or schedule retry/backoff
            emit Event DownloadFailed with job.technicalId
            return

        if snapshot.row_count == 0:
            job.status = "FAILED"
            persist(job)
            # empty dataset treated as failure for this pipeline
            emit Event DownloadFailed with job.technicalId
        else:
            store_snapshot(snapshot, job.technicalId)  # idempotent store by job id
            persist(job)
            emit Event DownloadCompleted with job.technicalId
```

AnalyzeProcessor:
```
class AnalyzeProcessor:
    process(job):
        job.status = "ANALYZING"
        persist(job)

        snapshot = load_snapshot(job.technicalId)
        if snapshot is None:
            job.status = "FAILED"
            persist(job)
            emit Event AnalysisFailed with job.technicalId
            return

        try:
            metrics = compute_summary_metrics(snapshot)
        except AnalysisError:
            job.status = "FAILED"
            persist(job)
            emit Event AnalysisFailed with job.technicalId
            return

        report = new AnalysisReport(
            job_technicalId = job.technicalId,
            generated_at = now(),
            summary_metrics = metrics,
            record_count = snapshot.row_count,
            report_link = store_report(metrics, job.technicalId),
            status = "CREATED",
            created_at = now()
        )
        persist(report)
        # Emit canonical event so ValidateReportProcessor and watchers pick it up
        emit Event ReportCreated with report.technicalId and job.technicalId
```

ValidateReportProcessor:
```
class ValidateReportProcessor:
    process(report):
        report.status = "VALIDATING"
        persist(report)

        if report_missing_required_metrics(report.summary_metrics):
            report.status = "FAILED"
            persist(report)
            emit Event ReportFailed with report.technicalId
        else:
            report.status = "READY"
            persist(report)
            emit Event ReportReady with report.technicalId
```

DeliveryProcessor:
```
class DeliveryProcessor:
    process(job):
        job.status = "DELIVERING"
        persist(job)

        # Find the most recent READY report for this job
        report = find_latest_report_for_job(job.technicalId, status="READY")
        if report is None:
            # no ready report -> fail or schedule retry
            job.status = "FAILED"
            persist(job)
            emit Event DeliveryFailed with job.technicalId
            return

        subscribers = list_active_subscribers()  # subscription_status == ACTIVE
        failures = []
        for s in subscribers:
            ok = queue_email(s.email, report.report_link, s.preferred_format, job.technicalId)
            if not ok:
                failures.append(s.technicalId)

        if failures:
            # record failures for retry/monitoring, but deliveries to other subscribers may have succeeded
            job.status = "FAILED"
            persist(job)
            emit Event DeliveryFailed with job.technicalId
        else:
            job.status = "COMPLETED"
            persist(job)
            emit Event DeliveryCompleted with job.technicalId
```

VerifySubscriberProcessor:
```
class VerifySubscriberProcessor:
    process(subscriber):
        subscriber.subscription_status = "VERIFYING"
        persist(subscriber)

        if auto_verify(subscriber.email):
            subscriber.subscription_status = "ACTIVE"
            subscriber.last_verified_at = now()
        else:
            subscriber.subscription_status = "UNVERIFIED"
        persist(subscriber)
        emit Event SubscriberVerificationCompleted with subscriber.technicalId
```

Notes on processors:
- Persistence occurs before emitting events so downstream components observe the persisted state.
- Event names are canonical and separate from Criterion names (criteria are used by the orchestration engine to make transitions).
- Transient failures should trigger retry logic (exponential backoff) rather than immediate permanent failure wherever feasible.

---

## 4. API Endpoints and Design Rules (functional)
Design rules:
- POST endpoints that create entities return { "technicalId": "..." } only.
- GET endpoints return full persisted entity by technicalId.
- POST triggers workflow orchestration via emitted events (platform responsibility).
- All POSTs are idempotent when provided the same client-generated idempotency key (recommended header). If the request is repeated with the same input and idempotency key, the same technicalId must be returned and no duplicate jobs/subscribers created.
- Scheduling: if scheduled_at is in the future, the job status is SCHEDULED until the scheduled time when it transitions into VALIDATING.

Endpoints:

1) POST /jobs
- Purpose: create DataIngestJob (triggers ingestion -> analysis -> delivery workflow)
- Request JSON:
  {
    "source_url": "string",
    "scheduled_at": "ISO8601 or null",
    "triggered_by": "string"
  }
- Behavior:
  - Create DataIngestJob with status CREATED (or SCHEDULED if scheduled_at is in the future).
  - Persist job and return only { "technicalId": "string" }.
  - Emit JobCreated/initial event to start orchestration.
- Response: 201 Created with body { "technicalId": "..." }

2) GET /jobs/{technicalId}
- Purpose: retrieve full DataIngestJob entity and current status/state
- Response: 200 OK with DataIngestJob JSON (fields in Entities section). 404 if not found.

3) POST /subscribers
- Purpose: add a subscriber (triggers verification)
- Request JSON:
  {
    "email": "string",
    "name": "string",
    "preferred_format": "HTML|PDF|BOTH"
  }
- Behavior:
  - Create Subscriber with subscription_status = CREATED and return { "technicalId": "string" }.
  - Emit SubscriberCreated event so VerifySubscriberProcessor runs.
- Response: 201 Created with { "technicalId": "..." }

4) GET /subscribers/{technicalId}
- Purpose: retrieve full Subscriber entity
- Response: 200 OK with Subscriber JSON. 404 if not found.

5) GET /reports/{technicalId}
- Purpose: retrieve AnalysisReport by technicalId
- Response: 200 OK with AnalysisReport JSON. 404 if not found.

Notes on GET by query/filter:
- GET by filter/condition is allowed only when explicitly required and should be added as additional endpoints (e.g. GET /reports?jobTechnicalId=...). Not included in the base spec but recommended for observability.

---

## 5. Required Report Metrics and Validation Rules
Every AnalysisReport must include (at minimum):
- total_records: integer
- mean_price: numeric
- median_price: numeric
- distribution_by_neighbourhood: map/neighbourhood -> metrics (counts, mean, median)
- missing_value_counts: map/field -> integer

ValidateReportProcessor must ensure these metrics are present and that record_count matches total_records derived from summary_metrics when applicable. Reports failing these checks move to FAILED and may be retried.

---

## 6. Delivery and Retry semantics
- DeliveryProcessor sends the report_link to all active subscribers (subscription_status == ACTIVE).
- Individual subscriber delivery failures should be recorded and retried independently; repeated global failures mark the job DELIVERING -> FAILED and surface alerts/metrics.
- Retries: processors must support a retry mechanism for transient errors. Retry policies and limits are configurable (recommended defaults: 3 retries with exponential backoff).

---

## 7. Monitoring, Observability and Idempotency
- All entities expose status via GET endpoints for inspection.
- Processors must log attempts, timestamps, and failure reasons for retries.
- Processors must be safe to run multiple times (idempotent) and use persisted markers to avoid duplicate downstream side effects (e.g. only create one AnalysisReport for a given job unless a deliberate retry/replace is requested).
- Provide metrics for job count by status, report count by status, delivery success/failure rates, and subscriber verification metrics.

---

## 8. Acceptance / Functional criteria (summary)
- Scheduling: Jobs may run immediately (scheduled_at null) or be scheduled at a specific time (SCHEDULED).
- Required report metrics: total records, mean/median prices, distribution by neighbourhood, missing-value counts.
- Delivery: Reports are queued to all ACTIVE subscribers; unsuccessful deliveries are recorded for independent retry.
- Retries: Processors support retry transitions for transient failures with configurable backoff and retry limits.
- Monitoring: Jobs and reports expose status via GET endpoints for inspection; operational metrics are available for alerts.

---

If you confirm this updated and normalized functional requirements file is correct, I will persist it as requested. If any additional corrections or changes are required (for example different canonical status names or additional endpoints), tell me what to modify.