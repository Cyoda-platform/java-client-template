# Functional Requirements - Reporting Prototype

## Summary
This document defines entities, workflows, processors, and API rules for the reporting prototype that aggregates bookings from the Restful Booker service and generates reports. It reconciles previously inconsistent identifiers and statuses, clarifies assumptions (currency, partial overlap), and lists required processors and validation criteria.

---

## 1. Entities
All date/time values use ISO formats. Where relevant, separate date (YYYY-MM-DD) from datetime (YYYY-MM-DDThh:mm:ssZ).

### 1.1 ReportJob
Fields:
- technicalId: String (internal unique id, e.g., `job_abc123`)  -- REQUIRED
- jobName: String (human-friendly name for job) -- OPTIONAL
- initiatedBy: String (user or system) -- REQUIRED
- filterDateFrom: String (ISO date, optional) -- inclusive
- filterDateTo: String (ISO date, optional) -- inclusive
- minPrice: Number (optional)
- maxPrice: Number (optional)
- depositPaid: Boolean (optional)
- grouping: String enum (DAILY | WEEKLY | MONTHLY, optional) -- granularity for aggregation
- presentationType: String enum (TABLE | CHART, optional)
- status: String enum (PENDING | VALIDATING | IN_PROGRESS | COMPLETED | FAILED) -- REQUIRED
- createdAt: String (ISO datetime) -- REQUIRED
- completedAt: String (ISO datetime, optional)
- errorDetails: String (optional) -- description/cause when FAILED
- schedule: Object (optional) -- see Scheduling section if recurring jobs are supported

Notes:
- `technicalId` is the stable identifier returned by POST and used for subsequent GETs and references.
- `jobName` may be non-unique; `technicalId` is authoritative.

### 1.2 Booking
Fields:
- technicalId: String (internal unique id for persisted booking, e.g., `bkg_001`) -- REQUIRED
- bookingId: String (source booking id from Restful Booker) -- REQUIRED
- customerName: String (guest name)
- checkInDate: String (ISO date)
- checkOutDate: String (ISO date)
- totalPrice: Number (total booking price)
- currency: String (ISO 4217 currency code, e.g., "USD") -- RECOMMENDED
- depositPaid: Boolean (deposit indicator)
- status: String enum (CONFIRMED | CANCELLED)
- source: String (e.g., "RestfulBooker")
- persistedAt: String (ISO datetime)
- normalizedFields: Object (optional) -- fields added by enrichment (e.g., normalized name parts)

Notes:
- `technicalId` is required for GET /bookings/{technicalId}. The original `bookingId` is the source id and is stored as an attribute.
- Currency should be captured when available from the source. If the source does not provide currency, a default currency must be specified in system configuration.

### 1.3 Report
Fields:
- reportId: String (internal unique id, e.g., `report_xyz789`) -- REQUIRED
- technicalId: String (alias to reportId, used in endpoints) -- REQUIRED
- jobRef: String (ReportJob.technicalId) -- REQUIRED
- periodFrom: String (ISO date)
- periodTo: String (ISO date)
- metrics: Object (e.g., { totalRevenue: Number, avgPrice: Number, bookingCount: Number, currency: String })
- grouping: String enum (DAILY | WEEKLY | MONTHLY)
- groupingBuckets: Array of objects (each: { label: String, totalRevenue: Number, bookingCount: Number, avgPrice?: Number })
- presentationType: String enum (TABLE | CHART)
- generatedAt: String (ISO datetime)
- downloadUrl: String (optional)
- metadata: Object (optional) -- info such as source count, pages fetched, warnings

Notes:
- `jobRef` must reference the job's `technicalId`.
- `metrics` must include currency when monetary values are present.

---

## 2. Core workflows
This section corrects and clarifies state transitions and processor effects. All transitions are persisted.

### 2.1 ReportJob workflow (detailed)
States: PENDING -> VALIDATING -> IN_PROGRESS -> COMPLETED | FAILED

