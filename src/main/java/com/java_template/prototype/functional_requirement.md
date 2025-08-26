### 1. Entity Definitions
```
BatchJob:
- job_name: String (human name for the scheduled batch)
- schedule_cron: String (cron or schedule descriptor for beginning of month)
- api_endpoint: String (Fakerest API base url)
- admin_emails: List<String> (recipients for report)
- last_run_timestamp: DateTime (last execution)
- status: String (PENDING IN_PROGRESS COMPLETED FAILED)
- created_at: DateTime (when job created)
- metadata: Object (optional extra settings)

User:
- id: Integer (source id from Fakerest)
- name: String (user full name)
- username: String (username)
- email: String (email)
- phone: String (phone)
- address: Object (address fields)
- raw_payload: Object (original source payload)
- fetched_at: DateTime (when fetched)
- processing_status: String (FETCHED VALIDATED TRANSFORMED STORED FAILED)
- stored_reference: String (pointer to stored/transformed record)

MonthlyReport:
- month: String (YYYY-MM)
- generated_at: DateTime (report generation time)
- total_users: Integer (count)
- new_users: Integer
- updated_users: Integer
- invalid_records_count: Integer
- sample_records: List<Object> (sample rows)
- report_file_ref: String (storage reference)
- published_status: String (PENDING PUBLISHED FAILED)
- delivery_attempts: Integer
- admin_recipients: List<String>
```

### 2. Entity workflows

BatchJob workflow:
1. Initial State: Job created with PENDING status (manual POST)
2. Scheduled: Scheduler moves job to TRIGGERED at schedule (automatic)
3. Fetching: FetchUsersProcessor reads Fakerest -> creates User entities (automatic)
4. Transforming: TransformProcessor validates & normalizes Users (automatic)
5. Storing: StoreProcessor persists transformed records and updates User.processing_status (automatic)
6. Reporting: GenerateReportProcessor produces MonthlyReport (automatic)
7. Publishing: PublishReportProcessor sends report to admins and updates MonthlyReport.published_status (automatic)
8. Completion: Job marked COMPLETED or FAILED

```mermaid
stateDiagram-v2
    [*] --> "PENDING"
    "PENDING" --> "TRIGGERED" : "schedule_cron, automatic"
    "TRIGGERED" --> "FETCHING" : "StartJobProcessor, automatic"
    "FETCHING" --> "TRANSFORMING" : "FetchCompleteCriterion"
    "TRANSFORMING" --> "STORING" : "TransformCompleteCriterion"
    "STORING" --> "REPORTING" : "StorageCompleteCriterion"
    "REPORTING" --> "PUBLISHING" : "ReportReadyCriterion"
    "PUBLISHING" --> "COMPLETED" : "PublishCompleteCriterion"
    "PUBLISHING" --> "FAILED" : "PublishFailureCriterion"
    "FAILED" --> [*]
    "COMPLETED" --> [*]
```

Processors and criteria for BatchJob:
- Processors: StartJobProcessor, FetchUsersProcessor, TransformUsersProcessor, StoreUsersProcessor, GenerateReportProcessor, PublishReportProcessor
- Criteria: FetchCompleteCriterion, TransformCompleteCriterion, StorageCompleteCriterion, ReportReadyCriterion
- Notes: FetchUsersProcessor will create User entities (persistence triggers User workflow)

User workflow:
1. Initial State: FETCHED (created by FetchUsersProcessor)
2. Validation: VALIDATING -> runs validations (automatic)
3. Transformation: TRANSFORMED -> normalized fields (automatic)
4. Storing: STORED -> persisted in cloud DB (automatic)
5. Failure: FAILED -> for invalid/unrecoverable records (automatic or manual review)

```mermaid
stateDiagram-v2
    [*] --> "FETCHED"
    "FETCHED" --> "VALIDATING" : "ValidateUserProcessor, automatic"
    "VALIDATING" --> "TRANSFORMED" : "ValidationSuccessCriterion"
    "VALIDATING" --> "FAILED" : "ValidationFailedCriterion"
    "TRANSFORMED" --> "STORED" : "StoreUserProcessor, automatic"
    "STORED" --> [*]
    "FAILED" --> [*]
```

Processors and criteria for User:
- Processors: ValidateUserProcessor, NormalizeUserProcessor, StoreUserProcessor, MarkFailedProcessor
- Criteria: ValidationSuccessCriterion, ValidationFailedCriterion

