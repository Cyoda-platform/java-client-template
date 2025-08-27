### 1. Entity Definitions
```
IngestionJob:
- source_url: String (Pet Store API endpoint or other source)
- schedule_day: String (e.g., Monday)
- schedule_time: String (HH:MM timezone-local)
- data_formats: String (comma separated e.g., JSON,XML)
- time_window_days: Integer (how many past days to fetch)
- status: String (PENDING/VALIDATING/FETCHING/TRANSFORMING/COMPLETED/FAILED)
- created_by: String (user who created job)
- created_at: String (timestamp)

ReportJob:
- name: String (e.g., Weekly Product Performance)
- period_start: String (date)
- period_end: String (date)
- template_id: String (report template to use)
- output_formats: String (PDF,CSV)
- recipients: String (comma separated emails)
- status: String (PENDING/GENERATING/ATTACHING/SENDING/COMPLETED/FAILED)
- generated_url: String (location of produced report)
- created_at: String (timestamp)

Product:
- product_id: String (product identifier from source)
- name: String
- category: String
- price: Number
- cost: Number
- total_sales_volume: Integer (aggregated for period)
- total_revenue: Number (aggregated for period)
- inventory_on_hand: Integer
- last_updated: String (timestamp)
- performance_flag: String (e.g., UNDERPERFORMING/OK/RESTOCK)
```

### 2. Entity workflows

IngestionJob workflow:
1. Initial State: PENDING when POSTed
2. Validation: automatic -> VALIDATING (check source_url, formats)
3. Fetching: automatic -> FETCHING (call API, support JSON/XML)
4. Transforming: automatic -> TRANSFORMING (normalize, create/update Product entities, aggregate sales)
5. Completion: automatic -> COMPLETED or FAILED
6. Trigger: on COMPLETED emit event to start ReportJob if scheduled

```mermaid
stateDiagram-v2
    [*] --> "PENDING"
    "PENDING" --> "VALIDATING" : ValidateSourceCriterion, automatic
    "VALIDATING" --> "FETCHING" : StartFetchProcessor, automatic
    "FETCHING" --> "TRANSFORMING" : FetchCompleteCriterion
    "TRANSFORMING" --> "COMPLETED" : TransformAndPersistProcessor
    "TRANSFORMING" --> "FAILED" : TransformFailureCriterion
    "COMPLETED" --> [*]
    "FAILED" --> [*]
```

Processors & Criteria (IngestionJob):
- ValidateSourceCriterion (criterion)
- StartFetchProcessor (processor)
- FetchCompleteCriterion (criterion)
- TransformAndPersistProcessor (processor)
- TransformFailureCriterion (criterion)

ReportJob workflow:
1. Initial State: PENDING when created (manual or triggered)
2. Generating: automatic -> GENERATING (compute KPIs from Product aggregates)
3. Attaching: automatic -> ATTACHING (populate template, generate attachments)
4. Sending: automatic -> SENDING (email recipients)
5. Completion: automatic -> COMPLETED or FAILED

```mermaid
stateDiagram-v2
    [*] --> "PENDING"
    "PENDING" --> "GENERATING" : StartGenerateProcessor, automatic
    "GENERATING" --> "ATTACHING" : KPIsReadyCriterion
    "ATTACHING" --> "SENDING" : AttachFilesProcessor
    "SENDING" --> "COMPLETED" : SendEmailProcessor
    "SENDING" --> "FAILED" : EmailFailureCriterion
    "COMPLETED" --> [*]
    "FAILED" --> [*]
```

Processors & Criteria (ReportJob):
- StartGenerateProcessor
- KPIsReadyCriterion
- AttachFilesProcessor
- SendEmailProcessor
- EmailFailureCriterion

Product workflow:
1. Initial State: NEW when created/updated by IngestionJob
2. Updated: automatic -> UPDATED (sales & inventory aggregated)
3. Evaluation: automatic -> performance flag set (UNDERPERFORMING/RESTOCK/OK)
4. Monitored: automatic -> MONITORED (ongoing)

