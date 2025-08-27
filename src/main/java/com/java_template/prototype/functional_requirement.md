### 1. Entity Definitions
```
Job:
- jobId: String (human-id / name for the job)
- sourceEndpoint: String (API endpoint to ingest from)
- schedule: String (cron or schedule descriptor)
- triggeredAt: DateTime (when job run was triggered)
- startedAt: DateTime (when ingestion started)
- finishedAt: DateTime (when ingestion finished)
- state: String (SCHEDULED, INGESTING, SUCCEEDED, FAILED, NOTIFIED_SUBSCRIBERS)
- resultSummary: String (summary of records processed / errors)
- retryCount: Integer (number of automatic retries attempted)
- errorDetails: String (error message / stack if failed)

Laureate:
- id: Integer (source id from dataset)
- firstname: String (given name)
- surname: String (family name)
- gender: String (gender)
- born: Date (birth date)
- died: Date (death date or null)
- borncountry: String (country name)
- borncountrycode: String (country code)
- borncity: String (birth city)
- year: String (award year)
- category: String (award category)
- motivation: String (award motivation)
- affiliation_name: String (affiliation name)
- affiliation_city: String (affiliation city)
- affiliation_country: String (affiliation country)
- derived_ageAtAward: Integer (derived field: age at award)
- recordStatus: String (NEW, UPDATED, UNCHANGED)
- persistedAt: DateTime (when stored)

Subscriber:
- subscriberId: String (external identifier)
- contactType: String (EMAIL or WEBHOOK or OTHER)
- contactAddress: String (email address or webhook URL)
- filters: String (optional filter expression for notifications: category/year/country)
- active: Boolean (is subscriber active)
- lastNotifiedAt: DateTime (timestamp of last notification)
```

Note: User specified 3 entities; I used exactly those 3.

---

### 2. Entity workflows

Job workflow:
1. Initial State: Job persisted with state = SCHEDULED (persistence triggers Cyoda Job workflow).
2. Start Ingestion: Automatic transition to INGESTING when schedule/time arrives.
3. Fetch & Process: Fetch records from source; emit Laureate add events for each record.
4. Finalize: If fetch/process completed successfully -> SUCCEEDED; else -> FAILED.
5. Notification: On SUCCEEDED or FAILED -> send notifications to Subscribers -> transition to NOTIFIED_SUBSCRIBERS.
6. Terminal: Job stays in NOTIFIED_SUBSCRIBERS until archived or new run scheduled.

```mermaid
stateDiagram-v2
    [*] --> "SCHEDULED"
    "SCHEDULED" --> "INGESTING" : StartIngestionProcessor, *automatic*
    "INGESTING" --> "SUCCEEDED" : IngestionSuccessCriterion
    "INGESTING" --> "FAILED" : IngestionFailureCriterion
    "SUCCEEDED" --> "NOTIFIED_SUBSCRIBERS" : NotifySubscribersProcessor
    "FAILED" --> "NOTIFIED_SUBSCRIBERS" : NotifySubscribersProcessor
    "NOTIFIED_SUBSCRIBERS" --> [*]
```

Job processors and criteria (suggested)
- Processors:
  - StartIngestionProcessor: triggers fetch and emits Laureate persist events.
  - FetchRecordsProcessor: fetches page(s) from sourceEndpoint and yields raw records.
  - ValidateAndEnrichProcessor: delegates validation/enrichment to Laureate workflow or processors.
  - PersistLaureatesProcessor: persists transformed laureate entities and marks recordStatus.
  - NotifySubscribersProcessor: collects subscribers and dispatches notifications.
- Criteria:
  - IngestionSuccessCriterion: all pages fetched or processed without fatal error.
  - IngestionFailureCriterion: fatal error reached or retry exhausted.
  - RetryCriterion: decide automatic retry vs fail (based on retryCount).

