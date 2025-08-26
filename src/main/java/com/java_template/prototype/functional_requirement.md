### 1. Entity Definitions
```
CommentAnalysisJob:
- post_id: Integer (post identifier to fetch comments for)
- recipient_email: String (email to send the report to)
- schedule: String (optional scheduling info: immediate, cron, etc.)
- status: String (CREATED, VALIDATING, INGESTING, ANALYZING, READY_TO_SEND, SENDING, COMPLETED, FAILED)
- requested_at: DateTime (when the job was created)
- completed_at: DateTime (when processing finished)
- metrics_config: Object (optional list of metrics to compute)

Comment:
- id: Integer (comment id from source API)
- post_id: Integer (post identifier)
- name: String (commenter name)
- email: String (commenter email)
- body: String (comment text)
- fetched_at: DateTime (when fetched)
- source: String (source URI)

AnalysisReport:
- report_id: String (unique report id)
- job_id: String (reference to CommentAnalysisJob)
- post_id: Integer
- generated_at: DateTime
- summary: String (human readable summary)
- metrics: Object (json with computed metrics like count, avg_length, top_words, sentiment_summary)
- recipient_email: String
- status: String (GENERATED, SENDING, SENT, FAILED)
- sent_at: DateTime
```

Note: 3 entities used (default). No extra entities added.

---

### 2. Entity workflows

CommentAnalysisJob workflow:
1. Initial State: CREATED when POSTed (event triggers processing)
2. Validation: VALIDATING (automatic) — check post_id and recipient_email
3. Ingestion: INGESTING (automatic) — fetch comments from external API
4. Storage: INGESTING -> (Store comments) -> ANALYZING
5. Analysis: ANALYZING (automatic) — compute metrics and create AnalysisReport
6. Ready to Send: READY_TO_SEND (automatic) — report created
7. Send: SENDING (automatic) — send report email
8. Completion: COMPLETED or FAILED

```mermaid
stateDiagram-v2
    [*] --> "CREATED"
    "CREATED" --> "VALIDATING" : ValidateJobProcessor, *automatic*
    "VALIDATING" --> "INGESTING" : ValidationPassedCriterion
    "INGESTING" --> "ANALYZING" : FetchAndStoreCommentsProcessor, *automatic*
    "ANALYZING" --> "READY_TO_SEND" : AnalyzeCommentsProcessor, *automatic*
    "READY_TO_SEND" --> "SENDING" : GenerateReportProcessor, *automatic*
    "SENDING" --> "COMPLETED" : SendEmailProcessor
    "SENDING" --> "FAILED" : SendEmailFailureCriterion
```

Processors and criteria for Job:
- Processors: ValidateJobProcessor, FetchAndStoreCommentsProcessor, AnalyzeCommentsProcessor, GenerateReportProcessor, SendEmailProcessor
- Criteria: ValidationPassedCriterion, SendEmailFailureCriterion

Comment workflow:
1. Created: COMMENT_FETCHED when fetched from API and persisted
2. Stored: STORED (available for analysis)
3. Optional: FLAGGED if content triggers moderation (manual)

```mermaid
stateDiagram-v2
    [*] --> "COMMENT_FETCHED"
    "COMMENT_FETCHED" --> "STORED" : StoreCommentProcessor, *automatic*
    "STORED" --> "FLAGGED" : ModerationCriterion
    "FLAGGED" --> [*] : ManualReview
```

Processors/criteria:
- Processors: StoreCommentProcessor
- Criteria: ModerationCriterion

AnalysisReport workflow:
1. Created: GENERATED when analysis completes
2. SENDING: system attempts to email
3. SENT or FAILED

```mermaid
stateDiagram-v2
    [*] --> "GENERATED"
    "GENERATED" --> "SENDING" : SendReportProcessor, *automatic*
    "SENDING" --> "SENT" : EmailSentCriterion
    "SENDING" --> "FAILED" : EmailFailureCriterion
```

