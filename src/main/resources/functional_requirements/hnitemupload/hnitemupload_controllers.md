# HNItemUpload Controllers

## HNItemUploadController

### Endpoints

#### POST /api/hnitem/upload/single
Upload a single HN item
- **Transition**: auto_create (automatic)

**Request Example:**
```json
{
  "uploadType": "single",
  "item": {
    "id": 8863,
    "type": "story",
    "by": "dhouston",
    "title": "My YC app: Dropbox"
  }
}
```

**Response Example:**
```json
{
  "entity": {
    "uploadId": "upload-123",
    "uploadType": "single",
    "totalItems": 1,
    "processedItems": 0,
    "failedItems": 0,
    "uploadTimestamp": "2024-01-15T10:30:00Z"
  },
  "meta": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "state": "pending"
  }
}
```

#### POST /api/hnitem/upload/array
Upload array of HN items
- **Transition**: auto_create (automatic)

**Request Example:**
```json
{
  "uploadType": "array",
  "items": [
    {"id": 8863, "type": "story", "title": "Item 1"},
    {"id": 8864, "type": "comment", "text": "Comment 1"}
  ]
}
```

#### POST /api/hnitem/upload/file
Upload HN items from JSON file
- **Transition**: auto_create (automatic)

**Request**: Multipart form with JSON file

**Response Example:**
```json
{
  "entity": {
    "uploadId": "upload-456",
    "uploadType": "file",
    "fileName": "hn_items.json",
    "totalItems": 100,
    "processedItems": 0
  },
  "meta": {
    "state": "pending"
  }
}
```

#### GET /api/hnitem/upload/{uploadId}
Get upload status

**Response Example:**
```json
{
  "entity": {
    "uploadId": "upload-123",
    "uploadType": "single",
    "totalItems": 1,
    "processedItems": 1,
    "failedItems": 0,
    "completionTimestamp": "2024-01-15T10:31:00Z"
  },
  "meta": {
    "state": "completed"
  }
}
```
