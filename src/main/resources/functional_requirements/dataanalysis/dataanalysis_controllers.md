# DataAnalysis Controller API

## DataAnalysisController

REST endpoints for managing data analysis operations.

### Endpoints

#### POST /api/dataanalysis
Create a new data analysis entity.

**Request Example:**
```json
{
  "analysisId": "analysis_001",
  "dataSourceId": "london_houses_001",
  "analysisType": "housing_market_summary"
}
```

**Response Example:**
```json
{
  "entity": {
    "analysisId": "analysis_001",
    "dataSourceId": "london_houses_001",
    "analysisType": "housing_market_summary",
    "reportData": null,
    "analysisStartedAt": null,
    "analysisCompletedAt": null
  },
  "meta": {
    "uuid": "660e8400-e29b-41d4-a716-446655440001",
    "state": "initial_state",
    "createdAt": "2025-09-17T10:10:00Z"
  }
}
```

#### PUT /api/dataanalysis/{uuid}/transition
Trigger state transition for data analysis.

**Request Example:**
```json
{
  "transitionName": "begin_analysis"
}
```

**Response Example:**
```json
{
  "entity": {
    "analysisId": "analysis_001",
    "dataSourceId": "london_houses_001",
    "analysisType": "housing_market_summary",
    "reportData": {
      "totalRecords": 1500,
      "averagePrice": 650000,
      "priceRange": {
        "min": 200000,
        "max": 2500000
      },
      "topAreas": [
        {"area": "Westminster", "count": 150},
        {"area": "Kensington", "count": 120}
      ],
      "priceByBedrooms": [
        {"bedrooms": 1, "averagePrice": 450000},
        {"bedrooms": 2, "averagePrice": 650000}
      ]
    },
    "analysisStartedAt": "2025-09-17T10:15:00Z",
    "analysisCompletedAt": "2025-09-17T10:18:00Z"
  },
  "meta": {
    "uuid": "660e8400-e29b-41d4-a716-446655440001",
    "state": "analysis_complete",
    "updatedAt": "2025-09-17T10:18:00Z"
  }
}
```

#### GET /api/dataanalysis/{uuid}
Retrieve data analysis by UUID.

**Response Example:**
```json
{
  "entity": {
    "analysisId": "analysis_001",
    "dataSourceId": "london_houses_001",
    "analysisType": "housing_market_summary",
    "reportData": {
      "totalRecords": 1500,
      "averagePrice": 650000
    },
    "analysisStartedAt": "2025-09-17T10:15:00Z",
    "analysisCompletedAt": "2025-09-17T10:18:00Z"
  },
  "meta": {
    "uuid": "660e8400-e29b-41d4-a716-446655440001",
    "state": "analysis_complete"
  }
}
```
