# HNItem Controllers

## HNItemController

### Endpoints

#### POST /api/hnitem
Create a new HN item
- **Transition**: auto_create (automatic)

**Request Example:**
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

**Response Example:**
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

#### GET /api/hnitem/{id}
Retrieve HN item by ID

**Response Example:**
```json
{
  "entity": {
    "id": 8863,
    "type": "story",
    "by": "dhouston",
    "title": "My YC app: Dropbox - Throw away your USB drive"
  },
  "meta": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "state": "active"
  }
}
```

#### PUT /api/hnitem/{id}
Update existing HN item
- **Transition**: update_item (manual)

**Request Example:**
```json
{
  "transition": "update_item",
  "entity": {
    "score": 125,
    "descendants": 75
  }
}
```

#### POST /api/hnitem/firebase-sync
Trigger Firebase HN API data pull
- **Transition**: auto_create (for new items)

**Request Example:**
```json
{
  "itemIds": [8863, 8864, 8865],
  "syncType": "incremental"
}
```
