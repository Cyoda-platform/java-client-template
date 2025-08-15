# Functional Requirements — Nobel Laureates Data Ingestion (Updated)

This document specifies the functional requirements for the Nobel Laureates data ingestion prototype. It defines the three core entities (Job, Laureate, Subscriber), their fields, workflows, lifecycle states, processors, criteria, and API contract. The content below represents the latest clarified logic, including idempotency, upsert handling, success criteria, notification delivery semantics, and error handling.

---

## 1. Overview and Assumptions
- Core entities: Job, Laureate, Subscriber. No other entities will be added.
- Persistence events drive processing: creating (persisting) a Job or a Laureate entity triggers downstream processing automatically (EDA semantics). Creating a Subscriber does not automatically trigger orchestration, but persisted Subscribers may receive notifications when Jobs complete.
- All POST endpoints return only the created entity's technicalId in the response body (and should return 201 Created). The API should also set Location header to the entity GET URL (optional but recommended).
- JSON parsing/serialization: use Jackson (preferred) or Gson.
- Asynchronous processing and scheduling: use Spring Scheduler, or Quartz for richer scheduling.
- Data source for testing: OpenDataSoft Nobel laureates dataset: https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records

---

## 2. Entity Definitions

Note: fields list includes type, purpose, and important semantics (nullable, default values).

### Job
- id: Long | Optional business id if source provides one. Nullable for internally created jobs.
- technicalId: String | Required. Datastore-imitation technical identifier returned by POST endpoints (unique, UUID-like).
- sourceUrl: String | Required for ingestion jobs. The endpoint to fetch laureate records.
- scheduledTime: String (ISO-8601) | When the job is scheduled to run. If null, run immediately.
- startTime: String (ISO-8601) | Populated when ingestion starts.
- endTime: String (ISO-8601) | Populated when ingestion finishes (success or failure).
- status: String | Lifecycle state. Allowed values: SCHEDULED, INGESTING, FETCHING, PROCESSING_RECORDS, SUCCEEDED, PARTIAL_FAILURE, FAILED, NOTIFIED_SUBSCRIBERS.
- fetchedRecordCount: Integer | Number of records fetched from source (may be 0).
- persistedRecordCount: Integer | Number of records persisted (created or updated) into Laureate store.
- succeededCount: Integer | Number of records that were persisted successfully and for which processing (persisting) did not throw fatal error. Note: this does not imply that validation/enrichment also succeeded for each record.
- failedCount: Integer | Number of records that failed during persistence or processing.
- errorDetails: String | Optional. Aggregated job-level error summary or stack trace for fatal job failures.
- config: String | Optional JSON blob with job-specific configuration (e.g., limit, filters, incremental offset).
- dedupeStrategy: String | Optional. Controls how duplicate laureate IDs are handled on ingest. Allowed values: UPSERT (default), SKIP_DUPLICATE, FAIL_ON_DUPLICATE.
- incrementalOffset: String | Optional token or pointer to mark incremental ingestion progress (used when performing incremental runs).

Important: Job success semantics
- SUCCEEDED: Fetch and persist steps completed without fatal errors; all fetched records were persisted (created or updated) and no record-level fatal persistence errors occurred. Per-record validation/enrichment issues do not automatically mark the Job as FAILED.
- PARTIAL_FAILURE: Job completed fetch but some records failed to persist; job finished and recorded counts accordingly.
- FAILED: Job encountered a fatal exception preventing completion (e.g., network error that prevents finishing fetch), counts may not be reliable.


### Laureate
- id: Integer | Primary business key. By default map to OpenDataSoft record id (recommended). If using an internal id, document the mapping. Required.
- firstname: String | Nullable.
- surname: String | Nullable.
- gender: String | Nullable.
- born: String (ISO-8601 date) | Nullable.
- died: String (ISO-8601 date) | Nullable.
- borncountry: String | Nullable.
- borncountrycode: String | Nullable (two-letter code if present in source).
- borncity: String | Nullable.
- year: String | Required for award year in source. Note: stored as string to preserve original formatting if needed.
- category: String | Award category (e.g., Chemistry). Nullable.
- motivation: String | Nullable.
- affiliationName: String | Nullable.
- affiliationCity: String | Nullable.
- affiliationCountry: String | Nullable.
- ageAtAward: Integer | Enrichment value: computed if born and year are available and valid.
- normalizedCountryCode: String | Enrichment value: normalized country code (e.g., ISO alpha-2).
- dataValidated: Boolean | True if ValidationProcessor passed.
- dataEnriched: Boolean | True if EnrichmentProcessor succeeded (may be false even if validation passed).
- sourceJobTechnicalId: String | technicalId of the Job that created or last updated this Laureate record.
- persistedAt: String (ISO-8601) | Timestamp when laureate persisted or last updated.
- validationErrors: String | Optional details if validation failed.
- enrichmentErrors: String | Optional details if enrichment failed.

