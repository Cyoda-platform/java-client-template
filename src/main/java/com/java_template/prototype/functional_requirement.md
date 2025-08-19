# Functional Requirements — Book Fetching & Weekly Report System

## Overview
This document defines the entities, workflows, processors, criteria and API surface for the Book Fetch + WeeklyReport system. It corrects inconsistencies and clarifies ambiguous behaviors in the previous version (processor counts, criteria, report range, popularity rule and API details). Key decisions applied here:
- Popularity (default): composite score — 70% normalized pageCount + 30% recency score.
- Report range (default): delta since FetchJob.lastRunAt (only new/updated books) to avoid reprocessing the entire dataset. This can be configured per job.
- Delivery format (default): email summary + CSV attachment. Recipients are taken from FetchJob.recipients.

---

## 1. Entity Definitions
All timestamps are ISO-8601 (DateTime). Fields labeled "technicalId" are system-generated opaque identifiers used for orchestration and retrieval.

### Book
- id: Integer (API source id; primary key from upstream API)
- title: String
- description: String (full description; optional)
- pageCount: Integer
- excerpt: String (short excerpt; optional)
- publishDate: DateTime
- retrievedAt: DateTime (when fetched from source)
- sourceStatus: String (enum: ok | missing | invalid) — indicates validation status and source completeness
- popularityScore: Double (computed score for ranking; 0.0..1.0 normalized)
- excluded: Boolean (optional; manual exclusion flag)
- correctedBy: String (optional; user id if manually corrected)

Notes:
- Books are created only by FetchJob processors (no public POST endpoint).
- `id` is the upstream source id and is the stable identifier for GET operations.

### FetchJob (orchestration entity)
- technicalId: String (system-generated id for this job definition or run)
- name: String (human name)
- runDay: String (e.g., Wednesday) — optional when the job is one-off/manual
- runTime: String (HH:mm) — interpreted in the configured timezone
- timezone: String (IANA timezone string, e.g., "UTC", "Europe/Paris")
- recurrence: String (enum: one-off | daily | weekly | cron-expression)
- recipients: Array<String> (emails for report delivery)
- lastRunAt: DateTime (timestamp of last completed run)
- nextRunAt: DateTime (computed next run time)
- status: String (enum: scheduled | paused | running | failed | completed)
- triggeredBy: String (enum: manual | schedule)
- parameters: Object (job-specific params, e.g., { "topNPopular": 5, "reportRange": "delta" })
- createdAt: DateTime
- updatedAt: DateTime

Notes:
- A FetchJob represents a configured scheduled job or a manual run request. Each run will update lastRunAt/nextRunAt and may create a WeeklyReport.

### WeeklyReport
- technicalId: String (system-generated id)
- fetchJobId: String (technicalId of the originating FetchJob)
- weekStartDate: DateTime
- weekEndDate: DateTime
- totalBooks: Integer
- totalPages: Integer
- avgPages: Double
- topTitles: Array<Object> (each: { id: Integer, title: String, pageCount: Integer, popularityScore: Double })
- popularTitles: Array<Object> (each: { id: Integer, title: String, descriptionSnippet: String, excerptSnippet: String })
- publicationSummary: Object ({ "newest": {id, publishDate}, "oldest": {id, publishDate}, "countsByYear": { year: count } })
- generationTimestamp: DateTime
- reportStatus: String (enum: generated | sent | failed | archived)
- deliveryInfo: Object ({ recipients: Array<String>, emailStatus: String (delivered | failed | partial), messageId: String?, errors: Array<String> })

Notes:
- WeeklyReports are created by the analyzeMetricsProcessor (or assembleSummaryProcessor) and then delivered by sendReportProcessor.

---

## 2. Workflows
Workflows are stateful, with explicit processors and criteria. All automatic transitions are triggered by processors and criteria; manual transitions are permitted for administrative actions (pause/resume, manual run, manual review, manual exclusion of books).

