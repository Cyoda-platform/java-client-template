Certainly! To follow the requirement strictly:

- Move **all asynchronous and pre-persistence logic** from the controller endpoints into the corresponding `process{EntityName}` workflow functions.
- The workflow function receives the entity as an `ObjectNode` (Jackson JSON tree node), so all modifications should be done on it directly.
- The workflow can call `entityService` methods for **different** entityModels (not the current one), for supplementary or raw data.
- The controllers will become much thinner, delegating entity manipulation and async calls into these workflow functions.

---

Below is the **updated Java code** with these principles applied:

```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda/entity")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EntityService entityService;

    private static final String OPEN_LIBRARY_SEARCH_API = "https://openlibrary.org/search.json?q={query}";
    private static final String ENTITY_NAME = "BookEntity";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Workflow function to process BookEntity before persistence.
     * This function replaces all async and pre-persistence logic from controller.
     * It expects ObjectNode representing the entity and can mutate it.
     */
    private CompletableFuture<ObjectNode> processBookEntity(ObjectNode entity) {
        // 1. Modify entity directly if needed
        // Append "[Processed]" tag to title if not present
        if (entity.hasNonNull("title")) {
            String title = entity.get("title").asText();
            if (!title.contains("[Processed]")) {
                entity.put("title", title + " [Processed]");
            }
        }

        // 2. Example: Add supplementary entity (different model)
        // For example, raw data or audit log for the book
        ObjectNode auditEntity = objectMapper.createObjectNode();
        auditEntity.put("bookId", entity.path("bookId").asText(UUID.randomUUID().toString()));
        auditEntity.put("processedAt", Instant.now().toString());
        auditEntity.put("processedBy", "processBookEntityWorkflow");
        auditEntity.put("originalTitle", entity.path("title").asText());
        // Add audit entity asynchronously - different model e.g. "BookAudit"
        entityService.addItem("BookAudit", ENTITY_VERSION, auditEntity, obj -> CompletableFuture.completedFuture(obj));

        // 3. If you want to enrich entity with external data asynchronously before persistence,
        // you can make async calls here (e.g. enrich with cover image URL)
        // but here we keep it simple and synchronous for demo

        return CompletableFuture.completedFuture(entity);
    }

    @PostMapping("/books/search")
    public CompletableFuture<ResponseEntity<SearchResponse>> searchBooks(@RequestBody @Valid SearchRequest request) {
        logger.info("Received search request: query='{}' genre='{}' year='{}' author='{}'",
                request.getQuery(), request.getGenre(), request.getPublicationYear(), request.getAuthor());

        Map<String, String> uriVars = Map.of("query", request.getQuery());
        JsonNode root = restTemplate.getForObject(OPEN_LIBRARY_SEARCH_API, JsonNode.class, uriVars);
        if (root == null || !root.has("docs")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid data from Open Library API");
        }

        List<ObjectNode> filteredResults = new ArrayList<>();
        for (JsonNode doc : root.get("docs")) {
            ObjectNode bookNode = objectMapper.createObjectNode();
            String title = getFirstText(doc, "title");
            String author = getFirstTextFromArray(doc, "author_name");
            String coverId = doc.has("cover_i") ? doc.get("cover_i").asText() : null;
            String coverImageUrl = coverId != null
                    ? "https://covers.openlibrary.org/b/id/" + coverId + "-M.jpg"
                    : null;
            String genre = getFirstTextFromArray(doc, "subject");
            Integer pubYear = doc.has("first_publish_year") && !doc.get("first_publish_year").isNull()
                    ? doc.get("first_publish_year").asInt() : null;
            String bookId = doc.has("key") ? doc.get("key").asText() : UUID.randomUUID().toString();

            if (request.getGenre() != null && (genre == null || !genre.equalsIgnoreCase(request.getGenre()))) {
                continue;
            }
            if (request.getPublicationYear() != null && !request.getPublicationYear().equals(pubYear)) {
                continue;
            }
            if (request.getAuthor() != null && (author == null || !author.toLowerCase().contains(request.getAuthor().toLowerCase()))) {
                continue;
            }

            bookNode.put("title", title);
            bookNode.put("author", author);
            if (coverImageUrl != null) {
                bookNode.put("coverImageUrl", coverImageUrl);
            }
            bookNode.put("genre", genre);
            if (pubYear != null) {
                bookNode.put("publicationYear", pubYear);
            }
            bookNode.put("bookId", bookId);

            filteredResults.add(bookNode);
        }

        // Persist entities using workflow function processBookEntity per item
        // This will asynchronously apply the workflow on each entity before persist
        return entityService.addItems(ENTITY_NAME, ENTITY_VERSION, filteredResults, this::processBookEntity)
                .thenApply(ids -> {
                    // Convert ObjectNodes back to BookSummary DTOs for response
                    List<BookSummary> responseResults = new ArrayList<>();
                    for (ObjectNode node : filteredResults) {
                        BookSummary bs = new BookSummary(
                                node.path("title").asText(null),
                                node.path("author").asText(null),
                                node.path("coverImageUrl").asText(null),
                                node.path("genre").asText(null),
                                node.has("publicationYear") ? node.get("publicationYear").asInt() : null,
                                node.path("bookId").asText(null)
                        );
                        responseResults.add(bs);
                    }
                    return ResponseEntity.ok(new SearchResponse(responseResults));
                });
    }

    @GetMapping("/books/{bookId}")
    public CompletableFuture<ResponseEntity<BookDetails>> getBookDetails(@PathVariable @NotBlank String bookId) {
        Condition condition = Condition.of("$.bookId", "EQUALS", bookId);
        SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", condition);
        return entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, conditionRequest)
                .thenApply(arrayNode -> {
                    if (arrayNode == null || arrayNode.isEmpty()) {
                        BookDetails mock = new BookDetails(
                                "Unknown Title", "Unknown Author", null,
                                "Unknown Genre", null, "No description available",
                                "Unknown Publisher", "N/A"
                        );
                        return ResponseEntity.ok(mock);
                    }
                    ObjectNode item = (ObjectNode) arrayNode.get(0);
                    try {
                        return ResponseEntity.ok(objectMapper.treeToValue(item, BookDetails.class));
                    } catch (Exception e) {
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error mapping book details", e);
                    }
                });
    }

    @GetMapping("/reports/weekly")
    public ResponseEntity<WeeklyReport> getWeeklyReport() {
        // Since this is a simple synchronous endpoint with no async or persistence,
        // no workflow needed here.
        WeeklyReport report = new WeeklyReport(
                Instant.now().minusSeconds(7 * 24 * 60 * 60).toString().substring(0, 10),
                Instant.now().toString().substring(0, 10),
                Arrays.asList(
                        new MostSearchedBook("Example Book 1", 42),
                        new MostSearchedBook("Example Book 2", 27)
                ),
                new UserPreferences(
                        Arrays.asList("Fantasy", "Science Fiction"),
                        Arrays.asList("J.K. Rowling", "Isaac Asimov")
                )
        );
        return ResponseEntity.ok(report);
    }

    @PostMapping("/recommendations")
    public ResponseEntity<RecommendationResponse> getRecommendations(@RequestBody @Valid RecommendationRequest request) {
        // This endpoint has no async persistence, just returns recommendations from local cache.
        // If async enrichment is needed, it should be moved to a different entity workflow.
        List<Recommendation> recs = new ArrayList<>();
        // Suppose user search history is not persisted here, so just return static recs
        recs.add(new Recommendation("Popular Book", "Famous Author", null, "Popular recommendation"));
        return ResponseEntity.ok(new RecommendationResponse(recs));
    }

    @PostMapping("/ingestion/daily")
    public ResponseEntity<IngestionResponse> triggerDailyIngestion() {
        // Move ingestion logic to ingestion entity workflow to run async pre-persistence
        // Here just create ingestion entity with status "processing", workflow will update it asynchronously

        ObjectNode ingestionEntity = objectMapper.createObjectNode();
        ingestionEntity.put("jobId", UUID.randomUUID().toString());
        ingestionEntity.put("status", "processing");
        ingestionEntity.put("startedAt", Instant.now().toString());

        entityService.addItem("IngestionJob", ENTITY_VERSION, ingestionEntity, this::processIngestionJob)
                .exceptionally(ex -> {
                    logger.error("Failed to trigger ingestion job", ex);
                    return null;
                });

        return ResponseEntity.ok(new IngestionResponse("started", "Daily ingestion process triggered"));
    }

    /**
     * Workflow function to process ingestion job asynchronously.
     * It simulates ingestion task, updates status accordingly.
     */
    private CompletableFuture<ObjectNode> processIngestionJob(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(2000); // Simulate ingestion delay
                entity.put("status", "completed");
                entity.put("completedAt", Instant.now().toString());
            } catch (InterruptedException e) {
                entity.put("status", "failed");
                entity.put("errorMessage", e.getMessage());
            }
            return entity;
        });
    }

    // Utility methods to extract text from JsonNode
    private String getFirstText(JsonNode n, String f) {
        return n.hasNonNull(f) ? n.get(f).asText() : null;
    }

    private String getFirstTextFromArray(JsonNode n, String f) {
        if (n.has(f) && n.get(f).isArray() && n.get(f).size() > 0) {
            return n.get(f).get(0).asText();
        }
        return null;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchRequest {
        @NotBlank
        private String query;
        private String genre;
        private Integer publicationYear;
        private String author;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResponse {
        private List<BookSummary> results;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookSummary {
        private String title;
        private String author;
        private String coverImageUrl;
        private String genre;
        private Integer publicationYear;
        private String bookId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookDetails {
        private String title;
        private String author;
        private String coverImageUrl;
        private String genre;
        private Integer publicationYear;
        private String description;
        private String publisher;
        private String isbn;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyReport {
        private String weekStart;
        private String weekEnd;
        private List<MostSearchedBook> mostSearchedBooks;
        private UserPreferences userPreferences;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MostSearchedBook {
        private String title;
        private int searchCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserPreferences {
        private List<String> topGenres;
        private List<String> topAuthors;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationRequest {
        @NotBlank
        private String userId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationResponse {
        private List<Recommendation> recommendations;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recommendation {
        private String title;
        private String author;
        private String coverImageUrl;
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngestionResponse {
        private String status;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ErrorResponse {
        private String error;
        private String message;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }
}
```

