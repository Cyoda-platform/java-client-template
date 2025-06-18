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
import java.util.concurrent.ConcurrentHashMap;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda/entity")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EntityService entityService;

    private final Map<String, List<String>> userSearchHistory = new ConcurrentHashMap<>();
    private final Map<String, JobStatus> ingestionJobs = new ConcurrentHashMap<>();

    private static final String OPEN_LIBRARY_SEARCH_API = "https://openlibrary.org/search.json?q={query}";
    private static final String ENTITY_NAME = "BookEntity";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
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
        List<BookSummary> filteredResults = new ArrayList<>();
        for (JsonNode doc : root.get("docs")) {
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
            filteredResults.add(new BookSummary(title, author, coverImageUrl, genre, pubYear, bookId));
        }
        String cacheKey = UUID.randomUUID().toString();
        userSearchHistory.computeIfAbsent("anonymous", k -> new ArrayList<>()).add(request.getQuery());

        // Replace local cache with entityService calls - addItems
        return entityService.addItems(ENTITY_NAME, ENTITY_VERSION, filteredResults)
                .thenApply(ids -> ResponseEntity.ok(new SearchResponse(filteredResults)));
    }

    @GetMapping("/books/{bookId}")
    public CompletableFuture<ResponseEntity<BookDetails>> getBookDetails(@PathVariable @NotBlank String bookId) {
        // Build condition to filter by bookId (which matches bookId field in BookSummary)
        Condition condition = Condition.of("$.bookId", "EQUALS", bookId);
        SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", condition);
        return entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, conditionRequest)
                .thenApply(arrayNode -> {
                    if (arrayNode == null || arrayNode.isEmpty()) {
                        // Return mock
                        BookDetails mock = new BookDetails(
                                "Unknown Title", "Unknown Author", null,
                                "Unknown Genre", null, "No description available",
                                "Unknown Publisher", "N/A"
                        );
                        return ResponseEntity.ok(mock);
                    }
                    // Extract first item and convert to BookDetails
                    ObjectNode item = (ObjectNode) arrayNode.get(0);
                    BookDetails details = objectMapper.convertValue(item, BookDetails.class);
                    return ResponseEntity.ok(details);
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
        List<String> history = userSearchHistory.getOrDefault(request.getUserId(), Collections.emptyList());
        List<Recommendation> recs = new ArrayList<>();
        for (String q : history) {
            recs.add(new Recommendation("Recommended Book for " + q, "Some Author", null,
                    "Based on your search for '" + q + "'"));
        }
        if (recs.isEmpty()) {
            recs.add(new Recommendation("Popular Book", "Famous Author", null, "Popular recommendation"));
        }
        return ResponseEntity.ok(new RecommendationResponse(recs));
    }

    @PostMapping("/ingestion/daily")
    public ResponseEntity<IngestionResponse> triggerDailyIngestion() {
        String jobId = UUID.randomUUID().toString();
        ingestionJobs.put(jobId, new JobStatus("processing", Instant.now()));
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2000);
                ingestionJobs.put(jobId, new JobStatus("completed", Instant.now()));
            } catch (InterruptedException e) {
                ingestionJobs.put(jobId, new JobStatus("failed", Instant.now()));
            }
        });
        return ResponseEntity.ok(new IngestionResponse("started", "Daily ingestion process triggered"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

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
    private static class JobStatus {
        private String status;
        private Instant timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ErrorResponse {
        private String error;
        private String message;
    }
}