Important: Upsert behavior
- IngestJobProcessor default dedupeStrategy = UPSERT: if a Laureate with the same id already exists, update fields from the incoming record and set sourceJobTechnicalId to current job. This avoids duplicate records for re-runs or overlapping data.
- If dedupeStrategy = SKIP_DUPLICATE, the processor will skip persisting records with an existing id (increment skipped counter, not failed). If FAIL_ON_DUPLICATE, encountering an existing id should mark that record as failed.


### Subscriber
- id: Long | Optional business id.
- technicalId: String | Required unique identifier returned by POST endpoints.
- contactType: String | e.g., email, webhook. Required.
- contactDetails: String | e.g., email address or webhook URL.
- active: Boolean | Whether subscriber is active and eligible to receive notifications (default true).
- preferences: String | JSON blob describing notification preferences, e.g.:
  {
    "notifyOnSuccess": true,
    "notifyOnFailure": true,
    "includePayload": "none|summary|full", // controls if laureates are included in notification
    "filters": { "category": ["Physics"], "year": ["2020"] }
  }
- lastNotifiedAt: String (ISO-8601) | Timestamp of last successful notification attempt.
- lastNotificationStatus: String | Optional field for last delivery status: DELIVERED, FAILED.

Notification semantics
- Only active subscribers whose preferences match the Job will be considered for delivery.
- Notification delivery should record per-subscriber status and lastNotifiedAt only when delivery is successful.
- Delivery attempts may be retried (configurable) on transient failures.

---

## 3. Workflows and Lifecycle Logic

General notes:
- All processors should be designed to be idempotent when possible (especially ingest and delivery processors), to support retries without double-processing side effects.
- Jobs should record metrics and counts for observability and debugging.

### Job workflow (updated)
1. Creation: Client POST /api/jobs -> persist Job with status SCHEDULED and return {"technicalId":"..."}.
2. Scheduling/Start: At scheduledTime (or immediately if scheduledTime is null or in the past), Job scheduler picks up the Job and sets status to INGESTING and startTime = now().
3. Fetching: Status moves to FETCHING while calling job.sourceUrl. The response is parsed. If HTTP errors occur and are retryable, retry using exponential backoff (configurable). If fetch fails fatally after retries, set status = FAILED, errorDetails populated, endTime recorded, persist job, and stop.
4. Processing records: Status moves to PROCESSING_RECORDS. The fetched JSON is iterated. For each record:
   - Map JSON to Laureate domain model.
   - Apply dedupeStrategy (UPSERT/SKIP_DUPLICATE/FAIL_ON_DUPLICATE).
   - Persist Laureate (create or update). For each record keep track of per-record result: CREATED, UPDATED, SKIPPED, FAILED.
   - Emit persist event for Laureate which triggers Laureate workflow (validation and enrichment) asynchronously.
   - Update persistedRecordCount and succeededCount/failedCount appropriately.
5. Completion decision:
   - If fetch completed but some record-level failures occurred, set status = PARTIAL_FAILURE.
   - If fetch and all record persistence succeeded, set status = SUCCEEDED.
   - For fatal exceptions that prevented completion, set status = FAILED.
   - Set fetchedRecordCount, persistedRecordCount, succeededCount, failedCount, endTime.
   - Persist job.
6. Notifications: After job reaches SUCCEEDED, PARTIAL_FAILURE or FAILED, the system runs NotifySubscribersProcessor to evaluate and send notifications to matching active subscribers. NotifySubscribersProcessor must:
   - Query active subscribers and apply SubscriberPreferenceCriterion to select recipients for this job.
   - For each selected Subscriber, call DeliveryProcessor to deliver the notification (email or webhook), honoring the preference includePayload (none, summary, full). Payload may include summary counts and optionally a list of laureate ids or full laureate payload depending on preference.
   - Update subscriber.lastNotifiedAt and subscriber.lastNotificationStatus only on successful delivery. If delivery fails, record failure details and optionally retry.
   - After attempting notifications for all matching subscribers, set job.status = NOTIFIED_SUBSCRIBERS and persist the job.

