# Hacker News Item Service Implementation

## Overview

A workflow-driven Spring Boot service that stores Hacker News items in the JSON format of the Firebase HN API. The service validates required fields (`id` and `type`), enriches items with an `importTimestamp`, and provides REST endpoints for submission and retrieval.

## Architecture

### Entity: HackerNewsItem
- **Location:** `src/main/java/com/example/application/entity/hacker_news_item/version_1/HackerNewsItem.java`
- **Fields:**
  - `id` (Long): Business identifier from Hacker News
  - `type` (String): Item type (story, comment, job, poll, pollopt)
  - `rawJson` (JsonNode): Original Firebase API payload
  - `importTimestamp` (Instant): Server-side enrichment timestamp

### Workflow States
1. **RECEIVED** → Initial state when item is submitted
2. **VALIDATED** → Item passes validation (id and type present)
3. **STORED** → Item enriched with importTimestamp and persisted
4. **REJECTED** → Validation failed

### Components

#### Criterion: HackerNewsItemCriterion
- Validates presence of required fields (`id` and `type`)
- Returns `STRUCTURAL_FAILURE` if validation fails
- Transitions: RECEIVED → VALIDATED or REJECTED

#### Processor: HackerNewsItemProcessor
- Enriches item with current server timestamp
- Sets `importTimestamp = Instant.now()`
- Transitions: VALIDATED → STORED

#### Controller: HackerNewsItemController
- **POST /hn/items** - Submit item for storage
  - Request: Raw JSON matching Firebase API format
  - Response: 201 Created or 400 Bad Request
- **GET /hn/items/{id}** - Retrieve item by Hacker News ID
  - Response: 200 OK with original JSON or 404 Not Found

## Files Created

```
src/main/java/com/example/application/
├── entity/hacker_news_item/version_1/HackerNewsItem.java
├── criterion/HackerNewsItemCriterion.java
├── processor/HackerNewsItemProcessor.java
└── controller/HackerNewsItemController.java

src/main/resources/
├── entity/hacker_news_item/version_1/HackerNewsItem.json
└── workflow/hacker_news_item/version_1/HackerNewsItem.json
```

## Build Status

✅ **Compilation:** Successful
✅ **Full Build:** Successful (22s)
✅ **Tests:** All passing
✅ **Workflow Validation:** Successful

## Usage Example

### Submit Item
```bash
curl -X POST http://localhost:8080/hn/items \
  -H "Content-Type: application/json" \
  -d '{
    "id": 42161127,
    "type": "story",
    "by": "dang",
    "time": 1734691200,
    "title": "Show HN: Example",
    "url": "https://example.com"
  }'
```

### Retrieve Item
```bash
curl http://localhost:8080/hn/items/42161127
```

## Key Design Decisions

1. **Raw JSON Storage:** Original Firebase payload preserved verbatim
2. **Automatic Enrichment:** importTimestamp added during workflow processing
3. **Validation as Criterion:** Field validation implemented as pure function
4. **Workflow-Driven:** All state transitions managed by Cyoda workflow engine

