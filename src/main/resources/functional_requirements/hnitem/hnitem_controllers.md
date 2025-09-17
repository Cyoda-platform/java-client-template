# HNItem Controllers

## Overview
The HNItemController provides REST API endpoints for managing Hacker News items. It supports Firebase API integration, single/bulk item operations, and search functionality with hierarchy traversal.

## Controller: HNItemController

### Base Path: `/api/v1/hnitems`

## Endpoints

### 1. Trigger Firebase API Pull
**Endpoint**: `POST /api/v1/hnitems/pull-firebase`
**Description**: Trigger pulling data from Firebase HN API
**Method**: POST
**Transition**: `auto_ingest` (creates new HNItem entities)

#### Request
```json
{
  "itemTypes": ["story", "comment"],
  "maxItems": 100,
  "startFromId": 8863,
  "includeDescendants": true
}
```

#### Response
```json
{
  "success": true,
  "message": "Firebase pull initiated",
  "pullJobId": "pull-job-12345",
  "estimatedItems": 100,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 2. Create Single HN Item
**Endpoint**: `POST /api/v1/hnitems`
**Description**: Create a single HN item
**Method**: POST
**Transition**: `auto_ingest`

#### Request
```json
{
  "hnId": 8863,
  "type": "story",
  "by": "dhouston",
  "time": 1175714200,
  "title": "My YC app: Dropbox - Throw away your USB drive",
  "url": "http://www.getdropbox.com/u/2/screencast.html",
  "score": 111,
  "descendants": 71,
  "kids": [8952, 9224, 8917],
  "sourceType": "SINGLE_POST"
}
```

#### Response
```json
{
  "success": true,
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "entity": {
      "hnId": 8863,
      "type": "story",
      "by": "dhouston",
      "time": 1175714200,
      "title": "My YC app: Dropbox - Throw away your USB drive",
      "url": "http://www.getdropbox.com/u/2/screencast.html",
      "score": 111,
      "descendants": 71,
      "kids": [8952, 9224, 8917],
      "sourceType": "SINGLE_POST",
      "ingestedAt": "2024-01-15T10:30:00Z"
    },
    "meta": {
      "state": "ingested",
      "version": 1,
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:30:00Z"
    }
  }
}
```

### 3. Create Multiple HN Items
**Endpoint**: `POST /api/v1/hnitems/batch`
**Description**: Create multiple HN items from array
**Method**: POST
**Transition**: `auto_ingest` (for each item)

#### Request
```json
{
  "items": [
    {
      "hnId": 8863,
      "type": "story",
      "by": "dhouston",
      "title": "My YC app: Dropbox",
      "url": "http://www.getdropbox.com/u/2/screencast.html",
      "score": 111
    },
    {
      "hnId": 2921983,
      "type": "comment",
      "by": "norvig",
      "parent": 2921506,
      "text": "Aw shucks, guys ... you make me blush with your compliments.",
      "time": 1314211127
    }
  ],
  "sourceType": "ARRAY_POST"
}
```

#### Response
```json
{
  "success": true,
  "data": {
    "totalItems": 2,
    "createdItems": 2,
    "failedItems": 0,
    "items": [
      {
        "uuid": "550e8400-e29b-41d4-a716-446655440000",
        "hnId": 8863,
        "status": "created"
      },
      {
        "uuid": "550e8400-e29b-41d4-a716-446655440001",
        "hnId": 2921983,
        "status": "created"
      }
    ]
  }
}
```

### 4. Bulk Upload from File
**Endpoint**: `POST /api/v1/hnitems/upload`
**Description**: Upload HN items from JSON file
**Method**: POST (multipart/form-data)
**Transition**: Creates UploadBatch entity

#### Request
```
Content-Type: multipart/form-data