State diagram (textual):
- [*] -> SCHEDULED -> INGESTING -> FETCHING -> PROCESSING_RECORDS -> SUCCEEDED/PARTIAL_FAILURE/FAILED -> NOTIFIED_SUBSCRIBERS -> [*]

Notes:
- Notifications are executed regardless of job status (success/partial/failed) if subscriber preferences indicate interest. The system must allow subscribers to opt-in/out for specific job outcomes.
- Job counters: succeededCount counts record-level persistence successes (CREATED or UPDATED), not subsequent validation/enrichment results.


### Laureate workflow (updated)
1. Persisted event: Laureate persisted (created or updated) by the Job processor triggers Laureate workflow asynchronously.
2. Validation: ValidationProcessor runs and checks business rules and required fields. If validation fails, set dataValidated = false, store validationErrors, and persist the Laureate. In that case EnrichmentProcessor is not run.
3. Enrichment: If validation succeeded, EnrichmentProcessor runs and computes derived fields (ageAtAward, normalizedCountryCode). On success set dataEnriched = true and persist the laureate with enrichment results. On enrichment failure, set dataEnriched = false and store enrichmentErrors, persist laureate.
4. Manual remediation path: Records marked with validationErrors or enrichmentErrors can be inspected and corrected manually. Once corrected, re-run validation/enrichment or rely on persistence event to trigger processors again.

State diagram (textual):
- PERSISTED -> VALIDATING -> (VALIDATION_FAILED -> AWAIT_MANUAL_REVIEW) OR (ENRICHING -> (ENRICHMENT_FAILED -> AWAIT_MANUAL_REVIEW) OR COMPLETED)

Notes:
- A validation failure for a Laureate does not roll back Job persistence; Jobs track persistence results separately.
- ValidationProcessor and EnrichmentProcessor should be idempotent when possible.
- EnrichmentCriterion: only run enrichment if dataValidated == true and required fields for enrichment exist (e.g., born and year for ageAtAward).


### Subscriber workflow (updated)
1. Creation: POST /api/subscribers persists the subscriber and returns technicalId. No orchestration is triggered automatically on create.
2. Activation/Deactivation: Subscribers may be manually activated or deactivated.
3. Notification: NotifySubscribersProcessor (triggered by a Job completion) decides which subscribers receive notifications based on SubscriberPreferenceCriterion and contactType.
4. Delivery: DeliveryProcessor sends notifications using the appropriate adapter (email or webhook). DeliveryProcessor must support retries for transient failures and expose delivery results back to NotifySubscribersProcessor for status recording.
5. Deactivation: Manual deactivation prevents future notifications.

Notes on subscriber notifications:
- lastNotifiedAt should be updated only on successful delivery.
- Subscribers may specify includePayload level to limit payload size.
- Delivery attempts should respect rate limits and backoffs for webhooks.

---

## 4. Processors, Criteria and Important Implementation Details

Processors to implement (high level):
- IngestJobProcessor: orchestrates fetch, parse, record iteration, mapping to Laureate, persistence (upsert), and job counters. Handles fetch retries and error aggregation.
- FetchRecordsProcessor: optional split responsibility to fetch and return parsed records.
- ForEachRecordProcessor: iterate and persist laureate records per configured dedupeStrategy.
- JobCompleteCriterion: determine job completion status (SUCCEEDED, PARTIAL_FAILURE, FAILED) based on job counters and exception state.
- NotifySubscribersProcessor: selects subscribers per SubscriberPreferenceCriterion, prepares payloads (based on includePayload preference), invokes DeliveryProcessor, records per-subscriber delivery status, and marks job NOTIFIED_SUBSCRIBERS.
- ValidationProcessor: validate Laureate fields and persist validation results.
- EnrichmentProcessor: compute ageAtAward and normalizedCountryCode; persist enrichment results.
- ValidationCriterion: decide whether to run enrichment (e.g., only if dataValidated == true).
- EnrichmentCriterion: check required fields for enrichment.
- SubscriberPreferenceCriterion: evaluate subscriber preferences against job payload (e.g., category filters).
- DeliveryProcessor: delivery adapter supporting email and webhook. Should support retries on transient failures.

