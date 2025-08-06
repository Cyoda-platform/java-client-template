```markdown
# Requirement Specification: Hacker News Item Storage Service in Java

## Overview
Build a service in Java that stores Hacker News items using the JSON format as defined by the Firebase Hacker News API. The service will perform validation on incoming items, enrich them with metadata, and support retrieval by item ID.

---

## Functional Requirements

### 1. Item Storage
- The service must accept Hacker News items in JSON format exactly as per the Firebase Hacker News API specification.
- Each item must be stored preserving the original JSON without modification.

### 2. Validation on Save
- When saving an item, validate that **both** the fields `id` and `type` are present in the JSON payload.
- Based on validation:
  - If **both** `id` and `type` are present, assign the item a state of `VALID`.
  - If either `id` or `type` is missing, assign the item a state of `INVALID`.

### 3. Metadata Enrichment
- Each saved item must be enriched with an **import timestamp** indicating the exact time of import.
- The import timestamp and the state are stored **separately** from the original JSON payload (i.e., not embedded inside the JSON).

### 4. Item Retrieval
- The service must allow querying an item by its `id`.
- The response must return:
  - The original JSON item as stored.
  - The `state` (VALID or INVALID).
  - The `import timestamp`.

---

## Non-Functional Requirements
- The service should be implemented in **Java**.
- Use of standard JSON libraries (e.g., Jackson) for JSON handling.
- Persistence layer: can be in-memory or persistent store but must ensure retrieval by ID.
- The API should follow RESTful principles or equivalent for saving and retrieving items.

---

## API Endpoints (Example)

### POST `/items`
- Request body: JSON representing a Hacker News item.
- Response: Confirmation of save with assigned state and import timestamp (optional).

### GET `/items/{id}`
- Response body:
  ```json
  {
    "originalJson": { /* original HN item JSON */ },
    "state": "VALID" | "INVALID",
    "importTimestamp": "ISO-8601 timestamp"
  }
  ```

---

## Data Model Summary

| Field           | Description                                | Notes                                |
|-----------------|--------------------------------------------|------------------------------------|
| originalJson    | The full JSON object of the Hacker News item | Stored exactly as received          |
| id              | Unique identifier of the item              | Must exist for VALID state          |
| type            | Type of Hacker News item (story, comment, etc.) | Must exist for VALID state          |
| importTimestamp | Timestamp when the item was imported       | Stored separately from JSON         |
| state           | Validation state (`VALID` or `INVALID`)     | Based on presence of `id` and `type` |

---

## Summary
- Validate presence of `id` and `type` on save.
- Store original JSON unmodified.
- Enrich with import timestamp and validation state stored separately.
- Retrieve by `id` returning the original JSON plus metadata.

---

This specification preserves all business logic and technical details for the requested Hacker News Java service.
```