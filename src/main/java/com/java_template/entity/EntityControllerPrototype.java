```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String OPEN_LIBRARY_SEARCH_API = "https://openlibrary.org/search.json";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * In-memory DB mocks for prototype:
     * - bookId -> BookDetails
     * - userId -> List of searched bookIds (for recommendations)
     * - searchedBooksCount (for weekly report)
     */
    private final Map<String, BookDetails> bookStore = new ConcurrentHashMap<>();
    private final Map<String, List<String>> userSearchHistory = new ConcurrentHashMap<>();
    private final Map<String, Integer> searchedBooksCount = new ConcurrentHashMap<>();

    /**
     * Simple job status map for ingestion trigger endpoint
     */
    private final Map<String, JobStatus> ingestionJobs = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        logger.info("EntityControllerPrototype initialized");
    }

    // ==== 1. Search Books (POST) ====
    @PostMapping("/books/search")
    public ResponseEntity<SearchResponse> searchBooks(@RequestBody SearchRequest request) {
        logger.info("Received search request: query='{}', filters={}", request.getQuery(), request.getFilters());

        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query must not be empty");
        }

        try {
            // Build Open Library API URL with query and optional filters
            StringBuilder urlBuilder = new StringBuilder(OPEN_LIBRARY_SEARCH_API);
            urlBuilder.append("?q=").append(URLEncoder.encode(request.getQuery(), StandardCharsets.UTF_8));

            // OpenLibrary API does not support genre filter directly, so we'll filter results later
            // For author and year, use 'author' and 'first_publish_year' fields filtering locally

            URI uri = new URI(urlBuilder.toString());
            logger.info("Calling Open Library API: {}", uri);

            String rawResponse = restTemplate.getForObject(uri, String.class);
            if (rawResponse == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Empty response from Open Library");
            }

            JsonNode rootNode = objectMapper.readTree(rawResponse);
            JsonNode docs = rootNode.path("docs");
            if (!docs.isArray()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected Open Library response structure");
            }

            List<BookDetails> filteredBooks = new ArrayList<>();
            for (JsonNode bookNode : docs) {
                String title = bookNode.path("title").asText(null);
                if (title == null) continue;

                // Extract author: try "author_name" array first element
                JsonNode authorsNode = bookNode.path("author_name");
                String author = (authorsNode.isArray() && authorsNode.size() > 0) ? authorsNode.get(0).asText() : null;

                // Extract cover image id and build URL if available
                String coverId = bookNode.path("cover_i").asText(null);
                String coverImage = coverId != null && !coverId.isEmpty()
                        ? "https://covers.openlibrary.org/b/id/" + coverId + "-M.jpg"
                        : null;

                // Extract publication year
                int publicationYear = bookNode.path("first_publish_year").asInt(0);

                // Genre is not provided by Open Library search API; TODO: later enrich this info from another source or skip
                String genre = null;

                // Apply filters locally
                if (request.getFilters() != null) {
                    Filters f = request.getFilters();
                    if (f.getGenre() != null && (genre == null || !genre.equalsIgnoreCase(f.getGenre()))) {
                        continue; // genre filter applied but no match
                    }
                    if (f.getAuthor() != null && (author == null || !author.toLowerCase().contains(f.getAuthor().toLowerCase()))) {
                        continue; // author filter mismatch
                    }
                    if (f.getPublicationYear() != null && publicationYear != f.getPublicationYear()) {
                        continue; // year filter mismatch
                    }
                }

                // Compose a unique bookId - Open Library key or fallback to title+author hash
                String key = bookNode.path("key").asText(null);
                String bookId = key != null ? key.replace("/works/", "") : UUID.nameUUIDFromBytes((title + author).getBytes()).toString();

                BookDetails book = new BookDetails(bookId, title, author, coverImage, genre, publicationYear);
                filteredBooks.add(book);

                // Store/Update in mock DB
                bookStore.put(bookId, book);

                // Increase search count for reporting
                searchedBooksCount.merge(bookId, 1, Integer::sum);
            }

            // Save user search history
            if (request.getUserId() != null && !request.getUserId().isBlank()) {
                userSearchHistory.computeIfAbsent(request.getUserId(), k -> new ArrayList<>())
                        .addAll(filteredBooks.stream().map(BookDetails::getBookId).toList());
            }

            SearchResponse response = new SearchResponse(filteredBooks);
            logger.info("Search returned {} books", filteredBooks.size());
            return ResponseEntity.ok(response);

        } catch (ResponseStatusException ex) {
            logger.error("Error during searchBooks: status={}, message={}", ex.getStatusCode(), ex.getReason());
            throw ex;
        } catch (Exception ex) {
            logger.error("Unexpected error during searchBooks", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to search books");
        }
    }

    // ==== 2. Get Search Results (GET) ====
    @GetMapping("/books/results")
    public ResponseEntity<SearchResponse> getSearchResults(
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) Integer publicationYear,
            @RequestParam(required = false) String author) {

        logger.info("Fetching stored search results with filters - genre: {}, year: {}, author: {}", genre, publicationYear, author);

        List<BookDetails> filtered = new ArrayList<>();
        for (BookDetails book : bookStore.values()) {
            if (genre != null && (book.getGenre() == null || !book.getGenre().equalsIgnoreCase(genre))) {
                continue;
            }
            if (author != null && (book.getAuthor() == null || !book.getAuthor().toLowerCase().contains(author.toLowerCase()))) {
                continue;
            }
            if (publicationYear != null && book.getPublicationYear() != publicationYear) {
                continue;
            }
            filtered.add(book);
        }
        logger.info("Returning {} filtered books", filtered.size());
        return ResponseEntity.ok(new SearchResponse(filtered));
    }

    // ==== 3. Get Weekly Report (GET) ====
    @GetMapping("/reports/weekly")
    public ResponseEntity<WeeklyReportResponse> getWeeklyReport() {
        logger.info("Generating weekly report");

        // TODO: For prototype, aggregate from in-memory searchedBooksCount map, no time filtering
        List<MostSearchedBook> mostSearched = new ArrayList<>();
        searchedBooksCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> {
                    BookDetails b = bookStore.get(e.getKey());
                    if (b != null) {
                        mostSearched.add(new MostSearchedBook(b.getBookId(), b.getTitle(), e.getValue()));
                    }
                });

        // Summarize top genres and authors from user search history - simple frequency count
        Map<String, Integer> genreCount = new HashMap<>();
        Map<String, Integer> authorCount = new HashMap<>();

        for (List<String> userBooks : userSearchHistory.values()) {
            for (String bookId : userBooks) {
                BookDetails b = bookStore.get(bookId);
                if (b == null) continue;
                if (b.getGenre() != null) genreCount.merge(b.getGenre(), 1, Integer::sum);
                if (b.getAuthor() != null) authorCount.merge(b.getAuthor(), 1, Integer::sum);
            }
        }

        List<String> topGenres = genreCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        List<String> topAuthors = authorCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        WeeklyReportResponse report = new WeeklyReportResponse(mostSearched, new UserPreferencesSummary(topGenres, topAuthors));
        return ResponseEntity.ok(report);
    }

    // ==== 4. Get Recommendations (POST) ====
    @PostMapping("/recommendations")
    public ResponseEntity<RecommendationsResponse> getRecommendations(@RequestBody RecommendationRequest request) {
        logger.info("Generating recommendations for userId={}", request.getUserId());
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }

        // TODO: Simple prototype: recommend top searched books excluding already searched by user
        List<String> userBooks = userSearchHistory.getOrDefault(request.getUserId(), Collections.emptyList());
        Set<String> userBooksSet = new HashSet<>(userBooks);

        List<BookDetails> recommendations = new ArrayList<>();
        searchedBooksCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .filter(bookId -> !userBooksSet.contains(bookId))
                .limit(10)
                .forEach(bookId -> {
                    BookDetails b = bookStore.get(bookId);
                    if (b != null) recommendations.add(b);
                });

        return ResponseEntity.ok(new RecommendationsResponse(recommendations));
    }

    // ==== 5. Data Ingestion Trigger (POST) ====
    @PostMapping("/ingestion/trigger")
    public ResponseEntity<IngestionTriggerResponse> triggerDataIngestion() {
        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();

        ingestionJobs.put(jobId, new JobStatus("processing", requestedAt));
        logger.info("Ingestion job {} started at {}", jobId, requestedAt);

        // Fire-and-forget ingestion job async
        CompletableFuture.runAsync(() -> {
            try {
                // TODO: Implement real ingestion logic here (fetch & update books daily)
                logger.info("Ingestion job {} running (mock implementation)", jobId);
                Thread.sleep(2000); // simulate work
                ingestionJobs.put(jobId, new JobStatus("completed", Instant.now()));
                logger.info("Ingestion job {} completed", jobId);
            } catch (Exception e) {
                ingestionJobs.put(jobId, new JobStatus("failed", Instant.now()));
                logger.error("Ingestion job {} failed", jobId, e);
            }
        });

        return ResponseEntity.ok(new IngestionTriggerResponse("started"));
    }

    // ==== Basic error handler ====
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled exception: status={}, message={}", ex.getStatusCode(), ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    // ==== DTOs and Models ====

    @Data
    public static class SearchRequest {
        private String query;
        private Filters filters;
        private String userId; // optional, to track user searches for recommendations
    }

    @Data
    public static class Filters {
        private String genre;
        private Integer publicationYear;
        private String author;
    }

    @Data
    @AllArgsConstructor
    public static class SearchResponse {
        private List<BookDetails> results;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BookDetails {
        private String bookId;
        private String title;
        private String author;
        private String coverImage;
        private String genre;
        private int publicationYear;
    }

    @Data
    public static class RecommendationRequest {
        private String userId;
    }

    @Data
    @AllArgsConstructor
    public static class RecommendationsResponse {
        private List<BookDetails> recommendations;
    }

    @Data
    @AllArgsConstructor
    public static class WeeklyReportResponse {
        private List<MostSearchedBook> mostSearchedBooks;
        private UserPreferencesSummary userPreferencesSummary;
    }

    @Data
    @AllArgsConstructor
    public static class MostSearchedBook {
        private String bookId;
        private String title;
        private int searchCount;
    }

    @Data
    @AllArgsConstructor
    public static class UserPreferencesSummary {
        private List<String> topGenres;
        private List<String> topAuthors;
    }

    @Data
    @AllArgsConstructor
    public static class IngestionTriggerResponse {
        private String status;
    }

    @Data
    @AllArgsConstructor
    public static class JobStatus {
        private String status;
        private Instant timestamp;
    }
}
```
