# HNItem Controller Specification

## Overview
The HNItemController provides REST API endpoints for managing Hacker News items, including CRUD operations, search functionality, and Firebase API integration.

## Controller Details
- **Controller Name**: HNItemController
- **Base Path**: `/api/hnitem`
- **Package**: `com.java_template.application.controller`

## Endpoints

### 1. Create Single HN Item
**POST** `/api/hnitem`

Creates a single HN item.

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
  "kids": [8952, 9224, 8917, 8884, 8887],
  "sourceType": "SINGLE_POST"
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
    "kids": [8952, 9224, 8917, 8884, 8887],
    "sourceType": "SINGLE_POST",
    "createdAt": "2025-09-17T10:30:00",
    "updatedAt": "2025-09-17T10:30:00"
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "created",
    "version": 1,
    "createdAt": "2025-09-17T10:30:00",
    "updatedAt": "2025-09-17T10:30:00"
  }
}
```

### 2. Create Multiple HN Items
**POST** `/api/hnitem/batch`

Creates multiple HN items from an array.

**Request Body:**
```json
[
  {
    "id": 8863,
    "type": "story",
    "by": "dhouston",
    "title": "My YC app: Dropbox",
    "sourceType": "ARRAY_POST"
  },
  {
    "id": 8864,
    "type": "comment",
    "by": "user123",
    "parent": 8863,
    "text": "Great idea!",
    "sourceType": "ARRAY_POST"
  }
]
```

**Response:**
```json
{
  "successful": [
    {
      "entity": {
        "id": 8863,
        "type": "story",
        "by": "dhouston",
        "title": "My YC app: Dropbox",
        "sourceType": "ARRAY_POST",
        "createdAt": "2025-09-17T10:30:00",
        "updatedAt": "2025-09-17T10:30:00"
      },
      "metadata": {
        "id": "550e8400-e29b-41d4-a716-446655440001",
        "state": "created"
      }
    }
  ],
  "failed": [
    {
      "item": {
        "id": 8864,
        "type": "comment",
        "by": "user123",
        "parent": 8863,
        "text": "Great idea!",
        "sourceType": "ARRAY_POST"
      },
      "error": "Validation failed: parent item not found"
    }
  ],
  "summary": {
    "total": 2,
    "successful": 1,
    "failed": 1
  }
}
```

### 3. Get HN Item by ID
**GET** `/api/hnitem/{id}`

Retrieves an HN item by its technical UUID.

**Response:**
```json
{
  "entity": {
    "id": 8863,
    "type": "story",
    "by": "dhouston",
    "title": "My YC app: Dropbox",
    "sourceType": "SINGLE_POST",
    "createdAt": "2025-09-17T10:30:00",
    "updatedAt": "2025-09-17T10:30:00"
  },
  "metadata": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "state": "indexed",
    "version": 1
  }
}
```

### 4. Get HN Item by HN ID
**GET** `/api/hnitem/hn/{hnId}`

Retrieves an HN item by its Hacker News ID.

**Response:** Same as above

### 5. Update HN Item
**PUT** `/api/hnitem/{id}?transition=validate_item`

Updates an HN item with optional workflow transition.

**Request Body:**
```json
{
  "id": 8863,
  "type": "story",
  "by": "dhouston",
  "title": "My YC app: Dropbox - Updated Title",
  "score": 150
}
```

**Response:** Same structure as create response

### 6. Delete HN Item
**DELETE** `/api/hnitem/{id}`

Deletes an HN item by technical UUID.

**Response:** 204 No Content

### 7. Search HN Items
**GET** `/api/hnitem/search?query=dropbox&type=story&author=dhouston`

Searches HN items with query parameters.

**Query Parameters:**
- `query` (optional): Text search across title, text, and url
- `type` (optional): Filter by item type
- `author` (optional): Filter by author
- `minScore` (optional): Minimum score filter
- `maxScore` (optional): Maximum score filter
- `hasUrl` (optional): Filter items with/without URL
- `limit` (optional): Maximum results (default: 50)
- `offset` (optional): Pagination offset (default: 0)

**Response:**
```json
{
  "items": [
    {
      "entity": {
        "id": 8863,
        "type": "story",
        "by": "dhouston",
        "title": "My YC app: Dropbox",
        "score": 111
      },
      "metadata": {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "state": "indexed"
      }
    }
  ],
  "pagination": {
    "total": 1,
    "limit": 50,
    "offset": 0,
    "hasMore": false
  }
}
```

### 8. Get Item Hierarchy
**GET** `/api/hnitem/{id}/hierarchy`

Gets an item with its parent hierarchy and children.

**Response:**
```json
{
  "item": {
    "entity": {
      "id": 8952,
      "type": "comment",
      "parent": 8863,
      "text": "This is a comment"
    },
    "metadata": {
      "id": "550e8400-e29b-41d4-a716-446655440002"
    }
  },
  "parents": [
    {
      "entity": {
        "id": 8863,
        "type": "story",
        "title": "My YC app: Dropbox"
      },
      "metadata": {
        "id": "550e8400-e29b-41d4-a716-446655440000"
      }
    }
  ],
  "children": [
    {
      "entity": {
        "id": 8953,
        "type": "comment",
        "parent": 8952,
        "text": "Reply to comment"
      },
      "metadata": {
        "id": "550e8400-e29b-41d4-a716-446655440003"
      }
    }
  ]
}
```

### 9. Trigger Firebase API Pull
**POST** `/api/hnitem/pull-from-firebase`

Triggers pulling data from Firebase HN API.

**Request Body:**
```json
{
  "itemIds": [8863, 8864, 8865],
  "pullType": "SPECIFIC_ITEMS"
}
```

**Response:**
```json
{
  "pullId": "pull-550e8400-e29b-41d4-a716-446655440000",
  "status": "INITIATED",
  "requestedItems": 3,
  "message": "Firebase pull initiated for 3 items"
}
```

### 10. Get All Items
**GET** `/api/hnitem`

Gets all HN items (paginated).

**Query Parameters:**
- `limit` (optional): Maximum results (default: 50, max: 500)
- `offset` (optional): Pagination offset (default: 0)

**Response:** Same structure as search response
