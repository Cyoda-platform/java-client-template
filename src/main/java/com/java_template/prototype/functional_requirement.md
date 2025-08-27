### 1. Entity Definitions
(Max entities defaulted to 3)

```
Booking:
- bookingId: Integer (provider booking identifier)
- firstName: String (customer first name)
- lastName: String (customer last name)
- totalPrice: Number (booking total price)
- depositPaid: Boolean (whether deposit was paid)
- bookingDates: Object (checkin: String ISO date, checkout: String ISO date)
- additionalNeeds: String (optional notes)
- source: String (external source identifier)
- persistedAt: String (ISO timestamp when stored)

ReportJob:
- jobId: String (business id)
- requestedBy: String (user who requested)
- requestedAt: String (ISO timestamp)
- filterCriteria: Object (dateRange {from,to}, minPrice, maxPrice, depositStatus, customerName)
- visualization: String (table/chart)
- schedule: String (optional cron or schedule descriptor)
- status: String (PENDING/PROCESSING/COMPLETED/FAILED/CANCELLED)
- resultReportId: String (id of generated report if any)

Report:
- reportId: String (business id)
- generatedAt: String (ISO timestamp)
- jobReference: String (ReportJob.jobId)
- scope: Object (filters applied)
- metrics: Object (totalRevenue, averagePrice, bookingCount, bookingsByRange)
- rows: Array (list of Booking summaries)
- visualizations: Object (chartData, tableData)
- exportedAt: String (ISO timestamp if exported)
```

---

### 2. Entity workflows

Booking workflow:
1. Initial State: RETRIEVED (booking data obtained by a job)
2. Validation: Check required fields and date consistency
3. Persistence: Store validated booking
4. Completion: Mark as STORED or mark as INVALID on failure

```mermaid
stateDiagram-v2
    [*] --> "RETRIEVED"
    "RETRIEVED" --> "VALIDATED" : ValidateBookingProcessor, *automatic*
    "VALIDATED" --> "STORED" : StoreBookingProcessor, *automatic*
    "VALIDATED" --> "INVALID" : ValidationFailedCriterion, *automatic*
    "INVALID" --> [*]
    "STORED" --> [*]
```

Processors and criteria for Booking:
- Processors: FetchBookingRecordProcessor (invoked by job), ValidateBookingProcessor, StoreBookingProcessor
- Criteria: ValidationFailedCriterion (missing fields/date issues)

ReportJob workflow:
1. Initial State: CREATED (POST event)
2. Scheduling/Start: Move to PROCESSING (automatic or manual start)
3. Fetch Bookings: Trigger booking retrieval
4. Filter & Aggregate: Apply filters and compute metrics
5. Report Generation: Build Report entity (table/chart)
6. Completion: COMPLETED or FAILED, optional notification

```mermaid
stateDiagram-v2
    [*] --> "CREATED"
    "CREATED" --> "PROCESSING" : StartJobProcessor, *automatic*
    "PROCESSING" --> "FETCHING_BOOKINGS" : FetchBookingsProcessor, *automatic*
    "FETCHING_BOOKINGS" --> "AGGREGATING" : FilterAndAggregateProcessor, *automatic*
    "AGGREGATING" --> "GENERATING_REPORT" : GenerateReportProcessor, *automatic*
    "GENERATING_REPORT" --> "COMPLETED" : PersistReportProcessor, *automatic*
    "GENERATING_REPORT" --> "FAILED" : ReportGenerationFailedCriterion
    "COMPLETED" --> [*]
    "FAILED" --> [*]
```

Processors and criteria for ReportJob:
- Processors: StartJobProcessor, FetchBookingsProcessor, FilterAndAggregateProcessor, GenerateReportProcessor, PersistReportProcessor
- Criteria: ReportGenerationFailedCriterion, JobRetryCriterion (optional)

Report workflow:
1. Initial State: GENERATED (created by job)
2. Rendering: Prepare table and chart views
3. Publishing: Mark as PUBLISHED (available for GET)
4. Exporting: Optionally export to CSV/PDF (EXPORT_PENDING -> EXPORTED)
5. Archival: ARCHIVED for old reports

```mermaid
stateDiagram-v2
    [*] --> "GENERATED"
    "GENERATED" --> "RENDERED" : RenderReportProcessor, *automatic*
    "RENDERED" --> "PUBLISHED" : PersistVisualizationProcessor, *automatic*
    "PUBLISHED" --> "EXPORT_PENDING" : ExportReportProcessor, *manual*
    "EXPORT_PENDING" --> "EXPORTED" : ExportCompleteCriterion
    "EXPORTED" --> "ARCHIVED" : ArchiveReportProcessor, *automatic*
    "ARCHIVED" --> [*]
```

Processors and criteria for Report:
- Processors: RenderReportProcessor, PersistVisualizationProcessor, ExportReportProcessor, ArchiveReportProcessor
- Criteria: ExportCompleteCriterion, RenderValidationCriterion

---

