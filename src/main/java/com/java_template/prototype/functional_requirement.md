# Final Functional Requirements (updated)

Last updated: 2025-08-19

Summary: This document defines the entities, workflows, processors, criteria, pseudocode and API rules for the Cat Facts delivery platform. It also resolves previous inconsistencies around retry semantics and delivery records by explicitly defining the Delivery record and retry semantics.

---

## 1. Entities

The system uses the following persistent entities (business/operational records). Delivery and OutboundEvent are explicit system records used by the delivery workflow.

### Subscriber
- id: String (business id / technicalId)
- email: String
- name: String
- timezone: String (IANA timezone string; used for schedule calculations)
- subscription_status: String (one of: active, paused, unsubscribed, archived)
- signup_date: String (ISO 8601 timestamp)
- preferences: Object (e.g., { tags: [String], ... })
- last_delivery_id: String (reference to last Delivery record id)
- metadata: Object (optional free-form data)

Notes: Subscribers created via POST /subscribers use single-click (single opt-in) and become active immediately unless validation fails or email is blacklisted.

---

### CatFact
- id: String (business id / technicalId)
- fact_text: String (content)
- source: String (identifier of the source or ingestion adapter)
- source_created_at: String (timestamp as provided by source, if available)
- fetched_at: String (ISO timestamp when ingested into the platform)
- language: String (ISO language code)
- tags: Array[String]
- curated_by: String (user id if manually curated)
- curated_at: String (ISO timestamp)
- status: String (one of: active, archived, pending_curation)

Notes: CatFacts are created by ingestion jobs only. They go through validation, dedupe and curation before becoming active.

---

### Job
- id: String (business id / technicalId)
- jobType: String (e.g., INGEST_FACTS, DELIVER_WEEKLY, OTHER)
- parameters: Object (job-specific parameters; e.g., sources, batchSize, globalSendDay)
- scheduledAt: String (ISO timestamp when job is intended to run; optional for immediate jobs)
- status: String (PENDING, RUNNING, COMPLETED, FAILED)
- createdAt: String (ISO timestamp)
- resultSummary: Object (counts and other metrics: processed, failed, queued, sent)
- retriesPolicy: Object (optional override; e.g., { maxRetries: Integer })

Notes: When omitted, retriesPolicy defaults are applied. For delivery jobs the platform default is used unless overridden.

---

### Delivery (system record — explicit)
- id: String (technical id)
- job_id: String (originating job id, if created by a job; null for one-off scheduled deliveries)
- subscriber_id: String
- fact_id: String
- scheduled_at: String (ISO timestamp)
- attempts: Integer (number of send attempts already performed; starts at 0)
- status: String (PENDING, SCHEDULED, SENT, FAILED, CANCELLED)
- sent_at: String (ISO timestamp when successfully sent)
- last_error: Object (optional; { code, message })
- retries_policy: Object (copied from job.retriesPolicy at creation time or platform default; e.g., { maxRetries: Integer })

Notes: Delivery is the system's unit of work for sending a CatFact to a subscriber. It records attempts and the retries policy used for that delivery. Copying the policy at creation time avoids coupling to later job changes.

---

### OutboundEvent (system record)
- id: String
- delivery_id: String
- event_type: String (SendSucceeded, SendFailed, Bounce, etc.)
- timestamp: String
- details: Object

Notes: OutboundEvent records are used to compute KPIs (e.g., total sends) and for audit/reporting.

---

## 2. Global Rules & Clarifications

- Single-click subscription: POST /subscribers is single opt-in. A validated subscriber becomes active immediately.
- Global send day: For weekly deliveries a single global send day may be specified on the delivery Job via parameters.globalSendDay. If absent, the platform default send day is used.
- Retry semantics (clarified): retriesPolicy.maxRetries is defined as "the maximum number of retry attempts allowed after the initial attempt." Default: 2. That means default behavior allows up to 3 total attempts (1 initial + 2 retries).
- Delivery retains the retries policy at creation time (delivery.retries_policy) so retry logic uses the snapshot of policy that applied when the delivery was enqueued.
- Reporting KPI: primary send KPI is total successful sends (SENT), computed from OutboundEvent or Delivery records.
- POST endpoints: POST /subscribers and POST /jobs persist entities and return only a technicalId in the response body. They also immediately emit events that start workflows.