---

### Summary of changes and rationale:

- **`processBookEntity`**:
  - Converts the entity to `ObjectNode`, modifies fields (e.g., appends `[Processed]` to title).
  - Adds a supplementary entity `BookAudit` asynchronously to record audit info.
  - Any async enrichment or calls related to the Book entity before persistence should go here.
  - The controller just prepares raw `ObjectNode` entities and calls `addItems` with workflow.

- **`searchBooks` endpoint**:
  - Only fetches external API, filters, transforms results into `ObjectNode` list.
  - Calls `entityService.addItems` passing the workflow `processBookEntity`.
  - Returns DTOs built from the final entities.

- **`triggerDailyIngestion` endpoint**:
  - Instead of sleeping in controller, creates an `IngestionJob` entity with "processing" status.
  - Calls `entityService.addItem` with `processIngestionJob` workflow that asynchronously updates status to "completed" or "failed".
  - This moves fire-and-forget ingestion async task into the workflow function.

- Other endpoints:
  - `getBookDetails` remains similar because it just reads persisted data.
  - `getWeeklyReport` and `getRecommendations` are mostly synchronous, no persistence, so no workflows needed.
  - Exception handling unchanged.

---

### Benefits:

- Controllers are **thin**, only responsible for request/response.
- All async logic & entity mutations happen **before persistence** inside the workflow functions.
- Workflow functions can persist supplementary entities of different types safely.
- No infinite recursion risk because workflow functions cannot mutate the current entity model via `entityService`.
- Async workflows can handle complex enrichment, audit logging, or batch processing transparently.

---

Let me know if you want me to extract workflows into separate dedicated classes or services for better separation!