Processors/criteria:
- Processors: SendReportProcessor
- Criteria: EmailSentCriterion, EmailFailureCriterion

---

### 3. Pseudo code for processor classes

ValidateJobProcessor
```
process(job):
  if job.post_id is null or invalidFormat(job.recipient_email):
    job.status = FAILED
    persist(job)
    return
  job.status = INGESTING
  persist(job)
```

FetchAndStoreCommentsProcessor
```
process(job):
  comments = fetch("https://jsonplaceholder.typicode.com/comments?postId=" + job.post_id)
  for c in comments:
    comment = new Comment(...)
    comment.fetched_at = now()
    persist(comment)  // each persist triggers Comment workflow
  job.status = ANALYZING
  persist(job)
```

StoreCommentProcessor
```
process(comment):
  saveToDataStore(comment)
  comment.status = STORED
  persist(comment)
```

AnalyzeCommentsProcessor
```
process(job):
  comments = queryCommentsByPost(job.post_id)
  metrics = {
    count: comments.size,
    avg_length_words: avg(words(comment.body)),
    top_words: topN(words across bodies),
    unique_emails: uniqueCount(emails),
    sentiment_summary: simpleSentimentSummary(comments)
  }
  report = new AnalysisReport(...)
  report.metrics = metrics
  report.generated_at = now()
  report.status = GENERATED
  persist(report)
  job.status = READY_TO_SEND
  persist(job)
```

SendEmailProcessor / SendReportProcessor
```
process(report or job):
  payload = formatReport(report)
  ok = sendEmail(report.recipient_email, payload)
  if ok:
    report.status = SENT
    report.sent_at = now()
  else:
    report.status = FAILED
  persist(report)
  if job exists: update job.status = COMPLETED or FAILED
```

Criteria examples:
- ValidationPassedCriterion: checks job.post_id and recipient_email good
- ModerationCriterion: flag if banned words or too short/long
- EmailSentCriterion / EmailFailureCriterion: checks email delivery result

---

### 4. API Endpoints Design Rules

Rules followed:
- Only orchestration entity has POST that creates an entity and triggers processing.
- POST endpoints return only technicalId.
- GET endpoints for retrieving stored results.

1) Create Job (start event)
POST /jobs
Request:
```json
{
  "post_id": 1,
  "recipient_email": "ops@example.com",
  "schedule": "immediate",
  "metrics_config": {}
}
```
Response (POST returns only technicalId):
```json
{
  "technicalId": "job_123456"
}
```

2) Get Job by technicalId
GET /jobs/{technicalId}
Response:
```json
{
  "technicalId": "job_123456",
  "post_id": 1,
  "recipient_email": "ops@example.com",
  "status": "ANALYZING",
  "requested_at": "2025-08-26T12:00:00Z",
  "completed_at": null
}
```

3) Get comments by post_id (read only)
GET /comments?postId=1
Response:
```json
[
  {
    "id": 1,
    "post_id": 1,
    "name": "id labore ex et quam laborum",
    "email": "Eliseo@gardner.biz",
    "body": "...",
    "fetched_at": "2025-08-26T12:00:05Z",
    "source": "https://jsonplaceholder.typicode.com/comments"
  }
]
```

4) Get report by report_id
GET /reports/{report_id}
Response:
```json
{
  "report_id": "report_abc",
  "job_id": "job_123456",
  "post_id": 1,
  "generated_at": "2025-08-26T12:00:20Z",
  "summary": "5 comments analyzed. Mostly neutral. Top words ...",
  "metrics": { "count": 5, "avg_length_words": 42, "top_words": ["voluptate","quia"], "sentiment_summary": "neutral" },
  "recipient_email": "ops@example.com",
  "status": "SENT",
  "sent_at": "2025-08-26T12:01:00Z"
}
```

---

If you want, I can:
- add extra optional metrics,
- include a manual review step for flagged comments,
- or change the job scheduling semantics (immediate vs periodic). Which would you like to refine?