---

## 3. Entity Workflows (state machines)

### Subscriber workflow
1. Created → validated → active (single opt-in)
2. Send welcome email
3. Schedule weekly deliveries (either by platform default schedule or in response to a DELIVER_WEEKLY job)
4. Pause / unsubscribe (user action)
5. Bounce / problem handling → archive when thresholds crossed
6. Archive for GDPR removal or repeated failures

State diagram (conceptual): CREATED → VALIDATED → WELCOME_SENT → ACTIVE → SCHEDULED → UNSUBSCRIBED/ARCHIVED

Processors (main): ValidateSubscriberProcessor, SendWelcomeEmailProcessor, ScheduleDeliveryProcessor, ArchiveSubscriberProcessor
Criteria (main): BounceThresholdCriterion, ActiveEligibilityCriterion

Notes:
- ScheduleDeliveryProcessor when called from subscriber creation uses platform default send day. When deliveries are created as part of a DELIVER_WEEKLY job, BuildDeliveryBatchProcessor is responsible for per-subscriber Delivery creation and scheduling.

---

### CatFact workflow
1. Ingested (created by an INGEST_FACTS job)
2. Validation (text length, language, content heuristics)
3. Deduplication (against normalized text or source id)
4. Auto-approve or send to manual curation
5. Active if approved; archived if duplicate or rejected

Processors: ValidateFactProcessor, AutoApproveProcessor, CurateFactProcessor, ArchiveStaleFactsProcessor
Criteria: DedupeCriterion, LanguageSupportedCriterion

Notes: Persisting a CatFact via the ingestion job triggers its validation/dedupe/curation pipeline.

---

### Job workflow
1. Job created (status=PENDING)
2. Prepare (validate parameters, set retriesPolicy default if missing)
3. Execute: job-specific processors run (INGEST_FACTS → FetchFactsProcessor; DELIVER_WEEKLY → BuildDeliveryBatchProcessor)
4. Monitor / retry (job-level orchestration and per-delivery retry semantics as defined)
5. Complete or fail and emit reporting

Processors: PrepareJobProcessor, StartJobProcessor, FetchFactsProcessor, BuildDeliveryBatchProcessor, ReportingProcessor
Criteria: CheckCompleteCriterion, RetryAllowedCriterion

Notes:
- PrepareJobProcessor must populate job.retriesPolicy when absent (default { maxRetries: 2 }).
- BuildDeliveryBatchProcessor creates Delivery records and copies job.retriesPolicy into each delivery.retries_policy.

---

## 4. Pseudocode for processors and criteria (updated & consistent)

All pseudocode uses the clarified retry semantics: retriesPolicy.maxRetries = allowed additional retries after initial attempt. A delivery's maxAttempts = 1 + retries_policy.maxRetries.

ValidateSubscriberProcessor
```
process(subscriber):
  if isValidEmail(subscriber.email) and not isBlacklisted(subscriber.email):
    subscriber.subscription_status = "active"    // single-click
    subscriber.signup_date = now()
    persist(subscriber)
  else:
    subscriber.subscription_status = "archived"
    persist(subscriber)
    emit ErrorEvent(subscriber.id, reason)
```

SendWelcomeEmailProcessor
```
process(subscriber):
  emailId = sendEmail(to=subscriber.email, template=WELCOME)
  recordOutbound(emailId, entity="Subscriber", entityId=subscriber.id)
  // Subscriber remains active; no confirmation required
```

