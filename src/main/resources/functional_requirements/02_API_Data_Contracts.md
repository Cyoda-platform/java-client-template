# API Data Contracts (v0.1)
*Status:* Draft — RESTful contracts for core capabilities. JSON over HTTPS, UTF-8.  
*Auth:* `Authorization: Bearer <token>` (OIDC access token).  
*Headers (recommended):* `X-Request-Id`, `Idempotency-Key` (for POST).  
*Dates:* RFC3339 `date-time` or `date`.  
*Pagination:* cursor-based (`limit`, `cursor`).

---

## Common Error Model
**HTTP 4xx/5xx**  
```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "One or more fields are invalid.",
    "details": [{"field": "title", "message": "Required"}],
    "correlation_id": "5a1b2c3d-..."
  }
}
```

**Standard Codes**
- `VALIDATION_FAILED`, `NOT_FOUND`, `CONFLICT`, `FORBIDDEN`, `UNAUTHORIZED`, `RATE_LIMITED`, `INTERNAL_ERROR`

---

## Submissions
### Create Submission
**POST** `/v1/submissions`  
Request (JSON):
```json
{
  "title": "Phase II Study of XYZ in ABC",
  "study_type": "clinical_trial",
  "protocol_id": "PROT-2025-013",
  "phase": "II",
  "therapeutic_area": "Oncology",
  "sponsor_name": "Contoso Pharma",
  "principal_investigator": "uuid-of-pi",
  "sites": ["uuid-site-1", "uuid-site-2"],
  "start_date": "2025-10-01",
  "end_date": "2026-10-01",
  "funding_source": "Internal",
  "risk_category": "moderate",
  "keywords": ["xyz","abc"],
  "declarations": { "conflicts_of_interest": false, "attestations": ["..."] }
}
```
Response **201**:
```json
{
  "submission_id": "uuid",
  "status": "draft",
  "created_at": "2025-09-24T10:12:22Z"
}
```

### List Submissions
**GET** `/v1/submissions?status=review&study_type=clinical_trial&limit=50&cursor=...`  
Response **200**:
```json
{
  "items": [{"submission_id":"uuid","title":"...","status":"review","created_at":"..."}],
  "next_cursor": "eyJwYWdlIjoyfQ=="
}
```

### Get Submission
**GET** `/v1/submissions/{submission_id}`

### Update Submission (partial)
**PATCH** `/v1/submissions/{submission_id}`  
Request: JSON Patch-like partial (fields from Submission schema).

### Submit for Review (transition from draft→submitted)
**POST** `/v1/submissions/{submission_id}:submit`  
Response **200**: `{ "status": "submitted" }`

### Timeline & Activity
**GET** `/v1/submissions/{submission_id}/timeline`  
Response includes chronological events (created, routed, RFI created, etc.).

---

## Documents
### Upload Document (standalone)
**POST** `/v1/documents` (multipart/form-data)  
Parts: `file` (binary), `metadata` (JSON Document Metadata).  
Response **201**: `{ "document_id": "uuid", "version_id": "uuid" }`

### Attach to Submission
**POST** `/v1/submissions/{submission_id}/documents`  
Body:
```json
{ "document_id": "uuid", "type": "protocol" }
```

### New Version
**POST** `/v1/documents/{document_id}/versions` (multipart/form-data: `file`, `metadata`)  
Response: `{ "version_id": "uuid", "version_label": "v1.1" }`

### Download
**GET** `/v1/documents/{document_id}/download?version=latest`  
Returns file stream.

### Audit Trail
**GET** `/v1/audit/events?entity_type=document&entity_id={document_id}&limit=100`

---

## Workflow & Tasks
### Get Workflow Instance
**GET** `/v1/{entity_type}/{entity_id}/workflow`  
`entity_type`: `submissions` or `studies`

### Transition
**POST** `/v1/{entity_type}/{entity_id}/workflow:transition`  
Request:
```json
{ "transition_key": "scientific_approve", "comment": "All good." }
```