1. Creation: HTTP POST creates ReportJob with status = PENDING and returns `technicalId`. (Manual POST triggers event.)
2. Validation: Automatic transition to VALIDATING. ValidateJobCriterion checks parameter sanity (date ranges, min <= max price, grouping allowed). On success move to IN_PROGRESS to begin fetch. On validation failure set status = FAILED with errorDetails.
3. Fetch Bookings: StartFetchProcessor enqueues or starts FetchBookingsProcessor(s), status remains IN_PROGRESS.
4. Fetching & Persisting: FetchBookingsProcessor pages the Restful Booker API using the job filters. For each fetched booking:
   - BookingValidityCriterion checks basic sanity (dates present, price non-negative). Invalid items are logged as warnings and skipped.
   - EnrichBookingProcessor normalizes fields.
   - PersistBookingProcessor persists booking, assigns booking.technicalId and persistedAt.
   - Fetching respects source API rate limits, supports pagination and retry policy on transient errors.
5. Aggregation: When fetching completes for the requested period (or after configured timeouts), AggregateProcessor queries persisted bookings that match job filters and source, computes aggregates (see Aggregation rules below). AggregateProcessor creates a Report and invokes PersistReportProcessor.
6. Completion: If aggregation and report persistence succeed, job.status = COMPLETED, job.completedAt = now, and reportId is attached to the job record. If any non-recoverable error occurs, job.status = FAILED with errorDetails and completedAt set.
7. Notification/Publish: Optionally, a manual or automatic publish/notify step can be triggered after completion.

State diagram (logical):

[PENDING] -> [VALIDATING] -> [IN_PROGRESS] -> [COMPLETED]
                                            -> [FAILED]

Required criteria/processors:
- Criteria: ValidateJobCriterion
- Processors: StartFetchProcessor, FetchBookingsProcessor, AggregateProcessor, OnAggregationErrorProcessor, NotifyUsersProcessor (optional)

Notes:
- The earlier diagram used intermediate states (VALIDATING, FETCHING, AGGREGATING). The canonical statuses stored on ReportJob are PENDING, VALIDATING, IN_PROGRESS, COMPLETED, FAILED. Internal processors may log sub-states but should not rely on ephemeral states outside the canonical set.

### 2.2 Booking workflow (detailed)
1. Creation: Bookings are created by FetchBookingsProcessor with a source bookingId and initial status from source (CONFIRMED/CANCELLED).
2. Enrichment: EnrichBookingProcessor adds normalized fields.
3. Validation & Persist: BookingValidityCriterion validates; PersistBookingProcessor sets technicalId, persistedAt and saves.

State diagram:
[CREATED] -> [ENRICHING] -> [PERSISTED]

Required criteria/processors:
- Criteria: BookingValidityCriterion
- Processors: EnrichBookingProcessor, PersistBookingProcessor

### 2.3 Report workflow (detailed)
1. GENERATING: AggregateProcessor creates report data and builds a Report object. While building, temporary state GENERATING may be used internally; persisted Report should only reach READY after validation/persistence.
2. VALIDATING: ReportValidationCriterion ensures metrics exist and are reasonable (non-null counts, currency present for monetary values).
3. READY: PersistReportProcessor sets report.generatedAt, persists the report, and triggers FormatForPresentationProcessor to produce downloadUrl and presentation artifacts.
4. PUBLISHED (optional): Manual publish action moves report to published state (metadata field or archival flag); this is an audit/publishing action and is optional.

State diagram:
[GENERATING] -> [VALIDATING] -> [READY] -> [PUBLISHED]

Required criteria/processors:
- Criteria: ReportValidationCriterion
- Processors: PersistReportProcessor, FormatForPresentationProcessor, ManualPublishAction

---

## 3. Aggregation rules and edge cases
- Grouping granularity: DAILY, WEEKLY (ISO week), MONTHLY. Definitions:
  - DAILY: group by checkInDate (or the booking day) label in YYYY-MM-DD.
  - WEEKLY: ISO week number label (e.g., 2025-W27).
  - MONTHLY: YYYY-MM.
