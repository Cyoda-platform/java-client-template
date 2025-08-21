### 1. Entity Definitions
```
BatchJob:
- jobName: String (friendly name for the run)
- scheduledFor: String (ISO datetime when job should run)
- timezone: String (job timezone)
- adminEmails: List<String> (recipients for reports)
- status: String (PENDING, IN_PROGRESS, COMPLETED, FAILED)
- createdAt: String (ISO datetime)
- startedAt: String (ISO datetime)
- finishedAt: String (ISO datetime)
- processedUserCount: Integer (summary metric)
- errorMessage: String (last error)

UserRecord:
- externalId: Integer (id from Fakerest)
- firstName: String
- lastName: String
- email: String
- sourcePayload: String (raw JSON fetched)
- transformedAt: String (ISO datetime)
- normalized: Boolean (true if standardization applied)
- storedAt: String (ISO datetime)
- lastSeen: String (ISO datetime)

MonthlyReport:
- month: String (YYYY-MM, report month)
- generatedAt: String (ISO datetime)
- totalUsers: Integer
- newUsers: Integer
- changedUsers: Integer
- reportUrl: String (storage link or path)
- status: String (GENERATING, READY, PUBLISHING, PUBLISHED, FAILED)
- deliveredTo: List<String>
- deliveryStatus: String (SENT, FAILED)
```

### 2. Entity workflows

BatchJob workflow:
1. Initial State: CREATED (persisting BatchJob via POST triggers workflow)
2. Validation: automatic validation of parameters
3. Scheduling: if scheduledFor in future -> SCHEDULED
4. Execution: at scheduledFor or manual start -> IN_PROGRESS
5. Aggregate: collect metrics and reports -> COLLECTING_RESULTS
6. Completion: set COMPLETED or FAILED; notify admin delivery status recorded

```mermaid
stateDiagram-v2
    [*] --> "CREATED"
    "CREATED" --> "VALIDATING" : ValidateJobParametersProcessor
    "VALIDATING" --> "SCHEDULED" : IsScheduledCriterion
    "VALIDATING" --> "IN_PROGRESS" : StartImmediatelyCriterion
    "SCHEDULED" --> "IN_PROGRESS" : ScheduleJobProcessor
    "IN_PROGRESS" --> "COLLECTING_RESULTS" : StartIngestionProcessor
    "COLLECTING_RESULTS" --> "COMPLETED" : AggregateResultsProcessor
    "COLLECTING_RESULTS" --> "FAILED" : IfErrorsCriterion
    "COMPLETED" --> "NOTIFIED" : PublishReportProcessor
    "NOTIFIED" --> [*]
    "FAILED" --> [*]
```

Processors: ValidateJobParametersProcessor, ScheduleJobProcessor, StartIngestionProcessor, AggregateResultsProcessor, PublishReportProcessor  
Criteria: IsScheduledCriterion, StartImmediatelyCriterion, IfErrorsCriterion

UserRecord workflow:
1. Initial: INGESTED (each created UserRecord triggers processing)
2. Transform: apply normalization & enrichment -> TRANSFORMED
3. Deduplicate/Validate: check duplicates/valid email -> VERIFIED or DUPLICATE
4. Persist: store to data store -> STORED
5. Archive/Update: older records archived -> ARCHIVED (manual or automatic)

```mermaid
stateDiagram-v2
    [*] --> "INGESTED"
    "INGESTED" --> "TRANSFORMED" : TransformUserProcessor
    "TRANSFORMED" --> "VERIFIED" : ValidateUserProcessor
    "VERIFIED" --> "STORED" : PersistUserProcessor
    "VERIFIED" --> "DUPLICATE" : DeduplicationCriterion
    "DUPLICATE" --> "STORED" : MergeProcessor
    "STORED" --> "ARCHIVED" : ArchiveCriterion
    "ARCHIVED" --> [*]
```

Processors: TransformUserProcessor, ValidateUserProcessor, PersistUserProcessor, MergeProcessor  
Criteria: DeduplicationCriterion, ArchiveCriterion

MonthlyReport workflow:
1. Created: GENERATED when BatchJob aggregates metrics
2. Generating: compute metrics and build attachment -> READY
3. Publishing: send to admin emails -> PUBLISHED or FAILED
4. Post-process: record delivery status and archive

