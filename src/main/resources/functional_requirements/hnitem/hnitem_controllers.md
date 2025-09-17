# HN Item Controller Requirements

## Controller Name
**HnItemController**

## Description
REST API controller for managing Hacker News items with support for single item creation, bulk operations, search, and Firebase API integration.

## Endpoints

### 1. Create Single HN Item
- **Method**: POST
- **Path**: `/api/hnitem`
- **Purpose**: Create a single HN item
- **Request Body**: HN item JSON data
- **Transition**: `validate_item` (optional)

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
  "descendants": 71
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
    "descendants": 71
  },
  "meta": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "state": "created",
    "version": 1
  }
}
```

### 2. Create Multiple HN Items
- **Method**: POST
- **Path**: `/api/hnitem/batch`
- **Purpose**: Create multiple HN items from array
- **Request Body**: Array of HN item JSON data
- **Transition**: `validate_item` (optional)

### 3. Bulk Upload from File
- **Method**: POST
- **Path**: `/api/hnitem/upload`
- **Purpose**: Upload HN items from JSON file
- **Request**: Multipart file upload
- **Transition**: `validate_item` (optional)

### 4. Trigger Firebase API Pull
- **Method**: POST
- **Path**: `/api/hnitem/fetch-from-firebase`
- **Purpose**: Fetch latest items from Firebase HN API
- **Request Body**: Optional parameters (item IDs, story types)

### 5. Search HN Items
- **Method**: GET
- **Path**: `/api/hnitem/search`
- **Purpose**: Search items with hierarchical joins
- **Query Parameters**: text, type, author, parent, limit, offset

### 6. Get HN Item by ID
- **Method**: GET
- **Path**: `/api/hnitem/{id}`
- **Purpose**: Retrieve single item with metadata

### 7. Update HN Item State
- **Method**: PUT
- **Path**: `/api/hnitem/{id}/transition`
- **Purpose**: Trigger workflow transitions
- **Request Body**: `{"transition": "validate_item"}`
