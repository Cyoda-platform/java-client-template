```markdown
# Requirement Specification: Hacker News Item Storage Service in Java

## Overview
Build a Java service that stores Hacker News items in the JSON format compatible with the Firebase Hacker News API. The service must perform validation, enrichment, and retrieval operations on the items.

## Functional Requirements

1. **Store Hacker News Items**
   - Accept JSON objects representing Hacker News items in the Firebase HN API format.
   - Validate incoming JSON to ensure the presence of mandatory fields:
     - `"id"` (unique identifier for the item)
     - `"type"` (type of the item, e.g., "story", "comment", etc.)
   - Reject/save failure if either `"id"` or `"type"` is missing.

2. **Enrich Items on Save**
   - Add a new field `"importTimestamp"` to each saved item.
   - The `"importTimestamp"` should represent the timestamp when the item was stored (e.g., in ISO 8601 format or UNIX epoch milliseconds).

3. **Retrieve Items**
   - Support retrieval of stored items by their `"id"`.
   - The retrieval should return the original JSON object, including the added `"importTimestamp"` field.

## Technical Details

- **Programming Language:** Java (recommended Spring Boot framework for REST API)
- **Data Format:** JSON matching the Firebase Hacker News API structure
- **Validation Rules:**
  - `"id"` field must be present and non-null.
  - `"type"` field must be present and non-null.
- **Storage:**
  - Can be in-memory, file-based, or database, but must persist JSON objects exactly as stored (with enrichment).
- **APIs:**
  - **POST /items** — Accepts JSON payload for a Hacker News item, validates, enriches, stores, and responds with success or error.
  - **GET /items/{id}** — Returns the stored JSON object for the given `id`.

## Example JSON Item (simplified)
```json
{
  "id": 8863,
  "type": "story",
  "by": "dhouston",
  "time": 1175714200,
  "text": "My YC app: Dropbox - Throw away your USB drive",
  "importTimestamp": "2024-06-01T12:34:56Z"
}
```

## Summary
- Validate `"id"` and `"type"` on save.
- Enrich with `"importTimestamp"` on save.
- Allow retrieval by `"id"`.
- Return original JSON including enrichment.

---

This completes the detailed technical requirement for the Java Hacker News item storage service.
```