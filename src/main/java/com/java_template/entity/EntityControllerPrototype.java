```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/prototype/api")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Mock storage for search results keyed by user session or query id (simplified)
    private final Map<String, List<BookSummary>> cachedSearchResults = new ConcurrentHashMap<>();

    // Mock storage for book details, keyed by bookId
    private final Map<String, BookDetails> bookDetailsCache = new ConcurrentHashMap<>();

    // Mock user search history for recommendations keyed by userId
    private final Map<String, List<String>> userSearchHistory = new ConcurrentHashMap<>();

    // Mock job status storage (e.g. ingestion jobs)
    private final Map<String, JobStatus> ingestionJobs = new ConcurrentHashMap<>();

    private static final String OPEN_LIBRARY_SEARCH_API = "https://openlibrary.org/search.json?q={query}";

    @PostConstruct
    public void init() {
        logger.info("EntityControllerPrototype initialized");
    }

    // 1. Search Books - POST /api/books/search
    @PostMapping("/books/search")
    public ResponseEntity<SearchResponse> searchBooks(@RequestBody SearchRequest request) {
        logger.info("Received search request: query='{}' filters={}", request.getQuery(), request.getFilters());
        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query parameter must not be empty");
        }

        try {
            // Build Open Library API URL with query param (encoding handled by RestTemplate)
            Map<String, String> uriVariables = new HashMap<>();
            uriVariables.put("query", request.getQuery());

            String url = OPEN_LIBRARY_SEARCH_API;

            JsonNode root = restTemplate.getForObject(url, JsonNode.class, uriVariables);
            if (root == null || !root.has("docs")) {
                logger.error("Open Library API returned unexpected data structure");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid data from Open Library API");
            }

            List<BookSummary> results = new ArrayList<>();

            for (JsonNode doc : root.get("docs")) {
                // Extract fields safely with fallback
                String title = getFirstText(doc, "title");
                String author = getFirstTextFromArray(doc, "author_name");
                String coverId = doc.has("cover_i") ? doc.get("cover_i").asText() : null;
                String coverImageUrl = coverId != null ? "https://covers.openlibrary.org/b/id/" + coverId + "-M.jpg" : null;
                String genre = getFirstTextFromArray(doc, "subject"); // genre approximated by subject
                Integer pubYear = doc.has("first_publish_year") && !doc.get("first_publish_year").isNull()
                        ? doc.get("first_publish_year").asInt()
                        : null;
                String bookId = doc.has("key") ? doc.get("key").asText() : UUID.randomUUID().toString();

                // Apply filters (simple in-memory filter)
                if (request.getFilters() != null) {
                    if (request.getFilters().getGenre() != null && (genre == null || !genre.toLowerCase().contains(request.getFilters().getGenre().toLowerCase()))) {
                        continue;
                    }
                    if (request.getFilters().getPublicationYear() != null && !request.getFilters().getPublicationYear().equals(pubYear)) {
                        continue;
                    }
                    if (request.getFilters().getAuthor() != null && (author == null || !author.toLowerCase().contains(request.getFilters().getAuthor().toLowerCase()))) {
                        continue;
                    }
                }

                results.add(new BookSummary(title, author, coverImageUrl, genre, pubYear, bookId));
            }

            // Cache results by a simplistic key (could be session/user id or request hash)
            String cacheKey = UUID.randomUUID().toString();
            cachedSearchResults.put(cacheKey, results);

            // Store user search history for recommendations (mock userId = "anonymous" here for prototype)
            userSearchHistory.computeIfAbsent("anonymous", k -> new ArrayList<>()).add(request.getQuery());

            logger.info("Search processed: {} results found", results.size());
            return ResponseEntity.ok(new SearchResponse(results));
        } catch (Exception ex) {
            logger.error("Error during book search", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to search books");
        }
    }

    // 2. Get Book Details - GET /api/books/{bookId}
    @GetMapping("/books/{bookId}")
    public ResponseEntity<BookDetails> getBookDetails(@PathVariable String bookId) {
        logger.info("Fetching details for bookId={}", bookId);
        BookDetails details = bookDetailsCache.get(bookId);
        if (details != null) {
            logger.info("Book details found in cache");
            return ResponseEntity.ok(details);
        }

        // TODO: Implement real external fetch or database retrieval
        // For now, mock response with minimal info
        logger.info("Book details not found in cache, returning mock data");
        BookDetails mockDetails = new BookDetails(
                "Unknown Title",
                "Unknown Author",
                null,
                "Unknown Genre",
                null,
                "No description available",
                "Unknown Publisher",
                "N/A"
        );
        return ResponseEntity.ok(mockDetails);
    }

    // 3. Generate Weekly Report - GET /api/reports/weekly
    @GetMapping("/reports/weekly")
    public ResponseEntity<WeeklyReport> getWeeklyReport() {
        logger.info("Generating weekly report");

        // TODO: Replace with real analytics logic
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

    // 4. Get Personalized Recommendations - POST /api/recommendations
    @PostMapping("/recommendations")
    public ResponseEntity<RecommendationResponse> getRecommendations(@RequestBody RecommendationRequest request) {
        logger.info("Generating recommendations for userId={}", request.getUserId());
        List<String> history = userSearchHistory.getOrDefault(request.getUserId(), Collections.emptyList());

        // TODO: Replace with real recommendation logic
        List<Recommendation> recs = new ArrayList<>();
        for (String query : history) {
            recs.add(new Recommendation(
                    "Recommended Book for " + query,
                    "Some Author",
                    null,
                    "Based on your search for '" + query + "'"
            ));
        }
        if (recs.isEmpty()) {
            recs.add(new Recommendation(
                    "Popular Book",
                    "Famous Author",
                    null,
                    "Popular recommendation"
            ));
        }

        return ResponseEntity.ok(new RecommendationResponse(recs));
    }

    // 5. Data Ingestion Trigger (Scheduled) - POST /api/ingestion/daily
    @PostMapping("/ingestion/daily")
    public ResponseEntity<IngestionResponse> triggerDailyIngestion() {
        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        ingestionJobs.put(jobId, new JobStatus("processing", requestedAt));
        logger.info("Daily ingestion job triggered, jobId={}", jobId);

        // Fire-and-forget ingestion task
        CompletableFuture.runAsync(() -> {
            try {
                // TODO: Implement actual ingestion logic fetching from Open Library and updating DB/cache
                logger.info("Starting ingestion jobId={}", jobId);
                Thread.sleep(2000); // simulate delay
                ingestionJobs.put(jobId, new JobStatus("completed", Instant.now()));
                logger.info("Completed ingestion jobId={}", jobId);
            } catch (InterruptedException e) {
                ingestionJobs.put(jobId, new JobStatus("failed", Instant.now()));
                logger.error("Ingestion job failed jobId={}", jobId, e);
            }
        });

        return ResponseEntity.ok(new IngestionResponse("started", "Daily ingestion process triggered"));
    }

    // Basic error handler for ResponseStatusException
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled exception: {}", ex.getReason(), ex);
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    // Helper methods to safely extract JSON fields
    private String getFirstText(JsonNode node, String fieldName) {
        return node.hasNonNull(fieldName) ? node.get(fieldName).asText() : null;
    }

    private String getFirstTextFromArray(JsonNode node, String fieldName) {
        if (node.has(fieldName) && node.get(fieldName).isArray() && node.get(fieldName).size() > 0) {
            return node.get(fieldName).get(0).asText();
        }
        return null;
    }

    // DTOs and simple model classes

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchRequest {
        private String query;
        private Filters filters;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Filters {
            private String genre;
            private Integer publicationYear;
            private String author;
        }
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
```