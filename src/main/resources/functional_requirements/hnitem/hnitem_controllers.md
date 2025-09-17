# HN Item Controllers

## Controller Overview
The **HnItemController** provides REST API endpoints for managing Hacker News items. It supports single item operations, bulk operations, Firebase API integration, and hierarchical search capabilities.

## Controller Details
- **Controller Name**: `HnItemController`
- **Base Path**: `/api/hnitem`
- **Package**: `com.java_template.application.controller`

## API Endpoints

### 1. Create Single HN Item
**POST** `/api/hnitem`

Creates a single HN item in the system.

**Request Body:**
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
  "kids": [8952, 9224, 8917, 8884, 8887, 8943, 8869, 8958, 9005, 9671]
}
```

**Response:**
```json
{
  "entity": {
    "id": 8863,
    "type": "story",
    "by": "dhouston",
    "time": 1175714200,
    "title": "My YC app: Dropbox - Throw away your USB drive",
    "url": "http://www.getdropbox.com/u/2/screencast.html",
    "score": 111,
    "descendants": 71,
    "kids": [8952, 9224, 8917, 8884, 8887, 8943, 8869, 8958, 9005, 9671],
    "createdAt": "2025-01-17T10:30:00",
    "updatedAt": "2025-01-17T10:30:00",
    "sourceUrl": "https://hacker-news.firebaseio.com/v0/item/8863.json"
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "initial_state",
    "version": 1
  }
}
```

### 2. Create Multiple HN Items
**POST** `/api/hnitem/bulk`

Creates multiple HN items from an array.

**Request Body:**
```json
[
  {
    "id": 8863,
    "type": "story",
    "by": "dhouston",
    "title": "My YC app: Dropbox - Throw away your USB drive",
    "url": "http://www.getdropbox.com/u/2/screencast.html"
  },
  {
    "id": 2921983,
    "type": "comment",
    "by": "norvig",
    "parent": 2921506,
    "text": "Aw shucks, guys ... you make me blush with your compliments."
  }
]
```

**Response:**
```json
{
  "created": 2,
  "failed": 0,
  "items": [
    {
      "entity": { /* HN Item 1 */ },
      "metadata": { /* Metadata 1 */ }
    },
    {
      "entity": { /* HN Item 2 */ },
      "metadata": { /* Metadata 2 */ }
    }
  ]
}
```

### 3. Bulk Upload from File
**POST** `/api/hnitem/upload`

Uploads HN items from a JSON file.

**Request:** Multipart form with file upload
- **Content-Type**: `multipart/form-data`
- **File Parameter**: `file`
- **File Format**: JSON array of HN items

**Response:**
```json
{
  "created": 150,
  "failed": 5,
  "errors": [
    {
      "item": {"id": 12345},
      "error": "Invalid item type"
    }
  ]
}
```

### 4. Trigger Firebase API Pull
**POST** `/api/hnitem/pull-firebase`

Triggers pulling data from Firebase HN API.

**Request Body:**
```json
{
  "itemIds": [8863, 2921983, 121003],
  "pullType": "specific"
}
```

**Alternative Request (Pull Latest):**
```json
{
  "pullType": "latest",
  "count": 100
}
```

**Response:**
```json
{
  "requested": 3,
  "retrieved": 3,
  "created": 2,
  "updated": 1,
  "failed": 0,
  "items": [
    {
      "entity": { /* HN Item */ },
      "metadata": { /* Metadata */ }
    }
  ]
}
```

### 5. Get HN Item by ID
**GET** `/api/hnitem/{id}`

Retrieves an HN item by its technical UUID.

**Response:**
```json
{
  "entity": {
    "id": 8863,
    "type": "story",
    "by": "dhouston",
    "title": "My YC app: Dropbox - Throw away your USB drive"
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "processed"
  }
}
```

### 6. Get HN Item by Business ID
**GET** `/api/hnitem/business/{hnId}`

Retrieves an HN item by its Hacker News ID.

**Response:** Same as above

### 7. Update HN Item
**PUT** `/api/hnitem/{id}?transition={transitionName}`

Updates an HN item with optional workflow transition.

**Query Parameters:**
- `transition` (optional): Workflow transition name (`validate_item`, `process_item`, etc.)

**Request Body:** Updated HN item data

**Response:** Updated EntityWithMetadata

### 8. Search HN Items
**POST** `/api/hnitem/search`

Searches HN items with various criteria.

**Request Body:**
```json
{
  "type": "story",
  "author": "dhouston",
  "minScore": 50,
  "titleContains": "Dropbox",
  "timeRange": {
    "start": 1175714000,
    "end": 1175714400
  }
}
```

**Response:**
```json
{
  "items": [
    {
      "entity": { /* HN Item */ },
      "metadata": { /* Metadata */ }
    }
  ],
  "total": 1
}
```

### 9. Search with Parent Hierarchy
**POST** `/api/hnitem/search/hierarchy`

Searches HN items including parent-child relationships.

**Request Body:**
```json
{
  "rootItemId": 8863,
  "includeChildren": true,
  "maxDepth": 3,
  "childTypes": ["comment"]
}
```

**Response:**
```json
{
  "rootItem": {
    "entity": { /* Root HN Item */ },
    "metadata": { /* Metadata */ }
  },
  "children": [
    {
      "entity": { /* Child Comment */ },
      "metadata": { /* Metadata */ },
      "depth": 1,
      "children": [
        {
          "entity": { /* Nested Comment */ },
          "metadata": { /* Metadata */ },
          "depth": 2
        }
      ]
    }
  ],
  "totalItems": 25
}
```

### 10. Get All HN Items
**GET** `/api/hnitem`

Retrieves all HN items (paginated).

**Query Parameters:**
- `page` (optional, default: 0): Page number
- `size` (optional, default: 20): Page size
- `type` (optional): Filter by item type

**Response:**
```json
{
  "items": [
    {
      "entity": { /* HN Item */ },
      "metadata": { /* Metadata */ }
    }
  ],
  "page": 0,
  "size": 20,
  "total": 1000
}
```

### 11. Delete HN Item
**DELETE** `/api/hnitem/{id}`

Deletes an HN item by technical UUID.

**Response:** 204 No Content

## Transition Names
The following transition names can be used with update operations:
- `validate_item`: Move from pending_validation to validated
- `process_item`: Move from validated to processed
- `validation_failed`: Move to failed state due to validation issues
- `processing_failed`: Move to failed state due to processing issues

## Error Responses
All endpoints return appropriate HTTP status codes:
- `200 OK`: Successful operation
- `201 Created`: Resource created successfully
- `400 Bad Request`: Invalid request data
- `404 Not Found`: Resource not found
- `500 Internal Server Error`: Server error

Error response format:
```json
{
  "error": "Error message",
  "details": "Detailed error information",
  "timestamp": "2025-01-17T10:30:00"
}
```