file: [JSON file containing array of HN items]
```

#### Response
```json
{
  "success": true,
  "data": {
    "batchId": "batch-12345",
    "fileName": "hn_items.json",
    "fileSize": 1048576,
    "totalItemsInFile": 500,
    "uploadedAt": "2024-01-15T10:30:00Z",
    "status": "uploaded"
  }
}
```

### 5. Get HN Item by ID
**Endpoint**: `GET /api/v1/hnitems/{uuid}`
**Description**: Retrieve HN item by UUID
**Method**: GET

#### Response
```json
{
  "success": true,
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "entity": {
      "hnId": 8863,
      "type": "story",
      "by": "dhouston",
      "title": "My YC app: Dropbox - Throw away your USB drive",
      "url": "http://www.getdropbox.com/u/2/screencast.html",
      "score": 111,
      "descendants": 71,
      "kids": [8952, 9224, 8917],
      "sourceType": "SINGLE_POST",
      "ingestedAt": "2024-01-15T10:30:00Z",
      "lastUpdated": "2024-01-15T10:35:00Z"
    },
    "meta": {
      "state": "completed",
      "version": 1,
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:35:00Z"
    }
  }
}
```

### 6. Get HN Item by HN ID
**Endpoint**: `GET /api/v1/hnitems/hn/{hnId}`
**Description**: Retrieve HN item by Firebase HN ID
**Method**: GET

#### Response
```json
{
  "success": true,
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "entity": {
      "hnId": 8863,
      "type": "story",
      "by": "dhouston",
      "title": "My YC app: Dropbox - Throw away your USB drive"
    },
    "meta": {
      "state": "completed"
    }
  }
}
```

### 7. Update HN Item
**Endpoint**: `PUT /api/v1/hnitems/{uuid}`
**Description**: Update HN item with optional state transition
**Method**: PUT
**Transition**: Optional transition name in request

#### Request
```json
{
  "entity": {
    "score": 125,
    "descendants": 75,
    "lastUpdated": "2024-01-15T11:00:00Z"
  },
  "transition": null
}
```

#### Response
```json
{
  "success": true,
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "entity": {
      "hnId": 8863,
      "score": 125,
      "descendants": 75,
      "lastUpdated": "2024-01-15T11:00:00Z"
    },
    "meta": {
      "state": "completed",
      "version": 2,
      "updatedAt": "2024-01-15T11:00:00Z"
    }
  }
}
```

### 8. Trigger State Transition
**Endpoint**: `POST /api/v1/hnitems/{uuid}/transition`
**Description**: Trigger specific workflow transition
**Method**: POST
**Transition**: Specified in request body

#### Request
```json
{
  "transition": "retry_processing"
}
```

#### Response
```json
{
  "success": true,
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "previousState": "failed",
    "newState": "ingested",
    "transition": "retry_processing",
    "timestamp": "2024-01-15T11:00:00Z"
  }
}
```

### 9. Search HN Items
**Endpoint**: `GET /api/v1/hnitems/search`
**Description**: Search HN items with filters and hierarchy
**Method**: GET
**Transition**: Creates SearchQuery entity

#### Request Parameters
```
keywords=dropbox
author=dhouston
type=story,comment
minScore=50
maxScore=1000
fromTime=1175714200
toTime=1175800600
parentId=8863
includeDescendants=true
maxDepth=3
sortBy=score
sortOrder=DESC
limit=20
offset=0
```

#### Response
```json
{
  "success": true,
  "data": {
    "queryId": "search-12345",
    "results": [
      {
        "uuid": "550e8400-e29b-41d4-a716-446655440000",
        "hnId": 8863,
        "type": "story",
        "title": "My YC app: Dropbox - Throw away your USB drive",
        "by": "dhouston",
        "score": 111,
        "time": 1175714200
      }
    ],
    "pagination": {
      "total": 1,
      "limit": 20,
      "offset": 0,
      "hasMore": false
    },
    "executionTime": 150,
    "cached": false
  }
}
```

### 10. Get Item Hierarchy
**Endpoint**: `GET /api/v1/hnitems/{uuid}/hierarchy`
**Description**: Get parent-child hierarchy for an item
**Method**: GET

#### Request Parameters
```
direction=descendants  // or "ancestors"
maxDepth=5
includeRoot=true
```

#### Response
```json
{
  "success": true,
  "data": {
    "root": {
      "uuid": "550e8400-e29b-41d4-a716-446655440000",
      "hnId": 8863,
      "type": "story",
      "title": "My YC app: Dropbox"
    },
    "hierarchy": [
      {
        "uuid": "550e8400-e29b-41d4-a716-446655440001",
        "hnId": 8952,
        "type": "comment",
        "parent": 8863,
        "depth": 1,
        "children": [
          {
            "uuid": "550e8400-e29b-41d4-a716-446655440002",
            "hnId": 9001,
            "type": "comment",
            "parent": 8952,
            "depth": 2
          }
        ]
      }
    ],
    "totalItems": 3,
    "maxDepthReached": 2
  }
}
```

### 11. List HN Items
**Endpoint**: `GET /api/v1/hnitems`
**Description**: List HN items with pagination and filters
**Method**: GET

#### Request Parameters
```
type=story
state=completed
sourceType=FIREBASE_API
limit=50
offset=0
sortBy=time
sortOrder=DESC
```

#### Response
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "uuid": "550e8400-e29b-41d4-a716-446655440000",
        "hnId": 8863,
        "type": "story",
        "title": "My YC app: Dropbox",
        "by": "dhouston",
        "score": 111,
        "state": "completed"
      }
    ],
    "pagination": {
      "total": 1,
      "limit": 50,
      "offset": 0,
      "hasMore": false
    }
  }
}
```

## Error Responses

### Validation Error
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid HN item data",
    "details": {
      "field": "hnId",
      "reason": "must be positive integer"
    }
  }
}
```

### Not Found Error
```json
{
  "success": false,
  "error": {
    "code": "NOT_FOUND",
    "message": "HN item not found",
    "details": {
      "uuid": "550e8400-e29b-41d4-a716-446655440000"
    }
  }
}
```

### Duplicate Error
```json
{
  "success": false,
  "error": {
    "code": "DUPLICATE_ITEM",
    "message": "HN item with this ID already exists",
    "details": {
      "hnId": 8863,
      "existingUuid": "550e8400-e29b-41d4-a716-446655440000"
    }
  }
}
```