Suggested pseudo-code highlights (concise):

IngestJobProcessor (high-level):
```
class IngestJobProcessor {
  void process(Job job) {
    job.startTime = now();
    job.status = "INGESTING"; persist(job);
    try {
      job.status = "FETCHING"; persist(job);
      response = httpGet(job.sourceUrl) // with retries/backoff
      records = parseJson(response)
      job.fetchedRecordCount = records.size();
      job.status = "PROCESSING_RECORDS"; persist(job);
      for (record : records) {
        Laureate l = mapRecord(record);
        RecordResult r = persistLaureateWithStrategy(l, job.dedupeStrategy, job.technicalId);
        // update counters
        switch (r.status) {
          case CREATED/UPDATED: job.succeededCount++ ; break;
          case SKIPPED: // increment persistedRecordCount maybe
          case FAILED: job.failedCount++; break;
        }
      }
      // decide final job status
      job.status = (job.failedCount == 0) ? "SUCCEEDED" : "PARTIAL_FAILURE";
    } catch (Exception e) {
      job.status = "FAILED";
      job.errorDetails = summarize(e);
    } finally {
      job.endTime = now(); persist(job);
      // run notifications asynchronously
      triggerNotifySubscribers(job.technicalId);
    }
  }
}
```

NotifySubscribersProcessor (high-level):
```
class NotifySubscribersProcessor {
  void process(Job job) {
    subscribers = queryActiveSubscribers();
    recipients = subscribers.filter(s -> SubscriberPreferenceCriterion.matches(s.preferences, job));
    for (s : recipients) {
      payload = buildPayload(job, s.preferences.includePayload);
      deliveryResult = DeliveryProcessor.deliver(s, payload);
      if (deliveryResult.success) { s.lastNotifiedAt = now(); s.lastNotificationStatus = "DELIVERED"; persist(s); }
      else { s.lastNotificationStatus = "FAILED"; persist(s); /* optionally schedule retry */ }
    }
    job.status = "NOTIFIED_SUBSCRIBERS"; persist(job);
  }
}
```

Laureate Validation/Enrichment (high-level):
```
class ValidationProcessor {
  void process(Laureate l) {
    ValidationResult res = validate(l);
    if (!res.success) { l.dataValidated = false; l.validationErrors = res.details; persist(l); return; }
    l.dataValidated = true; persist(l);
  }
}

class EnrichmentProcessor {
  void process(Laureate l) {
    if (!l.dataValidated) return;
    try {
      l.ageAtAward = computeAge(l.born, l.year);
      l.normalizedCountryCode = normalizeCountryCode(l.borncountrycode, l.borncountry);
      l.dataEnriched = true;
    } catch (Exception e) {
      l.dataEnriched = false; l.enrichmentErrors = summarize(e);
    } finally { persist(l); }
  }
}
```

Delivery processor notes:
- For email: implement templating and consider batching for multiple notifications.
- For webhook: include delivery headers to help idempotency (Idempotency-Key derived from job technicalId + subscriber technicalId).
- Both adapters should return a deterministic success/failure result and error details for diagnostics.

---

## 5. API Endpoints (contract; behavior rules)

General rules:
- POST returns only {"technicalId":"..."} and 201 Created.
- POST persists entity and triggers any automatic processing (for Job -> ingestion; for Laureate, ingestion by Job creates Laureate; for Subscriber no automatic orchestration triggered).
- GET endpoints return full stored representation.
- Provide Location header on successful POST (e.g., Location: /api/jobs/{technicalId}).

### Jobs
- POST /api/jobs
  - Request body example:
    {
      "sourceUrl": "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records?limit=100",
      "scheduledTime": "2025-08-20T10:00:00Z",
      "config": "{ \"limit\": 100 }",
      "dedupeStrategy": "UPSERT"
    }
  - Response (201 Created):
    { "technicalId": "job-0001-uuid" }
  - Behavior: Persists Job in SCHEDULED status. Scheduler picks it up at scheduledTime.

- GET /api/jobs/{technicalId}
  - Response example:
    {
      "technicalId": "job-0001-uuid",
      "sourceUrl": "...",
      "scheduledTime": "2025-08-20T10:00:00Z",
      "startTime": "2025-08-20T10:00:05Z",
      "endTime": "2025-08-20T10:01:30Z",
      "status": "NOTIFIED_SUBSCRIBERS",
      "fetchedRecordCount": 10,
      "persistedRecordCount": 10,
      "succeededCount": 10,
      "failedCount": 0,
      "errorDetails": null,
      "config": "{ \"limit\": 100 }"
    }