### 3. Pseudo code for processor classes

Note: processors run when entities are persisted; methods are illustrative.

FetchBookingsProcessor
```
class FetchBookingsProcessor {
  void process(ReportJob job) {
    // Use job.filterCriteria to call external booking source and yield raw bookings
    rawList = ExternalBookingSource.fetchAll(job.filterCriteria)
    for raw in rawList:
      emit Booking entity with fields populated and source=ExternalBookingSource
  }
}
```

ValidateBookingProcessor
```
class ValidateBookingProcessor {
  void process(Booking b) {
    if b.bookingDates.checkin is null or b.totalPrice is null:
      mark b as INVALID
    else if checkin >= checkout:
      mark b as INVALID
    else:
      mark b as VALIDATED
  }
}
```

StoreBookingProcessor
```
class StoreBookingProcessor {
  void process(Booking b) {
    if b is VALIDATED:
      datastore.save(b)
      set b.persistedAt = now
    else:
      log invalid booking
  }
}
```

FilterAndAggregateProcessor
```
class FilterAndAggregateProcessor {
  Report process(ReportJob job, List<Booking> bookings) {
    filtered = bookings.apply(job.filterCriteria)
    metrics = {
      totalRevenue: sum(filtered.totalPrice),
      averagePrice: avg(filtered.totalPrice),
      bookingCount: count(filtered),
      bookingsByRange: groupByDateRanges(filtered, job.filterCriteria.dateRange)
    }
    return new Report(...metrics, rows=filtered.summary)
  }
}
```

GenerateReportProcessor
```
class GenerateReportProcessor {
  void process(Report r) {
    r.visualizations = renderChartsAndTables(r.rows, r.metrics, r.scope.visualization)
    datastore.save(r)
  }
}
```

PersistReportProcessor
```
class PersistReportProcessor {
  void process(Report r) {
    r.generatedAt = now
    datastore.save(r)
    notify requester that report is ready
  }
}
```

ExportReportProcessor (manual trigger)
```
class ExportReportProcessor {
  void process(Report r) {
    file = exportToCSVorPDF(r)
    r.exportedAt = now
    datastore.save(r)
    attach file location to r
  }
}
```

---

### 4. API Endpoints Design Rules

- Orchestration entity: ReportJob has POST endpoint (creates job event) and GET by technicalId to retrieve results/status.
- Business data retrieval: GET endpoints for bookings and reports. Filtering via GET query parameters (explicitly requested).

Endpoints:

1) Create report job (POST triggers processing). Returns only technicalId.
Request:
```json
{
  "requestedBy": "alice@example.com",
  "filterCriteria": {
    "dateRange": {"from": "2025-01-01", "to": "2025-01-31"},
    "minPrice": 50,
    "maxPrice": 500,
    "depositStatus": "paid",
    "customerName": ""
  },
  "visualization": "chart",
  "schedule": ""
}
```
Response:
```json
{
  "technicalId": "job-123456"
}
```

2) Get job status and result by technicalId (GET)
Request: GET /report-jobs/job-123456
Response:
```json
{
  "jobId": "job-123456",
  "status": "COMPLETED",
  "requestedBy": "alice@example.com",
  "requestedAt": "2025-08-27T12:00:00Z",
  "resultReportId": "report-987"
}
```

3) Get report by reportId (GET)
Request: GET /reports/report-987
Response:
```json
{
  "reportId": "report-987",
  "generatedAt": "2025-08-27T12:01:00Z",
  "metrics": {
    "totalRevenue": 12345.67,
    "averagePrice": 234.56,
    "bookingCount": 53,
    "bookingsByRange": {"2025-01-01_to_2025-01-07": 10}
  },
  "visualizations": {
    "tableData": [...],
    "chartData": {...}
  }
}
```

4) Get bookings with filters (GET by condition - explicitly requested)
Request: GET /bookings?from=2025-01-01&to=2025-01-31&minPrice=50&depositPaid=true
Response:
```json
{
  "count": 53,
  "bookings": [
    {
      "bookingId": 1,
      "firstName": "John",
      "lastName": "Doe",
      "totalPrice": 120.0,
      "depositPaid": true,
      "bookingDates": {"checkin":"2025-01-05","checkout":"2025-01-08"}
    }
  ]
}
```

Notes and rules summary:
- POST endpoints return only technicalId.
- GET endpoints only retrieve stored results.
- ReportJob POST triggers Cyoda workflow: on persistence, processing starts (fetch → validate → store → aggregate → generate report).
- GET by condition for bookings is allowed here because user explicitly asked for filtering capabilities.
- Bookings are produced by ReportJob processing (not direct POST). Stored bookings can be retrieved via GET endpoints.
- All processors and criteria listed above are triggered automatically by Cyoda when entities are persisted.

If you want, I can:
- expand entities to include User/Schedule/ExportJob (up to 10),
- add sample visual layout options (chart types, table columns),
- or convert these definitions into Cyoda-ready job definitions. Which would you like next?