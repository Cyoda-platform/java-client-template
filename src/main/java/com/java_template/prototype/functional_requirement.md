# Functional Requirements - Hacker News Item Importer

## Overview

This service accepts Hacker News (HN) items in the JSON format used by the Firebase HN API, stores them, and allows retrieval by id. The service will perform validation and enrichment of items when they are saved.

## API (Behavioral Contracts)

- Save item
  - Endpoint: POST /items
  - Request: Content-Type: application/json. Body: JSON representing an HN item.
  - Successful responses:
    - 201 Created: when a new item is stored.
    - 200 OK: when an existing item with the same id is updated (see Storage semantics).
  - Error responses:
    - 400 Bad Request: if validation fails (see Validation rules).

- Retrieve item
  - Endpoint: GET /items/{id}
  - Response:
    - 200 OK with Content-Type: application/json and the stored JSON body (see Retrieval behavior).
    - 404 Not Found: if no item with the requested id exists.

## Input Validation Rules

When saving an item, the service MUST:

- Require that the payload is valid JSON.
- Require that the top-level fields "id" and "type" are present.
  - "id" MUST be an integer (or a JSON number without fractional part) and MUST be >= 0.
  - "type" MUST be a non-empty string.
- Optionally (recommended): validate that "type" is one of the known HN types: `job`, `story`, `comment`, `poll`, `pollopt`. If an unknown type is provided the service SHOULD accept it but log a warning. If stricter semantics are required, the service can reject unknown types with 400 Bad Request (this should be configurable).
- NOT require other fields from the HN schema; unknown or additional fields MUST be preserved.

On validation failure the service MUST return 400 Bad Request and include a machine-readable error message explaining which validation rule failed.

## Enrichment Rules

- The service MUST add an `importTimestamp` field to the stored JSON when an item is saved.
- `importTimestamp` MUST be an ISO 8601 timestamp in UTC with seconds precision (e.g. `2025-08-15T12:34:56Z`). Millisecond precision is allowed but not required; consistency across the system is required.
- `importTimestamp` MUST reflect the time the item was accepted and stored by the service (not the time in the original HN item).

## Storage Semantics

- The service MUST store the entire JSON object it receives (after enrichment) so that retrieval returns the same JSON that was stored.
- The service MUST preserve all original fields from the incoming JSON and only add (or update) the `importTimestamp` field.
- Id uniqueness / upsert behavior:
  - If an item with the same `id` does not already exist, the service MUST create a new stored item and return 201 Created.
  - If an item with the same `id` already exists, the service MUST overwrite the existing stored item with the newly saved JSON (including updating `importTimestamp`) and return 200 OK.
  - The overwrite must be atomic and thread-safe.
- The persistence mechanism is implementation-defined (in-memory, file-based, database). The functional requirements require that the storage be pluggable; the default prototype implementation may use an in-memory store but must be clearly documented as ephemeral.

## Retrieval Behavior

- GET /items/{id} MUST return the stored JSON exactly as it was saved (including the `importTimestamp` added by the service).
- The service MUST return Content-Type: application/json and must not alter the JSON structure or field ordering unnecessarily. (Minor differences in field order are acceptable if they do not change semantics.)

## Error Handling

- All error responses MUST include a JSON body with at least the following fields:
  - `error`: short string code or title (e.g. `validation_error`, `not_found`).
  - `message`: human-readable explanation of the error.
- Validation failures return 400. Not found returns 404. Unexpected server errors return 500.

## Non-functional Requirements

- The save and retrieve operations MUST be thread-safe and perform correctly under concurrent requests.
- The service MUST log incoming requests and errors for observability, including when validation fails.
- The service MUST have unit and integration tests covering:
  - Valid save of new item.
  - Save that updates existing item (overwrite).
  - Validation failures for missing/invalid `id` and `type`.
  - Retrieval of existing item returns the enriched JSON.
  - Retrieval of non-existing item returns 404.

## Examples

- Save (request body):
  {
    "id": 12345,
    "by": "dhouston",
    "type": "story",
    "title": "An example story",
    "time": 1175714200,
    "url": "http://example.com"
  }

- Stored item after enrichment (response body for subsequent GET or as saved):
  {
    "id": 12345,
    "by": "dhouston",
    "type": "story",
    "title": "An example story",
    "time": 1175714200,
    "url": "http://example.com",
    "importTimestamp": "2025-08-15T12:34:56Z"
  }

## Notes and Rationale

- The requirements ensure the service preserves original HN data while recording when it was imported.
- The upsert behavior (overwrite on same id) is chosen to keep the service simple and idempotent for repeated imports; this can be changed to reject duplicates if a different behavior is desired.
- Timestamp format is ISO 8601 in UTC to ease interoperability.

If you want a stricter or different behavior (for example, rejecting duplicates instead of overwriting, or enforcing a closed set of `type` values), tell me which option to prefer and I will update the requirements accordingly.