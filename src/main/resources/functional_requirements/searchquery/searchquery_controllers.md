# SearchQuery Controllers

## Overview
The SearchQueryController provides REST API endpoints for searching HN items with advanced filtering, hierarchy traversal, and result caching. It supports complex queries with full-text search and parent-child relationship navigation.

## Controller: SearchQueryController

### Base Path: `/api/v1/search`

## Endpoints

### 1. Create and Execute Search
**Endpoint**: `POST /api/v1/search`
**Description**: Create and execute a search query for HN items
**Method**: POST
**Transition**: `auto_create`

#### Request
```json
{
  "keywords": "dropbox startup",
  "author": "dhouston",
  "itemTypes": ["story", "comment"],
  "minScore": 50,
  "maxScore": 1000,
  "fromTime": 1175714200,
  "toTime": 1175800600,
  "parentId": null,
  "rootItemsOnly": false,
  "includeDescendants": true,
  "maxDepth": 3,
  "sortBy": "score",
  "sortOrder": "DESC",
  "limit": 20,
  "offset": 0,
  "requestedBy": "user123"
}
```

#### Response
```json
{
  "success": true,
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "entity": {
      "queryId": "search-12345",
      "keywords": "dropbox startup",
      "author": "dhouston",
      "itemTypes": ["story", "comment"],
      "minScore": 50,
      "maxScore": 1000,
      "fromTime": 1175714200,
      "toTime": 1175800600,
      "sortBy": "score",
      "sortOrder": "DESC",
      "limit": 20,
      "offset": 0,
      "createdAt": "2024-01-15T10:30:00Z",
      "executedAt": "2024-01-15T10:30:05Z",
      "executionTimeMs": 150,
      "resultCount": 5,
      "totalMatches": 25,
      "requestedBy": "user123"
    },
    "meta": {
      "state": "completed",
      "version": 1,
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:30:05Z"
    },
    "results": [
      {
        "uuid": "550e8400-e29b-41d4-a716-446655440001",
        "hnId": 8863,
        "type": "story",
        "title": "My YC app: Dropbox - Throw away your USB drive",
        "by": "dhouston",
        "score": 111,
        "time": 1175714200,
        "url": "http://www.getdropbox.com/u/2/screencast.html"
      }
    ]
  }
}
```

### 2. Get Search Results
**Endpoint**: `GET /api/v1/search/{uuid}/results`
**Description**: Get results from a previously executed search
**Method**: GET

#### Request Parameters
```
limit=20
offset=0
includeHierarchy=false
```

#### Response
```json
{
  "success": true,
  "data": {
    "queryId": "search-12345",
    "results": [
      {
        "uuid": "550e8400-e29b-41d4-a716-446655440001",
        "hnId": 8863,
        "type": "story",
        "title": "My YC app: Dropbox - Throw away your USB drive",
        "by": "dhouston",
        "score": 111,
        "time": 1175714200,
        "descendants": 71,
        "relevanceScore": 0.95
      }
    ],
    "pagination": {
      "total": 25,
      "limit": 20,
      "offset": 0,
      "hasMore": true
    },
    "searchInfo": {
      "executedAt": "2024-01-15T10:30:05Z",
      "executionTimeMs": 150,
      "cached": true,
      "cacheExpiresAt": "2024-01-15T11:30:05Z"
    }
  }
}
```

### 3. Quick Search
**Endpoint**: `GET /api/v1/search/quick`
**Description**: Execute a simple search with query parameters
**Method**: GET

#### Request Parameters
```
q=dropbox startup
author=dhouston
type=story
minScore=50
limit=10
sortBy=score
sortOrder=DESC
```

#### Response
```json
{
  "success": true,
  "data": {
    "query": "dropbox startup",
    "results": [
      {
        "hnId": 8863,
        "type": "story",
        "title": "My YC app: Dropbox - Throw away your USB drive",
        "by": "dhouston",
        "score": 111,
        "time": 1175714200
      }
    ],
    "resultCount": 1,
    "executionTimeMs": 45,
    "cached": false
  }
}
```

### 4. Search with Hierarchy
**Endpoint**: `POST /api/v1/search/hierarchy`
**Description**: Search with parent-child hierarchy traversal
**Method**: POST
**Transition**: `auto_create`

#### Request
```json
{
  "parentId": 8863,
  "includeDescendants": true,
  "maxDepth": 5,
  "keywords": "great idea",
  "minScore": 1,
  "sortBy": "time",
  "sortOrder": "ASC",
  "limit": 50
}
```

#### Response
```json
{
  "success": true,
  "data": {
    "queryId": "search-hierarchy-12345",
    "rootItem": {
      "hnId": 8863,
      "type": "story",
      "title": "My YC app: Dropbox - Throw away your USB drive",
      "by": "dhouston"
    },
    "hierarchy": [
      {
        "hnId": 8952,
        "type": "comment",
        "by": "commenter1",
        "text": "Great idea! This could change everything.",
        "parent": 8863,
        "depth": 1,
        "children": [
          {
            "hnId": 9001,
            "type": "comment",
            "by": "commenter2",
            "text": "I agree, great idea indeed!",
            "parent": 8952,
            "depth": 2
          }
        ]
      }
    ],
    "totalItems": 15,
    "maxDepthReached": 3
  }
}
```

### 5. Get Search Query Details
**Endpoint**: `GET /api/v1/search/{uuid}`
**Description**: Get search query details and metadata
**Method**: GET

