# HackerNewsItem Controllers Requirements

## Overview
The HackerNewsItemController provides REST API endpoints for managing Hacker News items. It supports CRUD operations, bulk uploads, Firebase API integration, and advanced search capabilities including parent hierarchy joins.

## Controller: HackerNewsItemController

**Base Path**: `/api/v1/hackernews`
**Entity**: HackerNewsItem
**Purpose**: Complete REST API for Hacker News item management

## Endpoints

### 1. Create Single Item

**Endpoint**: `POST /api/v1/hackernews/items`
**Purpose**: Create a single HackerNewsItem
**Transition**: null (triggers workflow from initial_state)

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

**Response** (201 Created):
```json
{
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
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "state": "pending_validation",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:00Z"
  }
}
```

### 2. Create Multiple Items

**Endpoint**: `POST /api/v1/hackernews/items/batch`
**Purpose**: Create multiple HackerNewsItems in a single request
**Transition**: null (triggers workflow for each item)

**Request Body**:
```json
{
  "items": [
    {
      "id": 8863,
      "type": "story",
      "title": "My YC app: Dropbox"
    },
    {
      "id": 2921983,
      "type": "comment",
      "parent": 2921506,
      "text": "Great comment here"
    }
  ]
}
```

**Response** (201 Created):
```json
{
  "created": 2,
  "failed": 0,
  "results": [
    {
      "uuid": "550e8400-e29b-41d4-a716-446655440000",
      "entity": {
        "id": 8863,
        "type": "story",
        "title": "My YC app: Dropbox"
      },
      "meta": {
        "uuid": "550e8400-e29b-41d4-a716-446655440000",
        "state": "pending_validation"
      }
    },
    {
      "uuid": "550e8400-e29b-41d4-a716-446655440001",
      "entity": {
        "id": 2921983,
        "type": "comment",
        "parent": 2921506,
        "text": "Great comment here"
      },
      "meta": {
        "uuid": "550e8400-e29b-41d4-a716-446655440001",
        "state": "pending_validation"
      }
    }
  ]
}
```

### 3. Bulk Upload from JSON File

**Endpoint**: `POST /api/v1/hackernews/items/bulk-upload`
**Purpose**: Upload HackerNewsItems from a JSON file
**Content-Type**: `multipart/form-data`
**Transition**: null (triggers bulk processing)

**Request**:
- Form parameter: `file` (JSON file containing array of items)

**Response** (202 Accepted):
```json
{
  "message": "Bulk upload initiated",
  "jobId": "bulk-upload-123456",
  "status": "processing",
  "estimatedItems": 1500
}
```

### 4. Get Item by ID

**Endpoint**: `GET /api/v1/hackernews/items/{id}`
**Purpose**: Retrieve a single HackerNewsItem by its HN ID

**Response** (200 OK):
```json
{
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
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "state": "stored",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:00Z"
  }
}
```

### 5. Update Item

**Endpoint**: `PUT /api/v1/hackernews/items/{uuid}`
**Purpose**: Update an existing HackerNewsItem
**Transition**: validate_item (if moving to validation)

**Request Body**:
```json
{
  "entity": {
    "id": 8863,
    "type": "story",
    "title": "Updated title",
    "score": 150
  },
  "transition": "validate_item"
}
```

**Response** (200 OK):
```json
{
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "entity": {
    "id": 8863,
    "type": "story",
    "title": "Updated title",
    "score": 150
  },
  "meta": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "state": "validated",
    "updatedAt": "2024-01-15T11:00:00Z"
  }
}
```

### 6. Search Items

**Endpoint**: `GET /api/v1/hackernews/items/search`
**Purpose**: Search HackerNewsItems with various filters and hierarchy joins

**Query Parameters**:
- `q` (string): Full-text search in title and text
- `type` (string): Filter by item type
- `author` (string): Filter by author (by field)
- `minScore` (integer): Minimum score filter
- `maxScore` (integer): Maximum score filter
- `hasChildren` (boolean): Filter items with/without children
- `parentId` (long): Filter by parent ID
- `includeHierarchy` (boolean): Include parent hierarchy in results
- `page` (integer): Page number (default: 0)
- `size` (integer): Page size (default: 20)

**Example Request**: `GET /api/v1/hackernews/items/search?q=dropbox&type=story&minScore=50&includeHierarchy=true`

**Response** (200 OK):
```json
{
  "content": [
    {
      "uuid": "550e8400-e29b-41d4-a716-446655440000",
      "entity": {
        "id": 8863,
        "type": "story",
        "title": "My YC app: Dropbox - Throw away your USB drive",
        "score": 111
      },
      "meta": {
        "state": "stored"
      },
      "hierarchy": {
        "children": [
          {
            "id": 8952,
            "type": "comment",
            "parent": 8863,
            "text": "This is amazing!"
          }
        ]
      }
    }
  ],
  "pageable": {
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### 7. Get Item Hierarchy

**Endpoint**: `GET /api/v1/hackernews/items/{id}/hierarchy`
**Purpose**: Get complete comment hierarchy for an item

**Query Parameters**:
- `depth` (integer): Maximum depth to traverse (default: unlimited)
- `includeDeleted` (boolean): Include deleted comments (default: false)

**Response** (200 OK):
```json
{
  "root": {
    "id": 8863,
    "type": "story",
    "title": "My YC app: Dropbox"
  },
  "children": [
    {
      "id": 8952,
      "type": "comment",
      "parent": 8863,
      "text": "Great idea!",
      "children": [
        {
          "id": 8953,
          "type": "comment",
          "parent": 8952,
          "text": "I agree!"
        }
      ]
    }
  ],
  "totalComments": 71
}
```

### 8. Fetch from Firebase HN API

**Endpoint**: `POST /api/v1/hackernews/fetch/{endpoint}`
**Purpose**: Trigger fetching items from Firebase HN API
**Transition**: null (triggers fetch processing)

**Path Parameters**:
- `endpoint`: One of "topstories", "newstories", "beststories", "askstories", "showstories", "jobstories"

**Request Body**:
```json
{
  "maxItems": 100,
  "enrichItems": true
}
```

**Response** (202 Accepted):
```json
{
  "message": "Firebase fetch initiated",
  "endpoint": "topstories",
  "maxItems": 100,
  "jobId": "fetch-topstories-123456",
  "status": "processing"
}
```

### 9. Trigger Item Enrichment

**Endpoint**: `POST /api/v1/hackernews/items/{uuid}/enrich`
**Purpose**: Trigger enrichment for a specific item
**Transition**: enrich_item

**Response** (200 OK):
```json
{
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Enrichment triggered",
  "previousState": "validated",
  "newState": "enriching"
}
```

### 10. Get Processing Status

**Endpoint**: `GET /api/v1/hackernews/jobs/{jobId}`
**Purpose**: Check status of bulk operations

**Response** (200 OK):
```json
{
  "jobId": "bulk-upload-123456",
  "status": "completed",
  "startTime": "2024-01-15T10:30:00Z",
  "endTime": "2024-01-15T10:45:00Z",
  "totalItems": 1500,
  "processedItems": 1500,
  "successfulItems": 1485,
  "failedItems": 15,
  "errors": [
    {
      "itemId": 12345,
      "error": "Validation failed: Invalid type"
    }
  ]
}
```
