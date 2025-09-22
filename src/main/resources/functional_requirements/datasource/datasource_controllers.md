# DataSource Controllers

## DataSourceController

REST API for managing CSV data sources and triggering data processing workflows.

### Endpoints

#### POST /api/datasources
Create a new data source
- **Request Body**: DataSource entity (without ID)
- **Response**: EntityWithMetadata<DataSource>
- **Transition**: None (creates in initial_state)

**Request Example**:
```json
{
  "url": "https://raw.githubusercontent.com/Cyoda-platform/cyoda-ai/refs/heads/ai-2.x/data/test-inputs/v1/connections/london_houses.csv",
  "name": "London Houses Dataset",
  "description": "Housing data for London area analysis"
}
```

**Response Example**:
```json
{
  "entity": {
    "dataSourceId": "ds-123",
    "url": "https://raw.githubusercontent.com/Cyoda-platform/cyoda-ai/refs/heads/ai-2.x/data/test-inputs/v1/connections/london_houses.csv",
    "name": "London Houses Dataset",
    "description": "Housing data for London area analysis"
  },
  "meta": {
    "uuid": "uuid-123",
    "state": "created"
  }
}
```

#### PUT /api/datasources/{id}/fetch
Trigger data fetch operation
- **Path Parameter**: id (DataSource UUID)
- **Transition**: start_fetch
- **Response**: EntityWithMetadata<DataSource>

#### PUT /api/datasources/{id}/refresh
Refresh data from source
- **Path Parameter**: id (DataSource UUID)  
- **Transition**: refresh_data
- **Response**: EntityWithMetadata<DataSource>

#### GET /api/datasources/{id}
Get data source by ID
- **Path Parameter**: id (DataSource UUID)
- **Response**: EntityWithMetadata<DataSource>