- Partial overlap: a booking partially overlapping the filter period counts if there is any overlap between [checkInDate, checkOutDate) and [filterDateFrom, filterDateTo] (inclusive). The booking contributes its full totalPrice to aggregates. If pro-rated price is required instead, this should be an explicit option on the ReportJob.
- Currency: Aggregation assumes monetary values are in a single currency. The Booking.currency field must be present for consistent financial aggregation. If multiple currencies exist in the result set, the system must either convert to a target currency (using configured rates) or reject aggregation with a warning. Default behavior: if mixed currencies found and no conversion config exists, mark report with a warning and include separated metrics per currency in report.metadata.
- Cancellations: status CANCELLED bookings are excluded from revenue metrics by default. If an alternative behavior is desired (e.g., include cancellations), expose as a job filter.
- Taxes and fees: totalPrice is treated as the total amount on the booking (tax inclusive if source provides); taxes are not separated unless the source provides tax fields.

---

## 4. Processors - updated pseudo code
These snippets include canonical status updates, error handling, and id assignment.

StartFetchProcessor:
```
class StartFetchProcessor {
  process(job) {
    if (!ValidateJobCriterion.check(job)) {
      job.status = FAILED
      job.errorDetails = "Validation failed: ..."
      job.completedAt = now
      save(job)
      throw Error("Validation failed")
    }
    // mark job as in-progress and persist
    job.status = IN_PROGRESS
    job.updatedAt = now
    save(job)

    // enqueue fetch with job.technicalId and filters
    enqueue(FetchBookingsProcessor, job.technicalId, job.filters)
  }
}
```

FetchBookingsProcessor:
```
class FetchBookingsProcessor {
  process(jobId) {
    job = loadJob(jobId)
    page = 1
    while (morePages) {
      try {
        results, morePages = callRestfulBookerApi(job.filters, page)
      } catch (TransientError e) {
        retry with backoff up to N times
      } catch (FatalError e) {
        job.status = FAILED
        job.errorDetails = "Fetch failed: " + e.message
        job.completedAt = now
        save(job)
        trigger OnAggregationErrorProcessor(job)
        return
      }

      for item in results:
        if (!BookingValidityCriterion.check(item)) {
          logWarning(item)
          continue
        }
        enriched = EnrichBookingProcessor.process(item)
        PersistBookingProcessor.process(enriched)
      page++
    }

    // all pages processed; trigger aggregation
    enqueue(AggregateProcessor, job.technicalId)
  }
}
```

AggregateProcessor:
```
class AggregateProcessor {
  process(jobId) {
    job = loadJob(jobId)
    try {
      bookings = queryBookings(source="RestfulBooker", filters=job.filters)
      aggregate = computeAggregates(bookings, grouping=job.grouping)
      report = buildReport(job.technicalId, job.filterDateFrom, job.filterDateTo, aggregate, job.presentationType)
      PersistReportProcessor.process(report)

      job.status = COMPLETED
      job.completedAt = now
      job.reportId = report.reportId
      save(job)
    } catch (Exception e) {
      job.status = FAILED
      job.errorDetails = "Aggregation failed: " + e.message
      job.completedAt = now
      save(job)
      trigger OnAggregationErrorProcessor(job)
    }
  }
}
```

PersistBookingProcessor:
```
class PersistBookingProcessor {
  process(booking) {
    booking.technicalId = generateTechnicalId()
    booking.persistedAt = now
    save(booking)
  }
}
```

PersistReportProcessor:
```
class PersistReportProcessor {
  process(report) {
    report.reportId = generateReportId()
    report.generatedAt = now
    save(report)
    FormatForPresentationProcessor.process(report)
  }
}
```

FormatForPresentationProcessor:
- Produces downloadUrl (CSV/JSON) and presentation artifacts (chart data) and updates report metadata. Should be idempotent.

OnAggregationErrorProcessor:
- Handles retries, partial reports, alerting. May mark job as FAILED and send notifications.

---

## 5. API Endpoints and rules (updated & consistent)
Design rules:
- POST /jobs/reports creates a ReportJob and returns { technicalId } only.
- GET /jobs/{technicalId} returns the canonical stored ReportJob object.
- GET /reports/{technicalId} returns the generated Report.
- GET /bookings/{technicalId} returns the persisted Booking.
- No listing endpoints by filter are provided by default. Add if requested.
- Endpoints must return 404 for unknown technicalId.

