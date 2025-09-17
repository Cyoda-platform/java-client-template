# DataSource Controller API

## DataSourceController

REST endpoints for managing data source downloads.

### Endpoints

#### POST /api/datasource
Create a new data source and initiate download.

**Request Example:**
```json
{
  "sourceId": "london_houses_001",
  "url": "https://raw.githubusercontent.com/Cyoda-platform/cyoda-ai/refs/heads/ai-2.x/data/test-inputs/v1/connections/london_houses.csv"
}
```

**Response Example:**
```json
{
  "entity": {
    "sourceId": "london_houses_001",
    "url": "https://raw.githubusercontent.com/Cyoda-platform/cyoda-ai/refs/heads/ai-2.x/data/test-inputs/v1/connections/london_houses.csv",
    "fileName": null,
    "downloadedAt": null,
    "fileSize": null
  },
  "meta": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "state": "initial_state",
    "createdAt": "2025-09-17T10:00:00Z"
  }
}
```

#### PUT /api/datasource/{uuid}/transition
Trigger state transition for data source.

**Request Example:**
```json
{
  "transitionName": "begin_download"
}
```

**Response Example:**
```json
{
  "entity": {
    "sourceId": "london_houses_001",
    "url": "https://raw.githubusercontent.com/Cyoda-platform/cyoda-ai/refs/heads/ai-2.x/data/test-inputs/v1/connections/london_houses.csv",
    "fileName": "london_houses_20250917.csv",
    "downloadedAt": "2025-09-17T10:05:00Z",
    "fileSize": 1024000
  },
  "meta": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "state": "download_complete",
    "updatedAt": "2025-09-17T10:05:00Z"
  }
}
```

#### GET /api/datasource/{uuid}
Retrieve data source by UUID.

**Response Example:**
```json
{
  "entity": {
    "sourceId": "london_houses_001",
    "url": "https://raw.githubusercontent.com/Cyoda-platform/cyoda-ai/refs/heads/ai-2.x/data/test-inputs/v1/connections/london_houses.csv",
    "fileName": "london_houses_20250917.csv",
    "downloadedAt": "2025-09-17T10:05:00Z",
    "fileSize": 1024000
  },
  "meta": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "state": "download_complete",
    "createdAt": "2025-09-17T10:00:00Z",
    "updatedAt": "2025-09-17T10:05:00Z"
  }
}
```
