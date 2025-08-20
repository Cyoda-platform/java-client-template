### 1. Entity Definitions
```
Job:
- schedule_date: String (date for the run, e.g. 2025-08-21)
- timezone: String (timezone for schedule)
- status: String (PENDING/IN_PROGRESS/COMPLETED/FAILED)
- created_by: String (user or system)
- parameters: Object (ingestion window, Fakerest endpoints, retry_policy)
- started_at: String (timestamp)
- completed_at: String (timestamp)

Activity:
- activity_id: String (source id from Fakerest)
- user_id: String (owner of activity)
- timestamp: String (event time)
- activity_type: String (type of event)
- payload: Object (raw event payload)
- ingestion_status: String (RAW/VALIDATED/DEDUPE)

Report:
- report_id: String (report business id)
- date: String (reporting date)
- generated_at: String (timestamp)
- summary_items: Array (objects containing pattern_type, metrics, confidence)
- recipient_email: String
- delivery_status: String (PENDING/SENT/FAILED)
```

### 2. Entity workflows

Job workflow:
1. Initial State: CREATED
2. Validation: Validate parameters and schedule
3. Ingestion Trigger: Start ingestion (creates Activity events)
4. Processing: Trigger pattern detection and assemble Report
5. Publish: Send Report to recipient
6. Completion/Failure

```mermaid
stateDiagram-v2
    [*] --> "CREATED"
    "CREATED" --> "VALIDATING" : ValidateJobProcessor, automatic
    "VALIDATING" --> "SCHEDULING" : if valid
    "SCHEDULING" --> "INGESTION_TRIGGERED" : StartIngestionProcessor, automatic
    "INGESTION_TRIGGERED" --> "PROCESSING" : IngestionCompleteCriterion
    "PROCESSING" --> "PUBLISHING" : AssembleReportProcessor
    "PUBLISHING" --> "COMPLETED" : PublishReportProcessor
    "PUBLISHING" --> "FAILED" : on error
    "FAILED" --> [*]
    "COMPLETED" --> [*]
```

Processors/Criteria for Job:
- Processors: ValidateJobProcessor, StartIngestionProcessor, AssembleReportProcessor, PublishReportProcessor
- Criteria: IngestionCompleteCriterion, JobFailureCriterion

Activity workflow:
1. Initial State: RAW_RECEIVED
2. Validate & Normalize
3. Deduplicate
4. Persisted for processing (VALIDATED)
5. Mark as PROCESSED (or RETAIN for reprocessing)

```mermaid
stateDiagram-v2
    [*] --> "RAW_RECEIVED"
    "RAW_RECEIVED" --> "VALIDATING" : ValidateActivityProcessor
    "VALIDATING" --> "DEDUPE" : NormalizeProcessor
    "DEDUPE" --> "VALIDATED" : DedupCriterion
    "VALIDATED" --> "PROCESSED" : IndexedForPatternProcessor
    "PROCESSED" --> [*]
```

Processors/Criteria for Activity:
- Processors: ValidateActivityProcessor, NormalizeProcessor, IndexedForPatternProcessor
- Criteria: DedupCriterion

Report workflow:
1. Initial State: DRAFT (assembled)
2. Review/Attach summaries (automatic)
3. Send (PUBLISHING)
4. Sent/Failed
5. Archive

```mermaid
stateDiagram-v2
    [*] --> "DRAFT"
    "DRAFT" --> "READY_TO_SEND" : AssembleReportProcessor
    "READY_TO_SEND" --> "PUBLISHING" : PublishReportProcessor
    "PUBLISHING" --> "SENT" : on success
    "PUBLISHING" --> "FAILED" : on error
    "SENT" --> "ARCHIVED" : ArchiveCriterion
    "FAILED" --> [*]
    "ARCHIVED" --> [*]
```

Processors/Criteria for Report:
- Processors: AssembleReportProcessor, PublishReportProcessor, ArchiveProcessor
- Criteria: DeliverySuccessCriterion, ArchiveCriterion

### 3. Pseudo code for processor classes
ValidateJobProcessor:
```
function process(job){
  if missing(job.parameters) throw ValidationError
  job.status = VALIDATED
  return job
}
```
StartIngestionProcessor:
```
function process(job){
  fetch events from Fakerest for job.parameters.window
  for each event emit Activity entity persist(event)
  job.started_at = now
}
```
AssembleReportProcessor:
```
function process(job){
  read validated Activities for window
  detect patterns (aggregations, spikes, repeated failures)
  create Report with summary_items
}
```
PublishReportProcessor:
```
function process(report){
  send email to report.recipient_email
  update report.delivery_status
}
```

Criteria examples:
- IngestionCompleteCriterion: true when all expected Activities for window persisted
- DedupCriterion: check activity uniqueness by activity_id+user_id
- DeliverySuccessCriterion: email provider response == 200

### 4. API Endpoints Design Rules

POST /jobs
- Creates Job orchestration (triggers EDA run). Returns only technicalId.

Request:
```json
{
  "schedule_date":"2025-08-21",
  "timezone":"UTC",
  "created_by":"system",
  "parameters":{"window":"2025-08-20","fakerest_endpoint":"https://fakerest/api/events"}
}
```
Response:
```json
{"technicalId":"job-abc-123"}
```

GET /jobs/{technicalId}
Response:
```json
{
  "technicalId":"job-abc-123",
  "schedule_date":"2025-08-21",
  "status":"COMPLETED",
  "started_at":"2025-08-21T00:00:00Z",
  "completed_at":"2025-08-21T00:10:00Z",
  "result_counts":{"activities":120,"reports":1}
}
```

GET /activities/{activity_id}
Response:
```json
{
  "activity_id":"evt-1",
  "user_id":"user-7",
  "timestamp":"2025-08-20T12:00:00Z",
  "activity_type":"login",
  "ingestion_status":"VALIDATED"
}
```

GET /reports/{report_id}
Response:
```json
{
  "report_id":"rpt-2025-08-20",
  "date":"2025-08-20",
  "generated_at":"2025-08-21T01:00:00Z",
  "delivery_status":"SENT",
  "summary_items":[{"pattern_type":"peak_usage","metrics":{"peak_hour":12},"confidence":0.9}]
}
```

Notes:
- Max entities used: 3 (Job, Activity, Report). If you want PatternSummary as a separate entity or more orchestration entities (Tasks) I can expand to up to 10.