Examples:

1) Create report job
- POST /jobs/reports
Request JSON:
```
{
  "jobName":"MonthlyRevenue",
  "initiatedBy":"alice",
  "filterDateFrom":"2025-07-01",
  "filterDateTo":"2025-07-31",
  "minPrice":0,
  "maxPrice":1000,
  "depositPaid":true,
  "grouping":"DAILY",
  "presentationType":"CHART"
}
```
Response JSON:
```
{ "technicalId": "job_abc123" }
```

2) Get job status
- GET /jobs/{technicalId}
Response JSON (canonical fields):
```
{
  "technicalId":"job_abc123",
  "jobName":"MonthlyRevenue",
  "status":"COMPLETED",
  "createdAt":"2025-08-01T08:00:00Z",
  "completedAt":"2025-08-01T08:01:30Z",
  "reportId":"report_xyz789",
  "errorDetails": null
}
```

3) Get report
- GET /reports/{technicalId}
Response JSON:
```
{
  "reportId":"report_xyz789",
  "jobRef":"job_abc123",
  "periodFrom":"2025-07-01",
  "periodTo":"2025-07-31",
  "metrics":{"totalRevenue":12345.67,"avgPrice":234.56,"bookingCount":52, "currency":"USD"},
  "groupingBuckets":[{"label":"2025-07-01","totalRevenue":123.45,"bookingCount":1}],
  "presentationType":"CHART",
  "generatedAt":"2025-08-01T08:01:00Z",
  "downloadUrl":"/downloads/report_xyz789.csv",
  "metadata": {"sourceCount": 52 }
}
```

4) Get booking
- GET /bookings/{technicalId}
Response JSON:
```
{
  "technicalId":"bkg_0001",
  "bookingId":"RB_1001",
  "customerName":"John Doe",
  "checkInDate":"2025-07-05",
  "checkOutDate":"2025-07-08",
  "totalPrice":300.00,
  "currency":"USD",
  "depositPaid":true,
  "status":"CONFIRMED",
  "persistedAt":"2025-08-01T07:55:00Z"
}
```

---

## 6. Non-functional & operational concerns
- Pagination: FetchBookingsProcessor must support paging and not fetch unlimited data in a single call.
- Rate limiting & retries: Respect source API rate limits; implement exponential backoff and a retry policy for transient errors.
- Idempotency: POST /jobs/reports should be idempotent if client supplies an idempotency key. Otherwise each POST produces a new technicalId.
- Observability: Record metrics for pages fetched, failed records, processing time, and errors. Store warnings in report.metadata.
- Data retention & GDPR: Define retention policy for persisted bookings and reports. Support deletion by technicalId where required.

---

## 7. Assumptions & decisions (defaults applied)
- Grouping granularity: DAILY | WEEKLY | MONTHLY (ISO week). Default = MONTHLY if not provided.
- Partial overlap: bookings partially overlapping the date window count and contribute their full price. If pro-rating is required, add a job option proRate = true.
- Currency: totalPrice uses a currency field. If multiple currencies present and no conversion configured, the report will include per-currency metrics and a warning in metadata.
- Cancellation: CANCELLED bookings are excluded from revenue by default.
- Scheduled/Recurring jobs: NOT enabled by default. If scheduling is required, set schedule object on ReportJob (cron, interval, nextRun) and add scheduler states and lifecycle. Indicate "yes" to enable scheduling and this file will be updated with scheduling states and examples.

---

## 8. Open questions (pick defaults or confirm)
1. Confirm grouping granularity: DAILY / WEEKLY / MONTHLY? (Default: MONTHLY)
2. Should partially overlapping bookings be pro-rated or fully counted? (Default: fully counted)
3. Confirm currency/tax treatment (Default: currency field on Booking and no separate tax handling)
4. Do you want scheduled recurring ReportJobs? (Default: NO)

Please confirm any of the above which differ from your intended behavior and I will update this document accordingly.
