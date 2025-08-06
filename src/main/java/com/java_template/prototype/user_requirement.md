```markdown
# Requirement Specification: Hacker News Item Storage Service in Java

## Overview
Build a Java service that stores Hacker News items in the exact JSON format as provided by the Firebase Hacker News API. The service must validate, enrich, store, and retrieve items according to the specifications below.

---

## Functional Requirements

### 1. Store Hacker News Items
- Accept JSON payload representing a Hacker News item, structured exactly as from the Firebase HN API.
- Validate the presence of the following mandatory fields in the incoming JSON:
  - `"id"` (numeric identifier of the item)
  - `"type"` (string representing the item type, e.g., "story", "comment", etc.)
- If either `"id"` or `"type"` is missing, the service must reject the request with an appropriate error response.
- Enrich the item by adding a new field:
  - `"importTimestamp"`: a timestamp (e.g., ISO 8601 format or Unix epoch milliseconds) representing when the item was saved/imported into the service.
- Store the enriched item in a persistent store, preserving the original JSON structure plus the `"importTimestamp"` field.

### 2. Retrieve Hacker News Items
- Provide an API endpoint to retrieve a stored item by its `"id"`.
- Return the stored item as the original JSON structure, including all fields plus the `"importTimestamp"`.
- If the item with the specified `"id"` does not exist, return an appropriate error (e.g., HTTP 404).

---

## Non-Functional Requirements

- The service must be implemented in **Java**, preferably using **Spring Boot** framework (Java 21 compatibility).
- The JSON structure must be preserved exactly as per Firebase HN API, with only the addition of the `"importTimestamp"` field.
- Input and output must be JSON.
- Validation must be strict: missing `"id"` or `"type"` must cause rejection.
- Timestamp format for `"importTimestamp"` should be consistent and machine-readable (ISO 8601 recommended).

---

## API Specification

### POST `/items`
- **Request Body:** JSON object representing a Hacker News item.
- **Validation:** Check presence of `"id"` and `"type"`.
- **Behavior:** Enrich with `"importTimestamp"` and store.
- **Response:**
  - `201 Created` with saved item JSON (including `"importTimestamp"`), or
  - `400 Bad Request` if validation fails.

### GET `/items/{id}`
- **Path Parameter:** `id` (numeric)
- **Response:**
  - `200 OK` with JSON of the stored item including `"importTimestamp"`, or
  - `404 Not Found` if no item with such `id` exists.

---

## Data Storage

- The service can use an in-memory store (e.g., ConcurrentHashMap) or a persistent database.
- The key for storage is the `"id"` field.
- Stored value is the full JSON object enriched with `"importTimestamp"`.

---

## Example

### Input JSON (POST `/items`)
```json
{
  "id": 8863,
  "type": "story",
  "by": "dhouston",
  "time": 1175714200,
  "text": "This is a story",
  // other fields
}
```

### Stored JSON (with enrichment)
```json
{
  "id": 8863,
  "type": "story",
  "by": "dhouston",
  "time": 1175714200,
  "text": "This is a story",
  "importTimestamp": "2024-04-27T15:30:00Z"
  // other fields preserved exactly
}
```

---

## Summary

- **Validate:** `"id"` and `"type"` presence on save.
- **Enrich:** Add `"importTimestamp"` on save.
- **Store:** Full JSON including enrichment.
- **Retrieve:** Return full JSON by `id`.

This ensures compliance with the Firebase Hacker News API JSON format while adding import metadata and retrieval capability.
```