# Functional Requirement

## HackerNewsItem Prototype Controller

This prototype controller exposes REST endpoints to manage HackerNewsItem entities with an event-driven architecture pattern.

### Endpoints

- **POST /prototype/hackernewsitem**: Create a new HackerNewsItem event.
  - Validates presence of required fields `id` and `type` in the request body.
  - Generates UUIDs for business and technical IDs.
  - Stores the item in an in-memory cache.
  - Triggers event processing with validation and logging.
  - Returns the generated UUID and validation status.

- **GET /prototype/hackernewsitem/{uuid}**: Retrieve a HackerNewsItem by UUID.
  - Returns a JSON object with three top-level fields:
    - `content`: The raw HackerNewsItem content as parsed JSON.
    - `timestamp`: The timestamp when the item was stored.
    - `status`: Validation status, either "VALIDATED" or "INVALID".
  - Returns 404 if the item is not found.

### Design Principles

- Uses an append-only, immutable event-driven architecture.
- No PUT, PATCH, or DELETE endpoints; all changes are posted as new events.
- AtomicLong used for incremental IDs, UUIDs used for entity IDs.
- Processes entities in dedicated methods for validation and potential enrichment.
- Uses Lombok for entity data classes.

### Validation

- Ensures required fields are present on creation.
- Returns appropriate HTTP status codes and error messages.
- Logs all operations for traceability.

### Caching

- In-memory ConcurrentHashMap caches entities by UUID.

### Notes

- JSON serialization and deserialization handled with Jackson ObjectMapper.
- Prototype is designed for demonstration and testing of API design and event-driven flow.