# Controllers

## 1. HNItemController

### Description
REST controller for managing Hacker News items. Supports creating single items, arrays of items, and retrieving items with search capabilities.

### Endpoints

#### POST /api/hnitems
**Description**: Create a single HN item  
**Transition**: null (entity starts in INITIAL state)

**Request Body**:
```json
{
  "id": 8863,
  "type": "story",
  "by": "dhouston",
  "time": 1175714200,
  "title": "My YC app: Dropbox - Throw away your USB drive",
  "url": "http://www.getdropbox.com/u/2/screencast.html",
  "score": 111,
  "descendants": 71,
  "kids": [8952, 9224, 8917, 8884, 8887]
}
```

**Response**:
```json
{
  "success": true,
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "entity": {
      "id": 8863,
      "type": "story",
      "by": "dhouston",
      "time": 1175714200,
      "title": "My YC app: Dropbox - Throw away your USB drive",
      "url": "http://www.getdropbox.com/u/2/screencast.html",
      "score": 111,
      "descendants": 71,
      "kids": [8952, 9224, 8917, 8884, 8887]
    },
    "meta": {
      "state": "INITIAL",
      "createdAt": "2024-01-15T10:30:00Z"
    }
  }
}
```

#### POST /api/hnitems/batch
**Description**: Create multiple HN items  
**Transition**: null (entities start in INITIAL state)

**Request Body**:
```json
{
  "items": [
    {
      "id": 8863,
      "type": "story",
      "by": "dhouston",
      "time": 1175714200,
      "title": "My YC app: Dropbox - Throw away your USB drive",
      "url": "http://www.getdropbox.com/u/2/screencast.html",
      "score": 111
    },
    {
      "id": 2921983,
      "type": "comment",
      "by": "norvig",
      "time": 1314211127,
      "parent": 2921506,
      "text": "Aw shucks, guys ... you make me blush with your compliments."
    }
  ]
}
```

**Response**:
```json
{
  "success": true,
  "data": {
    "created": 2,
    "failed": 0,
    "items": [
      {
        "uuid": "550e8400-e29b-41d4-a716-446655440001",
        "entity": { "id": 8863, "type": "story", ... },
        "meta": { "state": "INITIAL", "createdAt": "2024-01-15T10:30:00Z" }
      },
      {
        "uuid": "550e8400-e29b-41d4-a716-446655440002",
        "entity": { "id": 2921983, "type": "comment", ... },
        "meta": { "state": "INITIAL", "createdAt": "2024-01-15T10:30:00Z" }
      }
    ]
  }
}
```

#### GET /api/hnitems/{id}
**Description**: Retrieve an HN item by its HN ID

**Response**:
```json
{
  "success": true,
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "entity": {
      "id": 8863,
      "type": "story",
      "by": "dhouston",
      "time": 1175714200,
      "title": "My YC app: Dropbox - Throw away your USB drive",
      "url": "http://www.getdropbox.com/u/2/screencast.html",
      "score": 111,
      "descendants": 71,
      "kids": [8952, 9224, 8917, 8884, 8887]
    },
    "meta": {
      "state": "PROCESSED",
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:35:00Z"
    }
  }
}
```

#### PUT /api/hnitems/{uuid}
**Description**: Update an HN item  
**Parameters**: 
- `transition` (optional): Transition name for state change

**Request Body**:
```json
{
  "score": 150,
  "descendants": 85,
  "transition": "reprocess"
}
```

**Response**: Same as GET response with updated data

#### GET /api/hnitems
**Description**: Search and list HN items with filtering

**Query Parameters**:
- `type`: Filter by item type
- `author`: Filter by author
- `minScore`: Minimum score
- `maxScore`: Maximum score
- `fromTime`: Start time (Unix timestamp)
- `toTime`: End time (Unix timestamp)
- `limit`: Maximum results (default: 50)
- `offset`: Pagination offset (default: 0)

**Response**:
```json
{
  "success": true,
  "data": {
    "items": [...],
    "total": 1250,
    "limit": 50,
    "offset": 0
  }
}
```

#### POST /api/hnitems/pull-firebase
**Description**: Trigger pulling data from Firebase HN API  
**Transition**: null (creates new entities in INITIAL state)

**Request Body**:
```json
{
  "itemIds": [8863, 2921983, 121003],
  "pullType": "specific"
}
```

**Response**:
```json
{
  "success": true,
  "data": {
    "pullId": "pull-550e8400-e29b-41d4-a716-446655440000",
    "itemIds": [8863, 2921983, 121003],
    "status": "initiated"
  }
}
```

## 2. SearchQueryController

### Description
REST controller for managing search queries with support for complex filtering and parent hierarchy joins.

### Endpoints

