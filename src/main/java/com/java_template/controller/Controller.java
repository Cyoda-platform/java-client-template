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

    // Workflow function for processing BookEntity asynchronously before persistence
    private CompletableFuture<ObjectNode> processBookEntity(ObjectNode entity) {
        // Defensive null checks and modifications
        if (entity == null) return CompletableFuture.completedFuture(null);

        // Append "[Processed]" to title if not present
        if (entity.hasNonNull("title")) {
            String title = entity.get("title").asText();
            if (!title.contains("[Processed]")) {
                entity.put("title", title + " [Processed]");
            }
        }

        // Add a supplementary audit entity asynchronously (different entityModel)
        try {
            ObjectNode auditEntity = objectMapper.createObjectNode();
            String bookId = entity.path("bookId").asText(UUID.randomUUID().toString());
            auditEntity.put("bookId", bookId);
            auditEntity.put("processedAt", Instant.now().toString());
            auditEntity.put("processedBy", "processBookEntityWorkflow");
            auditEntity.put("originalTitle", entity.path("title").asText());
            // We do not wait for completion, fire and forget
            entityService.addItem("BookAudit", ENTITY_VERSION, auditEntity);
        } catch (Exception e) {
            logger.error("Failed to create audit entity in processBookEntity", e);
        }

        // Potential place to add async enrichment if needed (e.g. external API call)
        // For now, synchronous return
        return CompletableFuture.completedFuture(entity);
    }

    // Workflow function for processing ingestion jobs asynchronously before persistence
    private CompletableFuture<ObjectNode> processIngestionJob(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (entity == null) return null;
            try {
                // Simulate ingestion work
                Thread.sleep(2000);
                entity.put("status", "completed");
                entity.put("completedAt", Instant.now().toString());
            } catch (InterruptedException e) {
                entity.put("status", "failed");
                entity.put("errorMessage", e.getMessage());
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                entity.put("status", "failed");
                entity.put("errorMessage", e.getMessage());
            }
            return entity;
        });
    }

    @PostMapping("/books/search")
    public CompletableFuture<ResponseEntity<SearchResponse>> searchBooks(@RequestBody @Valid SearchRequest request) {
        logger.info("Received search request: query='{}' genre='{}' year='{}' author='{}'",
                request.getQuery(), request.getGenre(), request.getPublicationYear(), request.getAuthor());

        Map<String, String> uriVars = Map.of("query", request.getQuery());
        JsonNode root = null;
        try {
            root = restTemplate.getForObject(OPEN_LIBRARY_SEARCH_API, JsonNode.class, uriVars);
        } catch (Exception ex) {
            logger.error("Error calling OpenLibrary API", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch data from Open Library API");
        }

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
            Integer pubYear = (doc.has("first_publish_year") && !doc.get("first_publish_year").isNull())
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

            if (title == null || title.isBlank()) continue; // skip invalid

            bookNode.put("title", title);
            bookNode.put("author", author != null ? author : "");
            if (coverImageUrl != null) {
                bookNode.put("coverImageUrl", coverImageUrl);
            }
            bookNode.put("genre", genre != null ? genre : "");
            if (pubYear != null) {
                bookNode.put("publicationYear", pubYear);
            }
            bookNode.put("bookId", bookId);

            filteredResults.add(bookNode);
        }

        // Persist entities without workflow function
        return entityService.addItems(ENTITY_NAME, ENTITY_VERSION, filteredResults)
                .thenApply(ids -> {
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
                        logger.error("Error converting book details", e);
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error mapping book details", e);
                    }
                });
    }

    @GetMapping("/reports/weekly")
    public ResponseEntity<WeeklyReport> getWeeklyReport() {
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
        // For demonstration, static recommendations; in real scenario, async enrichment can be added
        List<Recommendation> recs = new ArrayList<>();
        recs.add(new Recommendation("Popular Book", "Famous Author", null, "Popular recommendation"));
        return ResponseEntity.ok(new RecommendationResponse(recs));
    }

    @PostMapping("/ingestion/daily")
    public ResponseEntity<IngestionResponse> triggerDailyIngestion() {
        ObjectNode ingestionEntity = objectMapper.createObjectNode();
        String jobId = UUID.randomUUID().toString();
        ingestionEntity.put("jobId", jobId);
        ingestionEntity.put("status", "processing");
        ingestionEntity.put("startedAt", Instant.now().toString());

        entityService.addItem("IngestionJob", ENTITY_VERSION, ingestionEntity)
                .exceptionally(ex -> {
                    logger.error("Failed to trigger ingestion job", ex);
                    return null;
                });

        return ResponseEntity.ok(new IngestionResponse("started", "Daily ingestion process triggered"));
    }

    // Utility methods to extract text from JsonNode safely
    private String getFirstText(JsonNode n, String f) {
        if (n == null || f == null) return null;
        return n.hasNonNull(f) ? n.get(f).asText() : null;
    }

    private String getFirstTextFromArray(JsonNode n, String f) {
        if (n == null || f == null) return null;
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