Laureate workflow:
1. Initial State: Laureate event persisted as PERSISTED (created by Job workflow).
2. Validation: Automatic transition to VALIDATED if ValidationProcessor passes, else INVALID.
3. Enrichment: Automatic transition to ENRICHED with derived fields (ageAtAward, normalized country).
4. Classification: Determine recordStatus NEW/UPDATED/UNCHANGED via DeduplicationProcessor.
5. Stored: Persist final state and set persistedAt; emit domain event for downstream consumers.
6. Terminal: Stored or INVALID (invalid records flagged for manual review).

```mermaid
stateDiagram-v2
    [*] --> "PERSISTED"
    "PERSISTED" --> "VALIDATED" : ValidationProcessor
    "VALIDATED" --> "ENRICHED" : EnrichmentProcessor
    "ENRICHED" --> "CLASSIFIED" : DeduplicationProcessor
    "CLASSIFIED" --> "STORED" : PersistFinalProcessor
    "VALIDATED" --> "INVALID" : ValidationFailureCriterion
    "INVALID" --> [*]
    "STORED" --> [*]
```

Laureate processors and criteria
- Processors:
  - ValidationProcessor: ensure required fields non-null and formats (id, firstname, year, category).
  - EnrichmentProcessor: derive ageAtAward, normalize country codes, standardize names.
  - DeduplicationProcessor: check if id exists, compare fields and set recordStatus.
  - PersistFinalProcessor: persist final record and record audit metadata.
- Criteria:
  - IsValidCriterion: checks required fields and data types.
  - IsDuplicateCriterion: determines NEW vs UPDATED vs UNCHANGED.

Subscriber workflow:
1. Initial State: Subscriber persisted with state ACTIVE (or inactive if active=false).
2. Subscription Validation: Automatic basic validation of contact info.
3. Notification Ready: On Job completion, if subscriber active and filters match -> RECEIVING_NOTIFICATION.
4. Delivery: NotificationDispatcherProcessor attempts delivery; on success -> RECORD_ACK, on failure -> DELIVERY_RETRY or SUSPENDED after retries.
5. Manual: Admin can manually SUSPEND or REACTIVATE subscriber.

```mermaid
stateDiagram-v2
    [*] --> "ACTIVE"
    "ACTIVE" --> "RECEIVING_NOTIFICATION" : NotificationDispatcherProcessor, *automatic*
    "RECEIVING_NOTIFICATION" --> "RECORD_ACK" : NotificationDeliveredCriterion
    "RECEIVING_NOTIFICATION" --> "DELIVERY_RETRY" : NotificationFailureCriterion
    "DELIVERY_RETRY" --> "SUSPENDED" : RetryExhaustedCriterion
    "SUSPENDED" --> "ACTIVE" : ReactivateSubscriberProcessor, *manual*
    "RECORD_ACK" --> [*]
    "SUSPENDED" --> [*]
```

Subscriber processors and criteria
- Processors:
  - SubscriptionValidationProcessor: validate contactType and contactAddress.
  - NotificationDispatcherProcessor: build notification payload and deliver (email or webhook).
  - DeliveryRetryProcessor: schedule retries for failed deliveries.
  - ReactivateSubscriberProcessor: manual reactivation.
- Criteria:
  - SubscriberActiveCriterion: only send if active and filters match job result.
  - NotificationDeliveredCriterion: delivery acknowledged by endpoint or email system.
  - RetryExhaustedCriterion: decide when to suspend subscriber after repeated failures.

---

### 3. Pseudo code for processor classes (concise examples)

StartIngestionProcessor
```
class StartIngestionProcessor {
  void process(Job job) {
    emitEvent(JobStarted event with jobId);
    pages = FetchRecordsProcessor.fetch(job.sourceEndpoint);
    for page in pages:
      for raw in page.records:
        laureate = mapRawToLaureate(raw);
        persistEntity(laureate); // persisting triggers Laureate workflow in Cyoda
    if all ok emit JobSucceeded else emit JobFailed;
  }
}
```

ValidationProcessor (Laureate)
```
class ValidationProcessor {
  boolean process(Laureate l) {
    if l.id == null or l.firstname == null or l.year == null or l.category == null:
      l.recordStatus = "INVALID";
      persist(l);
      return false;
    normalize simple fields;
    return true;
  }
}
```