Notes: Additional endpoints (optional) for listing jobs or querying by status may be added but are not required unless requested.


### Subscribers
- POST /api/subscribers
  - Request example:
    {
      "contactType": "webhook",
      "contactDetails": "https://example.com/webhook",
      "preferences": "{ \"notifyOnSuccess\": true, \"notifyOnFailure\": true, \"includePayload\": \"summary\" }"
    }
  - Response (201 Created):
    { "technicalId": "sub-0001-uuid" }
  - Behavior: Persist Subscriber (active=true by default). No automatic orchestration; subscriber will be considered on subsequent Job notifications.

- GET /api/subscribers/{technicalId}
  - Response example:
    {
      "technicalId": "sub-0001-uuid",
      "contactType": "webhook",
      "contactDetails": "https://example.com/webhook",
      "active": true,
      "preferences": "{ \"notifyOnSuccess\": true, \"notifyOnFailure\": true }",
      "lastNotifiedAt": "2025-08-20T10:02:00Z",
      "lastNotificationStatus": "DELIVERED"
    }


### Laureates
- No POST endpoint is provided for Laureate (Laureates are created/updated by Job ingestion process).

- GET /api/laureates/{id}
  - Response example:
    {
      "id": 853,
      "firstname": "Akira",
      "surname": "Suzuki",
      "gender": "male",
      "born": "1930-09-12",
      "died": null,
      "borncountry": "Japan",
      "borncountrycode": "JP",
      "borncity": "Mukawa",
      "year": "2010",
      "category": "Chemistry",
      "motivation": "for palladium-catalyzed cross couplings in organic synthesis",
      "affiliationName": "Hokkaido University",
      "affiliationCity": "Sapporo",
      "affiliationCountry": "Japan",
      "ageAtAward": 80,
      "normalizedCountryCode": "JP",
      "dataValidated": true,
      "dataEnriched": true,
      "sourceJobTechnicalId": "job-0001-uuid",
      "persistedAt": "2025-08-20T10:00:15Z"
    }

- GET /api/laureates (optional) - return paginated list of laureates; include filters if needed.

---

## 6. Error handling, retries and idempotency
- Fetch retries: configurable retry policy with exponential backoff for transient network errors when fetching sourceUrl.
- Delivery retries: webhooks/email should be retried for transient errors a configurable number of times with backoff.
- Idempotency: Deliveries should include an Idempotency-Key header (job.technicalId + subscriber.technicalId) on webhooks. Laureate persistence must support idempotent upsert operations.
- Partial failures within a Job should not mark the entire Job as FAILED unless a fatal exception stops progress. Partial failures are recorded in counters and job.status may be PARTIAL_FAILURE.

---

## 7. Metrics & Observability
- Record and expose metrics: job run time, fetchedRecordCount, succeededCount, failedCount, notification delivery success/failure, per-record validation/enrichment failures.
- Log job-level and record-level errors with correlation ids (jobTechnicalId) for easy tracing.

---

## 8. Security & Rate Limits
- When invoking external webhooks, respect rate limits and TLS certificate validation.
- Validate and sanitize subscriber contactDetails (e.g., ensure webhook URLs are valid and permitted in configured allow-list if required).

---

## 9. Tests and Local Development
- Provide a small test fixture using the sample OpenDataSoft endpoint (limit=1) to validate mapping and processing pipelines.
- Allow job config to override sourceUrl for testing.

---

## 10. Open Questions
1. Should Subscribers support both email and webhook out of the box, or only webhooks? (original question retained)
2. For Laureate primary key, do you want to rely on OpenDataSoft record id as Laureate.id, or generate an internal id? (original question retained)
3. Do you want Job to support incremental runs (store last processed offset) or only full fetch per scheduled run? (original question retained)
4. What dedupeStrategy should be default in production: UPSERT (recommended) or FAIL_ON_DUPLICATE?
5. What retry/backoff policy should be used for external fetch and delivery (counts and backoff parameters)?

---

If you want changes to any part of this updated logic (for example: change dedupe strategy default, alter job states, add extra API endpoints like job listing, or change notification payload contents), reply with the specific items to adjust and I will update this document accordingly.