### Tasks
**GET** `/v1/tasks?assignee=me&status=open&limit=50`  
**PATCH** `/v1/tasks/{task_id}` (e.g., `{ "status": "done" }`)

### Workflow Templates (Admin)
**GET** `/v1/workflow/templates`  
**POST** `/v1/workflow/templates` (create/update template JSON)

---

## Studies, Sites, Subjects
### Create Study (post-approval)
**POST** `/v1/studies`  
Request:
```json
{
  "source_submission_id": "uuid",
  "title": "XYZ",
  "protocol_id": "PROT-2025-013",
  "phase": "II",
  "therapeutic_area": "Oncology",
  "arms": [{"arm_id":"uuid","name":"A"}],
  "visit_schedule": [{"visit_code":"V1","name":"Baseline","window_minus_days":2,"window_plus_days":3,"procedures":["LAB","ECG"]}]
}
```
Response **201**: `{ "study_id": "uuid" }`

### Sites
**POST** `/v1/studies/{study_id}/sites`  
**GET** `/v1/studies/{study_id}/sites`

### Subjects
**POST** `/v1/studies/{study_id}/subjects`  
Request:
```json
{
  "screening_id": "SCR-023",
  "enrollment_date": "2025-09-10",
  "demographics": { "age": 57, "sex_at_birth": "female" },
  "consent_status": "consented",
  "consent_date": "2025-09-03"
}
```
Response: `{ "subject_id": "uuid" }`

**GET** `/v1/studies/{study_id}/subjects?status=enrolled`

---

## Visits & CRF
### Create/Plan Visit
**POST** `/v1/subjects/{subject_id}/visits`  
Request:
```json
{ "visit_code": "V2", "planned_date": "2025-10-10" }
```

### Complete Visit & Enter CRF
**POST** `/v1/visits/{visit_id}:complete`  
Request:
```json
{
  "actual_date": "2025-10-11",
  "crf_data": {
    "labs": { "hemoglobin": 12.8, "wbc": 6.1 },
    "vitals": { "bp_systolic": 120, "bp_diastolic": 78 }
  },
  "deviations": [{"code": "LATE", "description": "Visit +1 day", "severity": "minor"}]
}
```

### Lock Visit Data
**POST** `/v1/visits/{visit_id}:lock` → `{ "locked": true }`

---

## Adverse Events
### Create AE
**POST** `/v1/subjects/{subject_id}/adverse-events`  
Request:
```json
{
  "onset_date": "2025-09-11",
  "seriousness": "serious",
  "severity": "moderate",
  "relatedness": "possible",
  "outcome": "recovering",
  "action_taken": "dose reduced",
  "narrative": "Subject experienced ...",
  "is_SAE": true,
  "follow_up_due_date": "2025-09-18"
}
```
Response: `{ "ae_id": "uuid" }`

### Update / Follow-up
**PATCH** `/v1/adverse-events/{ae_id}`

### List AEs for Study or Subject
**GET** `/v1/studies/{study_id}/adverse-events?seriousness=serious`

---

## Medication / IP Accountability
### Lots
**POST** `/v1/studies/{study_id}/inventory/lots`  
**GET** `/v1/studies/{study_id}/inventory/lots`

### Dispense
**POST** `/v1/studies/{study_id}/inventory/dispenses`  
Request:
```json
{ "subject_id": "uuid", "lot_id": "uuid", "date": "2025-09-11", "quantity": 2 }
```

### Return
**POST** `/v1/studies/{study_id}/inventory/returns`  
Request:
```json
{ "subject_id": "uuid", "lot_id": "uuid", "date": "2025-09-20", "quantity": 1, "reason": "unused" }
```

---

## RFIs & Messaging
### Create RFI
**POST** `/v1/{parent_type}/{parent_id}/rfis`  
Request:
```json
{
  "title": "Clarify Dose Titration Schedule",
  "message": "Please align with protocol v1.1",
  "requested_documents": ["dose_schedule"],
  "due_at": "2025-09-18T17:00:00Z",
  "participants": ["uuid-user-1","uuid-user-2"]
}
```
Response: `{ "rfi_id": "uuid" }`