### 2.1 FetchJob Workflow
Primary states and transitions:
- PENDING: job created, waiting for schedule or manual trigger
- RUNNING: active processing for a triggered run
- VALIDATING: post-fetch validation of persisted Books (per-run validation phase)
- ANALYZING: metrics computation and report creation
- REPORT_CREATED: WeeklyReport persisted
- SENT: report successfully delivered
- DELIVERY_FAILED: report delivery failed
- COMPLETED: job run completed successfully (updates lastRunAt/nextRunAt)
- FAILED: job run failed

Mermaid state diagram (conceptual):

stateDiagram-v2
    [*] --> PENDING
    PENDING --> RUNNING : scheduleTriggerProcessor / manualTrigger
    RUNNING --> PERSISTING : persistBooksProcessor
    PERSISTING --> VALIDATING : persistBooksProcessor completes
    VALIDATING --> ANALYZING : validateBooksCriterion passed
    VALIDATING --> FAILED : validateBooksCriterion failed
    ANALYZING --> REPORT_CREATED : analyzeMetricsProcessor
    REPORT_CREATED --> SENT : sendReportProcessor (success)
    REPORT_CREATED --> DELIVERY_FAILED : sendReportProcessor (failure)
    SENT --> COMPLETED : finalizeJobProcessor
    DELIVERY_FAILED --> FAILED : finalizeJobProcessor
    COMPLETED --> [*]
    FAILED --> [*]

Processors (ordered, per run):
- scheduleTriggerProcessor: triggers a run (by schedule or manual). Writes RUNNING state.
- persistBooksProcessor: calls external API, maps results to Book entities, sets retrievedAt and preliminary sourceStatus, persists Book entities. After persisting each Book the Book workflow triggers (validation/scoring).
- validateBooksProcessor: (per-run aggregator) applies validateBooksCriterion across the fetched set and updates job-level validation summary (e.g., number of invalid records). Drives transition to ANALYZING if validation summary passes configured thresholds.
- analyzeMetricsProcessor: queries Book data (default: delta since lastRunAt unless job.parameters.reportRange overrides to snapshot), computes metrics (totals, averages, popularity rankings) and creates WeeklyReport entity.
- assembleSummaryProcessor: optional processor to format summary text, CSV attachments and prepare delivery payloads.
- sendReportProcessor: sends email and attachments to FetchJob.recipients, records deliveryInfo and reportStatus on WeeklyReport.
- finalizeJobProcessor: sets FetchJob.lastRunAt, computes nextRunAt, updates status to COMPLETED/FAILED and persists.

Criteria:
- validateBooksCriterion: per-run validation rule (e.g., percentage of invalid records must be below configured threshold). Also checks for required fields presence for analytics.
- deliverySuccessCriterion: evaluates whether sendReportProcessor succeeded for all recipients (used to set reportStatus and job final state).

Notes:
- The previous version omitted validateBooksProcessor from the FetchJob processor list and incorrectly counted processors; this version corrects that.
- The analyzeMetricsProcessor uses delta by default (query books where retrievedAt > fetchJob.lastRunAt OR retrievedAt <= now() when lastRunAt is null) unless explicitly configured otherwise.

### 2.2 Book Workflow
States:
- NEW: Book record persisted by persistBooksProcessor
- VALIDATED: required fields present and acceptable values
- INVALID: validation failed (sourceStatus set to invalid)
- SCORED: popularityScore computed
- AVAILABLE: included in analysis / marked available for reporting

Mermaid diagram (conceptual):

stateDiagram-v2
    [*] --> NEW
    NEW --> VALIDATED : validateBookProcessor (passes)
    NEW --> INVALID : validateBookProcessor (fails)
    VALIDATED --> SCORED : computePopularityProcessor
    SCORED --> AVAILABLE : markAvailableProcessor
    INVALID --> [*]
    AVAILABLE --> [*]

Processors:
- validateBookProcessor: verifies presence of required fields (title, pageCount, publishDate) and sets sourceStatus (ok | invalid | missing). Persists Book updates.
- computePopularityProcessor: computes popularityScore using configured formula (default: normalized pageCount * 0.7 + recencyScore(publishDate) * 0.3). Persists Book updates.
- markAvailableProcessor: flags the Book as available for inclusion in reports (e.g., available=true). Persists Book updates.