#### POST /api/search
**Description**: Create and execute a search query  
**Transition**: null (entity starts in INITIAL state)

**Request Body**:
```json
{
  "searchText": "dropbox",
  "itemType": "story",
  "minScore": 50,
  "includeParentHierarchy": true,
  "maxDepth": 3,
  "sortBy": "score",
  "sortOrder": "desc",
  "limit": 20
}
```

**Response**:
```json
{
  "success": true,
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440003",
    "entity": {
      "queryId": "search-1642234567890",
      "searchText": "dropbox",
      "itemType": "story",
      "minScore": 50,
      "includeParentHierarchy": true,
      "maxDepth": 3,
      "sortBy": "score",
      "sortOrder": "desc",
      "limit": 20,
      "resultCount": 15
    },
    "meta": {
      "state": "COMPLETED",
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:30:05Z"
    }
  }
}
```

#### GET /api/search/{uuid}
**Description**: Retrieve search query and its results

**Response**:
```json
{
  "success": true,
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440003",
    "entity": {
      "queryId": "search-1642234567890",
      "searchText": "dropbox",
      "results": [
        {
          "id": 8863,
          "type": "story",
          "title": "My YC app: Dropbox - Throw away your USB drive",
          "score": 111,
          "parentHierarchy": []
        }
      ]
    },
    "meta": {
      "state": "COMPLETED"
    }
  }
}
```

#### PUT /api/search/{uuid}
**Description**: Update search query and re-execute  
**Parameters**: 
- `transition` (optional): "retry" to re-execute failed queries

**Request Body**:
```json
{
  "minScore": 100,
  "transition": "retry"
}
```

#### GET /api/search
**Description**: List search queries with filtering

**Query Parameters**:
- `state`: Filter by query state
- `limit`: Maximum results
- `offset`: Pagination offset

## 3. BulkUploadController

### Description
REST controller for managing bulk uploads of HN items from JSON files.

### Endpoints

#### POST /api/bulk-upload
**Description**: Upload and process a JSON file containing HN items  
**Transition**: null (entity starts in INITIAL state)

**Request**: Multipart form data with file upload

**Response**:
```json
{
  "success": true,
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440004",
    "entity": {
      "uploadId": "upload-1642234567890",
      "fileName": "hn_items_export.json",
      "fileSize": 2048576,
      "totalItems": 1000
    },
    "meta": {
      "state": "INITIAL",
      "createdAt": "2024-01-15T10:30:00Z"
    }
  }
}
```

#### GET /api/bulk-upload/{uuid}
**Description**: Get bulk upload status and progress

**Response**:
```json
{
  "success": true,
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440004",
    "entity": {
      "uploadId": "upload-1642234567890",
      "fileName": "hn_items_export.json",
      "fileSize": 2048576,
      "totalItems": 1000,
      "processedItems": 850,
      "failedItems": 150,
      "errorMessages": [
        "Item 12345: Invalid type specified",
        "Item 67890: Missing required field 'id'"
      ]
    },
    "meta": {
      "state": "PARTIALLY_COMPLETED",
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:45:00Z"
    }
  }
}
```

#### PUT /api/bulk-upload/{uuid}
**Description**: Retry failed items in a bulk upload  
**Parameters**: 
- `transition`: "retry" to retry failed items

**Request Body**:
```json
{
  "transition": "retry"
}
```

#### GET /api/bulk-upload
**Description**: List bulk uploads with filtering

**Query Parameters**:
- `state`: Filter by upload state
- `limit`: Maximum results
- `offset`: Pagination offset

**Response**:
```json
{
  "success": true,
  "data": {
    "uploads": [
      {
        "uuid": "550e8400-e29b-41d4-a716-446655440004",
        "entity": {
          "uploadId": "upload-1642234567890",
          "fileName": "hn_items_export.json",
          "totalItems": 1000,
          "processedItems": 850,
          "failedItems": 150
        },
        "meta": {
          "state": "PARTIALLY_COMPLETED",
          "createdAt": "2024-01-15T10:30:00Z"
        }
      }
    ],
    "total": 1,
    "limit": 50,
    "offset": 0
  }
}
```

## Common Response Patterns

### Success Response
```json
{
  "success": true,
  "data": { ... }
}
```

### Error Response
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid item type specified",
    "details": {
      "field": "type",
      "value": "invalid_type",
      "allowedValues": ["job", "story", "comment", "poll", "pollopt"]
    }
  }
}
```

### Validation Error Response
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Multiple validation errors",
    "details": {
      "errors": [
        {
          "field": "id",
          "message": "ID is required and must be positive"
        },
        {
          "field": "type",
          "message": "Type must be one of: job, story, comment, poll, pollopt"
        }
      ]
    }
  }
}
```