ScheduleDeliveryProcessor
```
process(subscriber):
  // Called on subscriber creation or when user changes preferences
  sendDay = platformDefaultSendDay
  nextSendDate = calculateNextDate(sendDay, subscriber.timezone)
  createDeliveryRecord(
    subscriber_id=subscriber.id,
    fact_id=null,
    scheduled_at=nextSendDate,
    status="SCHEDULED",
    attempts=0,
    retries_policy=platformDefaultRetriesPolicy
  )
```

PrepareJobProcessor
```
process(job):
  validate(job.parameters)
  if job.retriesPolicy is null:
    job.retriesPolicy = { maxRetries: 2 } // platform default
  persist(job)
```

FetchFactsProcessor
```
process(job):
  for src in job.parameters.sources:
    facts = fetchFromSource(src)
    for f in facts:
      fact = mapToCatFact(f, source=src)
      persist(fact)  // triggers CatFact workflow
  updateJobResultSummary(job, processed=..., failed=...)
```

BuildDeliveryBatchProcessor
```
process(job):
  subscribers = findActiveSubscribers()
  for s in subscribers:
    fact = selectFactForSubscriber(s) // business logic for personalization
    delivery = createDeliveryRecord(
      job_id=job.id,
      subscriber_id=s.id,
      fact_id=fact.id,
      scheduled_at=job.scheduledAt or computeFromGlobalSendDay(job.parameters.globalSendDay or platformDefaultSendDay, s.timezone),
      attempts=0,
      status="PENDING",
      retries_policy=job.retriesPolicy or { maxRetries: 2 }
    )
    enqueueSendTask(delivery.id)
  updateJobResultSummary(job, queued = count(subscribers))
```

SendEmailProcessor (delivery-level retry logic)
```
process(delivery):
  // delivery.attempts counts attempts already performed
  delivery.attempts += 1
  persist(delivery.attempts)

  result = sendEmail(to=getSubscriberEmail(delivery.subscriber_id), body=getFactText(delivery.fact_id))

  if result.success:
    delivery.status = "SENT"
    delivery.sent_at = now()
    persist(delivery)
    emit OutboundEvent(type="SendSucceeded", delivery_id=delivery.id)
  else:
    maxAttempts = 1 + (delivery.retries_policy.maxRetries or 2)
    if delivery.attempts < maxAttempts:
      // schedule another attempt with backoff
      scheduleRetry(delivery.id, backoff=computeBackoff(delivery.attempts))
    else:
      delivery.status = "FAILED"
      delivery.last_error = { code: result.errorCode, message: result.errorMessage }
      persist(delivery)
      emit OutboundEvent(type="SendFailed", delivery_id=delivery.id, details=delivery.last_error)
```

DedupeCriterion
```
evaluate(catFact):
  return not existsFactWithSameNormalizedText(catFact.fact_text)
```

RetryAllowedCriterion
```
evaluate(delivery):
  maxAttempts = 1 + (delivery.retries_policy.maxRetries or 2)
  return delivery.attempts < maxAttempts
```

ReportingProcessor
```
process(job):
  // Collects sends KPI
  sends = countOutboundEvents(job.id, type="SendSucceeded")
  job.resultSummary.sent = sends
  persist(job)
  emit JobReportAvailable(job.id)
```

Notes on retry/backoff:
- computeBackoff(attemptNumber) implements exponential backoff (e.g., base 60s * 2^(attemptNumber-1)) or another configured strategy.
- Delivery.retries_policy is authoritative for the delivery's retry behavior.

---

## 5. Processors & Criteria (summary)

Processors (key):
- ValidateSubscriberProcessor
- SendWelcomeEmailProcessor
- ScheduleDeliveryProcessor
- ArchiveSubscriberProcessor
- ValidateFactProcessor
- AutoApproveProcessor
- CurateFactProcessor
- ArchiveStaleFactsProcessor
- PrepareJobProcessor
- StartJobProcessor
- FetchFactsProcessor
- BuildDeliveryBatchProcessor
- SendEmailProcessor
- ReportingProcessor