EnrichmentProcessor (Laureate)
```
class EnrichmentProcessor {
  void process(Laureate l) {
    if l.born != null and l.year != null:
      l.derived_ageAtAward = calculateAgeAtYear(l.born, l.year);
    l.borncountrycode = normalizeCountryCode(l.borncountry, l.borncountrycode);
    persist(l);
  }
}
```

PersistLaureatesProcessor
```
class PersistLaureatesProcessor {
  void process(Laureate l) {
    if IsDuplicateCriterion.isDuplicate(l):
      l.recordStatus = compareAndDecideNewOrUpdated(l);
    else
      l.recordStatus = "NEW";
    saveToStore(l);
  }
}
```

NotifySubscribersProcessor
```
class NotifySubscribersProcessor {
  void process(Job job) {
    subscribers = findActiveSubscribers();
    for s in subscribers:
      if filtersMatch(s.filters, job.resultSummary):
        NotificationDispatcherProcessor.send(s, jobSummary);
    mark job as NOTIFIED_SUBSCRIBERS;
  }
}
```

NotificationDispatcherProcessor
```
class NotificationDispatcherProcessor {
  boolean send(Subscriber s, Job job) {
    if s.contactType == "WEBHOOK": httpPost(s.contactAddress, payload);
    if s.contactType == "EMAIL": sendEmail(s.contactAddress, payload);
    return deliveryAck;
  }
}
```

---

### 4. API Endpoints Design Rules + Request/Response formats

Rules applied:
- POST triggers entity persistence and Cyoda process method.
- POST responses return only technicalId.
- GET by technicalId allowed for entities created via POST (Job, Subscriber). GET for Laureate provided for retrieval only.
- No GET by condition included (not explicitly requested). GET all optional; included for Laureate retrieval.

Endpoints

1) Create Job
- POST /jobs
Request:
```json
{
  "jobId": "daily_nobel_ingest",
  "sourceEndpoint": "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records",
  "schedule": "0 0 2 * * ?",
  "retryCount": 3
}
```
Response (must return only technicalId):
```json
"tech_job_0001"
```

2) Get Job by technicalId
- GET /jobs/{technicalId}
Response:
```json
{
  "jobId": "daily_nobel_ingest",
  "sourceEndpoint": "...",
  "schedule": "0 0 2 * * ?",
  "state": "SCHEDULED",
  "startedAt": null,
  "finishedAt": null,
  "resultSummary": null,
  "retryCount": 0,
  "errorDetails": null
}
```

3) Create Subscriber
- POST /subscribers
Request:
```json
{
  "subscriberId": "chemistry_team",
  "contactType": "WEBHOOK",
  "contactAddress": "https://example.com/webhook",
  "filters": "category=Chemistry",
  "active": true
}
```
Response:
```json
"tech_sub_001"
```

4) Get Subscriber by technicalId
- GET /subscribers/{technicalId}
Response:
```json
{
  "subscriberId": "chemistry_team",
  "contactType": "WEBHOOK",
  "contactAddress": "https://example.com/webhook",
  "filters": "category=Chemistry",
  "active": true,
  "lastNotifiedAt": "2025-08-20T02:00:00Z"
}
```

5) Get Laureate by technicalId
- GET /laureates/{technicalId}
Response:
```json
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
  "affiliation_name": "Hokkaido University",
  "affiliation_city": "Sapporo",
  "affiliation_country": "Japan",
  "derived_ageAtAward": 80,
  "recordStatus": "NEW",
  "persistedAt": "2025-08-20T02:01:23Z"
}
```

---

Notes & next steps
- Persisting each entity triggers Cyoda to start the corresponding entity workflow (process method). That is the central EDA rule: persistence = event.
- I assumed automatic retries for ingestion and notification with counters; specify exact retry policy if you want changes.
- Would you like me to: (A) add a GET all laureates endpoint, (B) add filter-based GET endpoints, or (C) tighten required fields and validation rules?