# Report Controllers

## ReportController

### Endpoints

#### POST /api/reports
Generate a new report.

**Request Example:**
```json
{
  "reportName": "Monthly Submission Status Report",
  "reportType": "SUBMISSION_STATUS",
  "generatedBy": "admin@example.com",
  "parameters": "{\"includeCharts\": true, \"groupBy\": \"submissionType\"}",
  "dataRange": "2024-01-01,2024-01-31",
  "format": "PDF"
}
```

**Response Example:**
```json
{
  "entity": {
    "reportName": "Monthly Submission Status Report",
    "reportType": "SUBMISSION_STATUS",
    "generatedBy": "admin@example.com",
    "generationDate": "2024-01-15T12:00:00Z",
    "parameters": "{\"includeCharts\": true, \"groupBy\": \"submissionType\"}",
    "dataRange": "2024-01-01,2024-01-31",
    "format": "PDF",
    "filePath": null
  },
  "meta": {
    "uuid": "012e3456-e89b-12d3-a456-426614174003",
    "state": "generating",
    "version": 1
  }
}
```

#### PUT /api/reports/{uuid}/complete
Complete report generation (transition: complete_generation).

**Request Example:**
```json
{
  "transitionName": "complete_generation",
  "filePath": "/reports/monthly_status_2024_01.pdf"
}
```

#### GET /api/reports/{uuid}
Get report by UUID.

#### GET /api/reports
List reports with filtering options.

#### GET /api/reports/{uuid}/download
Download generated report file.