```mermaid
stateDiagram-v2
    [*] --> "NEW"
    "NEW" --> "UPDATED" : PersistProductProcessor, automatic
    "UPDATED" --> "EVALUATED" : EvaluatePerformanceProcessor
    "EVALUATED" --> "MONITORED" : FlagSetProcessor
    "MONITORED" --> [*]
```

Processors & Criteria (Product):
- PersistProductProcessor
- EvaluatePerformanceProcessor
- FlagSetProcessor
- DataQualityCriterion

### 3. Pseudo code for processor classes (concise)

StartFetchProcessor.process(IngestionJob job)
- fetch data from job.source_url with job.data_formats and job.time_window_days
- emit RawDataBundleEvent with payload
- update job.status = FETCHING

TransformAndPersistProcessor.process(RawDataBundleEvent e)
- parse records (JSON/XML)
- for each record compute sales, inventory
- upsert Product entity (persist triggers Product workflow)
- produce AggregationSummary
- update IngestionJob.status = COMPLETED

StartGenerateProcessor.process(ReportJob job)
- query Products for period_start..period_end
- compute KPIs: sales_volume, revenue, turnover per product/category
- store KPITable in ReportJob context
- set job.status = GENERATING

AttachFilesProcessor.process(ReportJob job)
- render template_id with KPI data
- export to formats in output_formats (PDF/CSV)
- set job.generated_url
- set job.status = ATTACHING

SendEmailProcessor.process(ReportJob job)
- compose summary body
- attach files from generated_url
- send to recipients
- set job.status = COMPLETED on success

EvaluatePerformanceProcessor.process(Product p)
- if total_sales_volume low and trend down then p.performance_flag = UNDERPERFORMING
- if inventory_on_hand <= reorder_threshold then p.performance_flag = RESTOCK
- else p.performance_flag = OK
- update p.last_updated

(Each processor should log errors and set corresponding FAILED states; criteria classes evaluate boolean conditions used in transitions.)

### 4. API Endpoints Design Rules

- POST endpoints (create orchestration entities only). Responses return only technicalId.

1) Create IngestionJob
POST /ingestion-jobs
Request:
```json
{
  "source_url":"https://petstore.swagger.io/v2/store",
  "schedule_day":"Monday",
  "schedule_time":"08:00",
  "data_formats":"JSON,XML",
  "time_window_days":7,
  "created_by":"admin@example.com"
}
```
Response (201):
```json
{
  "technicalId":"ingest-12345"
}
```

GET ingestion job by technicalId
GET /ingestion-jobs/{technicalId}
Response:
```json
{
  "technicalId":"ingest-12345",
  "source_url":"https://petstore.swagger.io/v2/store",
  "schedule_day":"Monday",
  "schedule_time":"08:00",
  "data_formats":"JSON,XML",
  "time_window_days":7,
  "status":"COMPLETED",
  "created_by":"admin@example.com",
  "created_at":"2025-08-25T08:00:00Z"
}
```

2) Create ReportJob
POST /report-jobs
Request:
```json
{
  "name":"Weekly Product Performance",
  "period_start":"2025-08-18",
  "period_end":"2025-08-24",
  "template_id":"weekly_v1",
  "output_formats":"PDF,CSV",
  "recipients":"victoria.sagdieva@cyoda.com"
}
```
Response:
```json
{
  "technicalId":"report-67890"
}
```

GET report job by technicalId
GET /report-jobs/{technicalId}
Response:
```json
{
  "technicalId":"report-67890",
  "name":"Weekly Product Performance",
  "status":"SENDING",
  "generated_url":"s3://reports/report-67890.pdf",
  "created_at":"2025-08-25T09:00:00Z"
}
```

3) GET product (read-only retrieval of processed results)
GET /products/{product_id}
Response:
```json
{
  "product_id":"123",
  "name":"Dog Toy",
  "category":"Toys",
  "price":9.99,
  "total_sales_volume":150,
  "total_revenue":1498.5,
  "inventory_on_hand":12,
  "performance_flag":"RESTOCK",
  "last_updated":"2025-08-25T08:10:00Z"
}
```

Notes and assumptions
- Only orchestration entities (IngestionJob, ReportJob) have POST endpoints; Product is created/updated by processors during ingestion.
- POST returns only technicalId per design rule.
- All entity creation triggers Cyoda workflows automatically (event driven).