Criteria:
- isBookValidCriterion: true when Book.sourceStatus == ok and required fields present. Used by both per-book decisions and by validateBooksCriterion aggregation at job level.

Notes:
- Manual transitions: books can be manually excluded or corrected by administrative actions which update `excluded` or `correctedBy` fields and re-trigger validation/scoring.

### 2.3 WeeklyReport Workflow
States:
- GENERATED: created by analyzeMetricsProcessor
- UNDER_REVIEW: optional manual review by analyst
- SENT: successfully delivered
- FAILED: delivery failed
- ARCHIVED: archived after successful delivery and retention actions

Mermaid diagram (conceptual):

stateDiagram-v2
    [*] --> GENERATED
    GENERATED --> UNDER_REVIEW : manualReview
    UNDER_REVIEW --> GENERATED : approveReview
    GENERATED --> SENT : sendReportProcessor (success)
    GENERATED --> FAILED : sendReportProcessor (failure)
    SENT --> ARCHIVED : archiveReportProcessor
    SENT --> [*]
    FAILED --> [*]

Processors:
- assembleSummaryProcessor: formats human-readable summary and builds CSV/attachment payloads.
- sendReportProcessor: sends email and attachments, updates WeeklyReport.deliveryInfo and reportStatus.
- archiveReportProcessor: marks report as archived and persists audit information.

Criteria:
- isReportCompleteCriterion: true when required fields (totals, topTitles, generationTimestamp, deliveryInfo) are present. Used to gate sendReportProcessor.

---

## 3. Pseudocode (updated) — Processor Implementations
These pseudocode snippets reflect latest logic (validation step included, delta report range, explicit assembleSummary step, and corrected processor names).

persistBooksProcessor
```
class PersistBooksProcessor {
  process(fetchJob) {
    response = callExternalBookApi(fetchJob.parameters)
    for each item in response {
      if item is null: continue
      book = mapToBookEntity(item)
      book.retrievedAt = now()
      book.sourceStatus = preliminaryStatus(item) // e.g., missing fields -> missing
      persist(book) // persisting triggers Book workflow (validate -> score)
    }
  }
}
```

validateBookProcessor
```
class ValidateBookProcessor {
  process(book) {
    if missing title or pageCount or publishDate {
      book.sourceStatus = "invalid"
      persist(book)
      return
    }
    book.sourceStatus = "ok"
    persist(book)
  }
}
```

computePopularityProcessor
```
class ComputePopularityProcessor {
  process(book, params) {
    // Normalize pageCount (e.g., min-max or log scale) to 0..1
    normalizedPages = normalizePageCount(book.pageCount)
    recency = recencyScore(book.publishDate) // 0..1, newer -> higher
    weightPages = params.pageWeight ?? 0.7
    weightRecency = params.recencyWeight ?? 0.3
    score = clamp(normalizedPages * weightPages + recency * weightRecency, 0.0, 1.0)
    book.popularityScore = score
    persist(book)
  }
}
```

analyzeMetricsProcessor
```
class AnalyzeMetricsProcessor {
  process(fetchJob) {
    // Default: delta range since last successful run
    rangeStart = fetchJob.lastRunAt
    rangeEnd = now()

    if fetchJob.parameters.reportRange == "snapshot" {
      // ignore lastRunAt and use full snapshot as of now
      books = queryAllAvailableBooks()
    } else {
      // default delta behavior: only new/updated books since lastRunAt
      books = queryBooksWhere(retrievedAt > rangeStart AND retrievedAt <= rangeEnd AND excluded != true)
    }

    metrics = computeTotalsAndAverages(books)
    topTitles = selectTopNBy(books, "popularityScore", fetchJob.parameters.topNPopular ?? 5)

    report = new WeeklyReport(
      fetchJobId = fetchJob.technicalId,
      weekStartDate = rangeStart ?? deriveWeekStart(rangeEnd),
      weekEndDate = rangeEnd,
      totalBooks = metrics.totalBooks,
      totalPages = metrics.totalPages,
      avgPages = metrics.avgPages,
      topTitles = topTitles,
      popularTitles = buildPopularTitlesSnippet(books),
      publicationSummary = computePublicationSummary(books),
      generationTimestamp = now(),
      reportStatus = "generated"
    )

    persist(report) // triggers WeeklyReport workflow
  }
}
```