MonthlyReport workflow:
1. Initial State: GENERATED (created by GenerateReportProcessor)
2. Pending Publish: PENDING_PUBLISH (automatic)
3. Publishing: PUBLISHING (PublishReportProcessor attempts delivery)
4. Final: PUBLISHED or FAILED (automatic / retry policy may be manual)

```mermaid
stateDiagram-v2
    [*] --> "GENERATED"
    "GENERATED" --> "PENDING_PUBLISH" : "GenerateReportProcessor, automatic"
    "PENDING_PUBLISH" --> "PUBLISHING" : "StartPublishProcessor, automatic"
    "PUBLISHING" --> "PUBLISHED" : "PublishSuccessCriterion"
    "PUBLISHING" --> "FAILED" : "PublishFailureCriterion"
    "FAILED" --> "PUBLISHING" : "RetryPublishCriterion"
    "PUBLISHED" --> [*]
    "FAILED" --> [*]
```

Processors and criteria for MonthlyReport:
- Processors: GenerateReportProcessor, StartPublishProcessor, SendEmailProcessor, ArchiveReportProcessor
- Criteria: ReportReadyCriterion, PublishSuccessCriterion, PublishFailureCriterion

### 3. Pseudo code for processor classes (short)

StartJobProcessor
```
class StartJobProcessor {
  process(job) {
    job.status = IN_PROGRESS
    job.last_run_timestamp = now()
    enqueue FetchUsersProcessor(job)
  }
}
```

FetchUsersProcessor
```
class FetchUsersProcessor {
  process(job) {
    users = http.get(job.api_endpoint + /users)
    for each u in users:
      create User with fetched_at and raw_payload
    mark job metadata fetched_count
    signal FetchCompleteCriterion
  }
}
```

TransformUsersProcessor
```
class TransformUsersProcessor {
  process(job) {
    for each User with processing_status FETCHED:
      normalized = normalize(User.raw_payload)
      if validate(normalized):
         update User fields, processing_status TRANSFORMED
      else:
         mark User processing_status FAILED
    signal TransformCompleteCriterion
  }
}
```

StoreUsersProcessor
```
class StoreUsersProcessor {
  process(job) {
    for each User TRANSFORMED:
      persist normalized record to cloud DB
      update User.processing_status STORED
    signal StorageCompleteCriterion
  }
}
```

GenerateReportProcessor
```
class GenerateReportProcessor {
  process(job) {
    gather StoredUsers for month
    compute metrics
    create MonthlyReport with GENERATED
    signal ReportReadyCriterion
  }
}
```

PublishReportProcessor / SendEmailProcessor
```
class PublishReportProcessor {
  process(report) {
    send email with report_file_ref to report.admin_recipients
    if success: report.published_status = PUBLISHED
    else increment delivery_attempts and mark FAILED
  }
}
```

### 4. API Endpoints Design Rules

- POST /jobs
  - Purpose: create BatchJob (triggers Schedule and EDA flow)
  - Response: returns only technicalId (system id)
```json
Request:
{
  "job_name":"MonthlyUserBatch",
  "schedule_cron":"0 0 1 * *",
  "api_endpoint":"https://fakerestapi.azurewebsites.net",
  "admin_emails":["admin@example.com"]
}
Response:
{
  "technicalId":"job_123456"
}
```

- GET /jobs/{technicalId}
  - Purpose: retrieve job status and metadata
```json
Response:
{
  "technicalId":"job_123456",
  "job_name":"MonthlyUserBatch",
  "status":"COMPLETED",
  "last_run_timestamp":"2025-08-01T00:00:00Z",
  "metadata":{"fetched_count":100}
}
```

- GET /users/{id}
  - Purpose: retrieve stored user result
```json
Response:
{
  "id":1,
  "name":"John Doe",
  "email":"john@example.com",
  "processing_status":"STORED",
  "stored_reference":"stored_user_789"
}
```

- GET /reports/{month}
  - Purpose: retrieve monthly report by month (YYYY-MM)
```json
Response:
{
  "month":"2025-08",
  "generated_at":"2025-08-01T01:00:00Z",
  "total_users":100,
  "new_users":10,
  "invalid_records_count":2,
  "report_file_ref":"s3://reports/monthly_2025-08.csv",
  "published_status":"PUBLISHED"
}
```

Notes and questions to finalize:
- Confirm exact schedule/timezone (00:00 on day 1? which TZ).
- Confirm report format preference (CSV/PDF) and retention policy for stored users/raw payloads.
- Confirm admin recipients (single or multiple) and retry/escalation policy on publish failures.

If you'd like, I can expand to include StoredUser as a separate explicit entity (would increase entities to 4) or add retry/escalation workflows. Which next?