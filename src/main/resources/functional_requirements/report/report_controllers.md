# Report Controllers

## ReportController

REST API for managing analysis reports and email distribution.

### Endpoints

#### POST /api/reports
Create a new report
- **Request Body**: Report entity (without ID)
- **Response**: EntityWithMetadata<Report>
- **Transition**: None (creates in initial_state)

**Request Example**:
```json
{
  "dataSourceId": "ds-123",
  "title": "London Houses Analysis Report",
  "reportFormat": "HTML"
}
```

**Response Example**:
```json
{
  "entity": {
    "reportId": "rpt-456",
    "dataSourceId": "ds-123", 
    "title": "London Houses Analysis Report",
    "reportFormat": "HTML"
  },
  "meta": {
    "uuid": "uuid-456",
    "state": "created"
  }
}
```

#### PUT /api/reports/{id}/generate
Generate report content
- **Path Parameter**: id (Report UUID)
- **Transition**: start_generation
- **Response**: EntityWithMetadata<Report>

#### PUT /api/reports/{id}/send
Send report to subscribers
- **Path Parameter**: id (Report UUID)
- **Transition**: send_to_subscribers  
- **Response**: EntityWithMetadata<Report>

#### GET /api/reports/{id}
Get report by ID
- **Path Parameter**: id (Report UUID)
- **Response**: EntityWithMetadata<Report>