assembleSummaryProcessor
```
class AssembleSummaryProcessor {
  process(report) {
    report.summaryText = formatSummary(report)
    report.attachment = buildCsvAttachment(report.topTitles, report.popularTitles)
    persist(report)
  }
}
```

sendReportProcessor
```
class SendReportProcessor {
  process(report, fetchJob) {
    recipients = fetchJob.recipients
    emailResult = sendEmail(recipients, report.summaryText, report.attachment)
    report.deliveryInfo = {
      recipients: recipients,
      emailStatus: emailResult.status, // delivered | failed | partial
      messageId: emailResult.messageId,
      errors: emailResult.errors
    }
    report.reportStatus = emailResult.success ? "sent" : "failed"
    persist(report)
  }
}
```

finalizeJobProcessor
```
class FinalizeJobProcessor {
  process(fetchJob, runResult) {
    fetchJob.lastRunAt = now()
    fetchJob.nextRunAt = computeNextRun(fetchJob)
    fetchJob.status = runResult.success ? "completed" : "failed"
    persist(fetchJob)
  }
}
```

---

## 4. API Endpoints — Design Rules & Examples
Rules:
- FetchJob orchestration: POST to create a new job or trigger a run (returns technicalId). GET by technicalId returns full job representation.
- WeeklyReport: GET by technicalId allowed. No POST for WeeklyReport or Book (system-managed).
- Book: GET by source id allowed. Books contain the upstream `id` but no separate technicalId.
- POST responses should return { "technicalId": "..." } for the created resource or run trigger.
- GET responses return full stored entity JSON including technicalId where applicable.

1) Create/Trigger Fetch Job (POST)
- POST /jobs/fetchBooks
- Request body JSON (example):
{
  "name": "Weekly Book Fetch",
  "runDay": "Wednesday",
  "runTime": "08:00",
  "timezone": "UTC",
  "recurrence": "weekly",
  "recipients": ["analytics@company.com"],
  "parameters": { "topNPopular": 5, "reportRange": "delta" },
  "triggeredBy": "schedule"
}
- Response: { "technicalId": "job-abc-123" }

2) Get FetchJob by technicalId (GET)
- GET /jobs/fetchBooks/{technicalId}
- Response: full FetchJob JSON (includes computed lastRunAt, nextRunAt, status).

3) Get WeeklyReport by technicalId (GET)
- GET /reports/weekly/{technicalId}
- Response: full WeeklyReport JSON including deliveryInfo and reportStatus.

4) Get Book by source id (GET)
- GET /books/{id}
- Response: Book JSON with fields defined above.

---

## 5. Open Questions / Decisions (captured and set by default in this version)
- Popularity rule: default composite score (70% pageCount, 30% recency). This is configurable per deployment via parameters pageWeight/recencyWeight.
- Report range: default = delta (new/updated since lastRunAt). Snapshot mode is supported if job.parameters.reportRange == "snapshot".
- Delivery: default email summary + CSV attachment. Other formats (PDF, JSON) can be added as an assembleSummaryProcessor option.

If you want a different default for any of these (e.g., top N by pageCount only, or snapshot by default), confirm which option you prefer and I will update the processors/criteria names and examples accordingly.

---

Revision notes:
- Fixed inconsistency in FetchJob processors count and added validateBooksProcessor to the flow.
- Clarified delta vs snapshot report range and made delta the default for efficiency.
- Standardized field names and added technicalId where appropriate.
- Expanded processor pseudocode to include assembleSummaryProcessor and explicit finalizeJobProcessor.

End of functional requirements.