Criteria (key):
- BounceThresholdCriterion
- ActiveEligibilityCriterion
- DedupeCriterion
- LanguageSupportedCriterion
- CheckCompleteCriterion
- RetryAllowedCriterion

---

## 6. API Endpoints (rules & examples)

General rules:
- POST endpoints create orchestration entities and return only { technicalId: "..." } in the response body.
- POST /subscribers uses single opt-in and the subscriber becomes active immediately when validation passes.
- Delivery jobs may specify parameters.globalSendDay; if absent, platform default is used.
- Job-level retriesPolicy overrides platform defaults; default maxRetries = 2 (i.e., up to 3 attempts total).
- GET endpoints return stored entities by technicalId.
- Reporting endpoints expose send KPIs computed from OutboundEvent/Delivery records.

API specification (representative):

1) Create subscriber
- POST /subscribers
  - Request JSON: { "email": "string", "name": "string", "timezone": "string", "preferences": { "tags": ["string"] } }
  - Response JSON: { "technicalId": "string" }

- GET /subscribers/{technicalId}
  - Response JSON: Full Subscriber entity

2) Create job
- POST /jobs
  - Request JSON:
    {
      "jobType": "INGEST_FACTS | DELIVER_WEEKLY",
      "scheduledAt": "ISO timestamp (optional for immediate jobs)",
      "parameters": {
         "sources": ["https://api.example.com/facts"],
         "batchSize": 100,
         "globalSendDay": "Monday"  // optional
      },
      "retriesPolicy": { "maxRetries": 2 } // optional; default is 2
    }
  - Response JSON: { "technicalId": "string" }

- GET /jobs/{technicalId}
  - Response JSON: Full Job entity including resultSummary (sends count)

3) Read a CatFact
- GET /catfacts/{technicalId}
  - Response JSON: Full CatFact entity

4) Read Delivery status (recommended)
- GET /deliveries/{technicalId}
  - Response JSON: Full Delivery record (status, attempts, last_error, sent_at)

5) Reporting (sends KPI)
- GET /reports/sends?from=2025-01-01&to=2025-01-07
  - Response JSON: { "from": "ISO", "to": "ISO", "totalSends": 1234 }
- GET /jobs/{technicalId}/report
  - Response JSON: job.resultSummary (including sent count)

Sequence behaviours (high level):
- POST /subscribers and POST /jobs persist the entity and emit an event that starts workflows.
- Ingestion job persistence of CatFact triggers its internal validation/dedupe/curation workflow.
- BuildDeliveryBatchProcessor creates Delivery records and enqueues send tasks; SendEmailProcessor handles retries using the delivery.retries_policy snapshot.

---

## 7. Important implementation notes and consistency fixes

- Delivery record is now a first-class system record to make retry logic, status and auditing explicit.
- retriesPolicy semantics explicitly defined so pseudocode and criteria are consistent: maxRetries = number of retries after initial attempt; maxAttempts = 1 + maxRetries.
- SendEmailProcessor and RetryAllowedCriterion use the same comparison (attempts < maxAttempts) to decide whether a retry is allowed.
- Delivery copies retries_policy from the creating job to avoid coupling to later job edits.
- OutboundEvent records are used as the source of truth for send KPIs (optionally supplemented by Delivery.status == SENT).

---

## 8. Confirmation example

I confirm:
- Single-click subscription (subscriber becomes active immediately upon valid creation).
- Global send day may be set on the DELIVER_WEEKLY job (or platform default is used).
- Reporting KPI focuses on sends (derived from OutboundEvent/Delivery records).
- Retry policy default: maxRetries = 2 (i.e., up to 3 attempts total). This is overridable per job and is captured on each Delivery at creation time.


If you want further changes (for example changing the default maxRetries, adding more API endpoints, or changing single opt-in to double opt-in), say so and I will update this specification accordingly.