#### Response
```json
{
  "success": true,
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "entity": {
      "queryId": "search-12345",
      "keywords": "dropbox startup",
      "author": "dhouston",
      "itemTypes": ["story", "comment"],
      "minScore": 50,
      "maxScore": 1000,
      "sortBy": "score",
      "sortOrder": "DESC",
      "limit": 20,
      "offset": 0,
      "createdAt": "2024-01-15T10:30:00Z",
      "executedAt": "2024-01-15T10:30:05Z",
      "executionTimeMs": 150,
      "resultCount": 5,
      "totalMatches": 25,
      "resultSummary": "Found 25 items matching 'dropbox startup' by dhouston",
      "cachedUntil": "2024-01-15T11:30:05Z",
      "requestedBy": "user123"
    },
    "meta": {
      "state": "cached",
      "version": 2,
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:30:10Z"
    }
  }
}
```

### 6. List Search Queries
**Endpoint**: `GET /api/v1/search`
**Description**: List search queries with pagination and filters
**Method**: GET

#### Request Parameters
```
requestedBy=user123
state=completed,cached
fromDate=2024-01-15T00:00:00Z
toDate=2024-01-15T23:59:59Z
limit=20
offset=0
sortBy=createdAt
sortOrder=DESC
```

#### Response
```json
{
  "success": true,
  "data": {
    "queries": [
      {
        "uuid": "550e8400-e29b-41d4-a716-446655440000",
        "queryId": "search-12345",
        "keywords": "dropbox startup",
        "author": "dhouston",
        "resultCount": 5,
        "totalMatches": 25,
        "executionTimeMs": 150,
        "state": "cached",
        "createdAt": "2024-01-15T10:30:00Z",
        "requestedBy": "user123"
      }
    ],
    "pagination": {
      "total": 1,
      "limit": 20,
      "offset": 0,
      "hasMore": false
    }
  }
}
```

### 7. Refresh Search Query
**Endpoint**: `POST /api/v1/search/{uuid}/refresh`
**Description**: Refresh expired or cached search query
**Method**: POST
**Transition**: `refresh_query`

#### Response
```json
{
  "success": true,
  "data": {
    "queryId": "search-12345",
    "refreshed": true,
    "previousState": "expired",
    "newState": "executing",
    "refreshStartedAt": "2024-01-15T11:00:00Z",
    "estimatedCompletionTime": "2024-01-15T11:00:05Z"
  }
}
```

### 8. Export Search Results
**Endpoint**: `GET /api/v1/search/{uuid}/export`
**Description**: Export search results to file
**Method**: GET

#### Request Parameters
```
format=json  // or "csv"
includeMetadata=true
includeHierarchy=false
```

#### Response
```
Content-Type: application/json
Content-Disposition: attachment; filename="search-12345-results.json"

{
  "searchInfo": {
    "queryId": "search-12345",
    "keywords": "dropbox startup",
    "executedAt": "2024-01-15T10:30:05Z",
    "resultCount": 5,
    "totalMatches": 25
  },
  "results": [
    {
      "hnId": 8863,
      "type": "story",
      "title": "My YC app: Dropbox - Throw away your USB drive",
      "by": "dhouston",
      "score": 111,
      "time": 1175714200,
      "url": "http://www.getdropbox.com/u/2/screencast.html"
    }
  ]
}
```

### 9. Get Popular Searches
**Endpoint**: `GET /api/v1/search/popular`
**Description**: Get frequently executed search queries
**Method**: GET

#### Request Parameters
```
timeframe=24h  // or "7d", "30d"
limit=10
```

#### Response
```json
{
  "success": true,
  "data": {
    "timeframe": "24h",
    "popularSearches": [
      {
        "keywords": "dropbox",
        "executionCount": 15,
        "avgExecutionTime": 120,
        "avgResultCount": 8
      },
      {
        "keywords": "startup",
        "executionCount": 12,
        "avgExecutionTime": 95,
        "avgResultCount": 25
      }
    ]
  }
}
```

### 10. Search Suggestions
**Endpoint**: `GET /api/v1/search/suggestions`
**Description**: Get search suggestions based on partial input
**Method**: GET

#### Request Parameters
```
q=drop
limit=5
```

#### Response
```json
{
  "success": true,
  "data": {
    "query": "drop",
    "suggestions": [
      {
        "text": "dropbox",
        "type": "keyword",
        "frequency": 25
      },
      {
        "text": "dropdown",
        "type": "keyword",
        "frequency": 8
      },
      {
        "text": "dhouston",
        "type": "author",
        "frequency": 15
      }
    ]
  }
}
```

## Error Responses

### Invalid Search Parameters
```json
{
  "success": false,
  "error": {
    "code": "INVALID_SEARCH_PARAMS",
    "message": "Invalid search parameters",
    "details": {
      "field": "fromTime",
      "reason": "fromTime must be less than toTime",
      "fromTime": 1175800600,
      "toTime": 1175714200
    }
  }
}
```

### Search Timeout Error
```json
{
  "success": false,
  "error": {
    "code": "SEARCH_TIMEOUT",
    "message": "Search query timed out",
    "details": {
      "queryId": "search-12345",
      "timeoutMs": 20000,
      "suggestion": "Try narrowing your search criteria"
    }
  }
}
```

### Search Not Found Error
```json
{
  "success": false,
  "error": {
    "code": "SEARCH_NOT_FOUND",
    "message": "Search query not found",
    "details": {
      "uuid": "550e8400-e29b-41d4-a716-446655440000"
    }
  }
}
```
