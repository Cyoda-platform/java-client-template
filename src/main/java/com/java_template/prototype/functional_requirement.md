### 1. Entity Definitions

```
HackerNewsItem:
- id: Long (Hacker News item unique identifier, required)
- type: String (Hacker News item type, required)
- originalJson: String (Raw JSON representation of the Hacker News item from Firebase HN API)
- state: String (VALID or INVALID, assigned after validation)
- importTimestamp: String (Timestamp of import, stored separately from originalJson, ISO 8601 format recommended)
```

### 2. Process Method Flows

```
processHackerNewsItem() Flow:
1. Initial State: HackerNewsItem entity created with originalJson payload
2. Validation: Check presence of 'id' and 'type' fields inside originalJson
3. Enrichment: Add importTimestamp (current time in ISO 8601 format)
4. State Assignment: 
   - If 'id' and 'type' are present → state = VALID
   - Otherwise → state = INVALID
5. Persistence: Store entity with originalJson, state, and importTimestamp separately
6. Completion: Entity ready for retrieval via GET endpoints
```

### 3. API Endpoints Design

- **POST /hackerNewsItem**
  - Function: Create a new HackerNewsItem entity.
  - Input: JSON payload of the Hacker News item in Firebase HN API format.
  - Output: JSON containing only the `technicalId` (internal datastore identifier).
  - Behavior: Persist immutable HackerNewsItem entity → triggers `processHackerNewsItem()` event.

- **GET /hackerNewsItem/{technicalId}**
  - Function: Retrieve the stored HackerNewsItem by its `technicalId`.
  - Output: JSON including:
    - originalJson (the raw item JSON)
    - state (VALID or INVALID)
    - importTimestamp (ISO 8601 string)

### 4. Request/Response Formats

**POST /hackerNewsItem**

_Request Body Example:_

```json
{
  "id": 12345,
  "type": "story",
  "by": "authorName",
  "time": 1609459200,
  "text": "Example text"
}
```

_Response Body Example:_

```json
{
  "technicalId": "abc123xyz"
}
```

---

**GET /hackerNewsItem/{technicalId}**

_Response Body Example:_

```json
{
  "originalJson": {
    "id": 12345,
    "type": "story",
    "by": "authorName",
    "time": 1609459200,
    "text": "Example text"
  },
  "state": "VALID",
  "importTimestamp": "2024-06-01T12:00:00Z"
}
```

### 5. Mermaid Diagram: Event-Driven Processing Chain

```mermaid
flowchart TD
  ClientPOST[POST /hackerNewsItem]
  StoreEntity[Persist HackerNewsItem Entity]
  TriggerProcess[Trigger processHackerNewsItem()]
  ValidateFields[Validate 'id' and 'type' fields]
  AssignState[Assign state: VALID or INVALID]
  AddTimestamp[Add importTimestamp]
  Complete[Entity Ready for Retrieval]

  ClientPOST --> StoreEntity --> TriggerProcess
  TriggerProcess --> ValidateFields --> AssignState --> AddTimestamp --> Complete
```