### Reply in Thread
**POST** `/v1/rfis/{rfi_id}/messages`  
Body: `{ "body": "Uploaded updated schedule.", "attachments": ["doc-uuid"] }`

---

## Reporting
### Run Ad-hoc Query
**POST** `/v1/reports:run`  
Request:
```json
{
  "dataset": "submissions",
  "metrics": [{"name": "median_cycle_time_days"}],
  "dimensions": ["study_type","status"],
  "filters": {"created_at_gte": "2025-09-01"},
  "limit": 1000
}
```
Response:
```json
{
  "columns": ["study_type","status","median_cycle_time_days"],
  "rows": [["clinical_trial","review",12.3]]
}
```

### Save Definition
**POST** `/v1/reports` → `{ "report_id": "uuid" }`  
**POST** `/v1/report-schedules` → cron & recipients

---

## Real-Time Events
### Server-Sent Events (SSE)
**GET** `/v1/events/stream` → `text/event-stream`  
Event example (data line):
```json
{
  "event_id": "uuid",
  "type": "StatusChanged",
  "entity_type": "submission",
  "entity_id": "uuid",
  "occurred_at": "2025-09-05T12:00:00Z",
  "payload": { "from": "review", "to": "decision" }
}
```

### Webhooks
**POST** `/v1/webhooks/subscriptions`  
Body:
```json
{ "url": "https://listener.example.com/webhook", "events": ["StatusChanged","AEFlagged"] }
```
Headers on delivery: `X-Signature` (HMAC-SHA256), `X-Event-Type`.

---

## Audit
**GET** `/v1/audit/events?entity_type=submission&entity_id={uuid}&limit=100&cursor=...`

---

## Admin
**GET** `/v1/roles` / **POST** `/v1/roles`  
**GET** `/v1/users` / **POST** `/v1/users`  
**GET** `/v1/taxonomies/{type}` (e.g., `document_types`, `therapeutic_areas`)  
**GET/POST** `/v1/workflow/templates`

---

## Schema Definitions (excerpts)
### Submission
```json
{
  "submission_id": "uuid",
  "title": "string",
  "study_type": "clinical_trial",
  "protocol_id": "string",
  "phase": "II",
  "therapeutic_area": "string",
  "sponsor_name": "string",
  "principal_investigator": "uuid",
  "sites": ["uuid"],
  "start_date": "date",
  "end_date": "date",
  "funding_source": "string",
  "risk_category": "moderate",
  "keywords": ["string"],
  "declarations": { "conflicts_of_interest": false, "attestations": ["string"] },
  "status": "review",
  "created_at": "date-time",
  "updated_at": "date-time"
}
```

### Document (metadata)
```json
{
  "document_id": "uuid",
  "name": "string",
  "type": "protocol",
  "version_label": "v1.0",
  "status": "final",
  "effective_date": "date",
  "expiry_date": "date",
  "checksum_sha256": "string",
  "file_size_bytes": 12345,
  "content_type": "application/pdf",
  "classifications": ["CONFIDENTIAL"],
  "retention_category": "regulatory",
  "uploaded_by": "uuid",
  "uploaded_at": "date-time"
}
```

### AE
```json
{
  "ae_id": "uuid",
  "subject_id": "uuid",
  "onset_date": "date",
  "seriousness": "serious",
  "severity": "moderate",
  "relatedness": "possible",
  "outcome": "recovering",
  "action_taken": "string",
  "narrative": "string",
  "is_SAE": true,
  "follow_up_due_date": "date"
}
```

---

## Conventions
- **Idempotency:** Use `Idempotency-Key` on POSTs that create resources.  
- **Filtering:** Common operators via query params (`*_eq`, `*_in`, `*_gte`, `*_lte`).  
- **Security:** RBAC enforced; field-level redaction for PHI where necessary.  
- **Validation:** Descriptive errors with field paths and machine-readable codes.
