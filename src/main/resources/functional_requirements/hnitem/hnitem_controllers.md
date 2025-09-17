# HN Item Controllers

## HnItemController

### POST /api/hnitem
Create a single HN item
- **Transition**: validate_item (nullable)
- **Request**: Single HN item JSON
- **Response**: Created HN item with metadata

**Request Example**:
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
  "kids": [8952, 9224, 8917]
}
```

**Response Example**:
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
    "kids": [8952, 9224, 8917]
  },
  "meta": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "state": "pending",
    "createdAt": "2024-01-15T10:30:00Z"
  }
}
```

### POST /api/hnitem/batch
Create multiple HN items
- **Transition**: validate_item (nullable)
- **Request**: Array of HN items
- **Response**: Array of created HN items with metadata

### POST /api/hnitem/bulk
Bulk upload HN items from JSON file
- **Transition**: validate_item (nullable)
- **Request**: Multipart file upload
- **Response**: Bulk operation result

### GET /api/hnitem/{id}
Retrieve HN item by ID
- **Response**: HN item with metadata

### GET /api/hnitem/search
Search HN items with parent hierarchy joins
- **Parameters**: query, type, author, parent
- **Response**: Array of matching HN items

### PUT /api/hnitem/{id}
Update HN item with transition
- **Transition**: process_item, retry_processing (nullable)
- **Request**: Updated HN item data
- **Response**: Updated HN item with metadata