```mermaid
stateDiagram-v2
    [*] --> "CREATED"
    "CREATED" --> "GENERATING" : GenerateMetricsProcessor
    "GENERATING" --> "READY" : IsReportCompleteCriterion
    "READY" --> "PUBLISHING" : CreateAttachmentProcessor
    "PUBLISHING" --> "PUBLISHED" : DeliverReportProcessor
    "PUBLISHING" --> "FAILED" : DeliveryFailureCriterion
    "PUBLISHED" --> "ARCHIVED" : ArchiveReportProcessor
    "FAILED" --> [*]
```

Processors: GenerateMetricsProcessor, CreateAttachmentProcessor, DeliverReportProcessor, ArchiveReportProcessor  
Criteria: IsReportCompleteCriterion, DeliveryFailureCriterion

### 3. Pseudo code for processor classes (concise)

TransformUserProcessor
```java
void process(UserRecord u) {
  u.sourcePayload = u.sourcePayload; // raw present
  u.firstName = normalizeName(extract(u.sourcePayload, name));
  u.lastName = normalizeName(...);
  u.email = lowerCase(extract(...));
  u.transformedAt = now();
  emit(u); // persist triggers next workflow step
}
```

ValidateUserProcessor
```java
void process(UserRecord u) {
  if (!isValidEmail(u.email)) markError(u, reason);
  else u.normalized = true;
  emit(u);
}
```

PersistUserProcessor
```java
void process(UserRecord u) {
  if (existsByExternalId(u.externalId)) merge(u);
  store(u);
  updateMetricsCounter();
}
```

StartIngestionProcessor (invoked by BatchJob)
```java
void process(BatchJob job) {
  List raw = fetchFakerestUsers();
  for each r in raw create UserRecord with sourcePayload r;
  job.processedUserCount = raw.size();
}
```

GenerateMetricsProcessor
```java
void process(BatchJob job) {
  MonthlyReport rep = new MonthlyReport();
  rep.month = job.scheduledFor.substr(0,7);
  rep.totalUsers = countUsersForMonth(rep.month);
  rep.newUsers = countNewUsers(rep.month);
  rep.changedUsers = countChangedUsers(rep.month);
  rep.generatedAt = now();
  persist(rep);
}
```

DeliverReportProcessor
```java
void process(MonthlyReport r, BatchJob job) {
  uploadAttachment(r);
  for email in job.adminEmails sendEmail(email, r.reportUrl);
  r.deliveryStatus = resolveDeliveryStatus();
  persist(r);
}
```

### 4. API Endpoints Design Rules (Cyoda EDA rules)
- Only BatchJob is created via POST (orchestration). Creating BatchJob produces technicalId and starts workflow.
- UserRecord and MonthlyReport are created by processors; provide GET by technicalId for retrieval.
- POST responses must return only technicalId.

POST create BatchJob
```json
Request:
{
  "jobName": "Monthly user ingest",
  "scheduledFor": "2025-09-01T00:00:00Z",
  "timezone": "UTC",
  "adminEmails": ["admin@example.com"]
}
Response:
{
  "technicalId": "job_12345"
}
```

GET BatchJob by technicalId
```json
Response:
{
  "technicalId": "job_12345",
  "jobName": "Monthly user ingest",
  "scheduledFor": "2025-09-01T00:00:00Z",
  "timezone": "UTC",
  "adminEmails": ["admin@example.com"],
  "status": "COMPLETED",
  "createdAt": "2025-09-01T00:00:00Z",
  "startedAt": "2025-09-01T00:00:05Z",
  "finishedAt": "2025-09-01T00:02:30Z",
  "processedUserCount": 1000,
  "errorMessage": null
}
```

GET UserRecord by technicalId
```json
Response:
{
  "technicalId": "user_987",
  "externalId": 987,
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "transformedAt": "2025-09-01T00:00:10Z",
  "storedAt": "2025-09-01T00:00:12Z",
  "normalized": true
}
```

GET MonthlyReport by technicalId
```json
Response:
{
  "technicalId": "report_2025-09",
  "month": "2025-09",
  "generatedAt": "2025-09-01T00:01:00Z",
  "totalUsers": 1000,
  "newUsers": 50,
  "changedUsers": 20,
  "reportUrl": "s3://reports/report_2025-09.pdf",
  "status": "PUBLISHED",
  "deliveredTo": ["admin@example.com"],
  "deliveryStatus": "SENT"
}
```

If you want I can:
- expand fields (retention, archival rules)
- add GET by condition (month filter) or GET all endpoints
- increase number of entities (up to 10) for audit, notification, or storage objects

Tell me which of these you want refined.