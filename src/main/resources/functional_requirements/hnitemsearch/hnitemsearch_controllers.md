# HNItemSearch Controllers

## HNItemSearchController

### Endpoints

#### POST /api/hnitem/search
Execute search query for HN items
- **Transition**: auto_create (automatic)

**Request Example:**
```json
{
  "query": "dropbox",
  "searchType": "text",
  "filters": {
    "type": "story",
    "minScore": 10
  },
  "includeParents": true,
  "maxResults": 50
}
```

**Response Example:**
```json
{
  "entity": {
    "searchId": "search-789",
    "query": "dropbox",
    "searchType": "text",
    "filters": {
      "type": "story",
      "minScore": 10
    },
    "includeParents": true,
    "maxResults": 50,
    "resultCount": 0,
    "searchTimestamp": "2024-01-15T10:30:00Z"
  },
  "meta": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "state": "pending"
  }
}
```

#### GET /api/hnitem/search/{searchId}
Get search results

**Response Example:**
```json
{
  "entity": {
    "searchId": "search-789",
    "query": "dropbox",
    "searchType": "text",
    "resultCount": 25,
    "executionTimeMs": 150,
    "searchTimestamp": "2024-01-15T10:30:15Z"
  },
  "meta": {
    "state": "completed"
  }
}
```

#### POST /api/hnitem/search/hierarchical
Search with parent hierarchy joins
- **Transition**: auto_create (automatic)

**Request Example:**
```json
{
  "query": "paul graham",
  "searchType": "hierarchical",
  "filters": {
    "type": "comment",
    "parentType": "story"
  },
  "includeParents": true,
  "maxResults": 100
}
```

#### GET /api/hnitem/search/{searchId}/results
Get detailed search results with items

**Response Example:**
```json
{
  "searchId": "search-789",
  "results": [
    {
      "id": 8863,
      "type": "story",
      "title": "My YC app: Dropbox",
      "score": 111,
      "parents": []
    }
  ],
  "totalResults": 25,
  "executionTimeMs": 150
}
```
