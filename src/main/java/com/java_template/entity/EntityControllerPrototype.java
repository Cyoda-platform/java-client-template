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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory mock DB for books, key = openLibraryId
    private final Map<String, Book> bookDb = new ConcurrentHashMap<>();

    // Mock in-memory user search history: userId -> List of openLibraryIds searched
    private final Map<String, List<String>> userSearchHistory = new ConcurrentHashMap<>();

    // Mock job store for weekly report generation
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    /**
     * POST /api/books/search
     * Search books via Open Library API with filters.
     */
    @PostMapping("/books/search")
    public ResponseEntity<SearchResponse> searchBooks(@RequestBody SearchRequest request) {
        logger.info("Search request received: query='{}', filters={}, page={}, pageSize={}",
                request.getQuery(), request.getFilters(), request.getPage(), request.getPageSize());

        try {
            // Build Open Library Search API URL
            StringBuilder url = new StringBuilder("https://openlibrary.org/search.json?");
            if (request.getQuery() != null && !request.getQuery().isEmpty()) {
                url.append("q=").append(URLEncoder.encode(request.getQuery(), StandardCharsets.UTF_8));
            } else {
                url.append("q=*"); // fallback to all
            }

            // Open Library supports filtering by author, subject (genre), and publish_year
            // Apply filters as AND conditions by appending &author=...&subject=...&publish_year=...
            if (request.getFilters() != null) {
                if (request.getFilters().getAuthor() != null && !request.getFilters().getAuthor().isEmpty()) {
                    for (String author : request.getFilters().getAuthor()) {
                        url.append("&author=").append(URLEncoder.encode(author, StandardCharsets.UTF_8));
                    }
                }
                if (request.getFilters().getGenre() != null && !request.getFilters().getGenre().isEmpty()) {
                    for (String genre : request.getFilters().getGenre()) {
                        url.append("&subject=").append(URLEncoder.encode(genre, StandardCharsets.UTF_8));
                    }
                }
                if (request.getFilters().getPublicationYear() != null) {
                    Integer from = request.getFilters().getPublicationYear().getFrom();
                    Integer to = request.getFilters().getPublicationYear().getTo();
                    if (from != null) {
                        url.append("&publish_year>").append(from);
                    }
                    if (to != null) {
                        url.append("&publish_year<").append(to);
                    }
                }
            }

            // Pagination
            int page = request.getPage() != null && request.getPage() > 0 ? request.getPage() : 1;
            int limit = request.getPageSize() != null && request.getPageSize() > 0 ? request.getPageSize() : 20;
            url.append("&page=").append(page);
            url.append("&limit=").append(limit);

            logger.info("Calling Open Library API: {}", url);
            String responseStr = restTemplate.getForObject(new URI(url.toString()), String.class);
            JsonNode root = objectMapper.readTree(responseStr);

            int numFound = root.path("numFound").asInt(0);
            JsonNode docs = root.path("docs");

            List<Book> results = new ArrayList<>();
            for (JsonNode doc : docs) {
                String title = doc.path("title").asText(null);
                List<String> authors = new ArrayList<>();
                if (doc.has("author_name")) {
                    for (JsonNode authNode : doc.path("author_name")) {
                        authors.add(authNode.asText());
                    }
                }
                List<String> subjects = new ArrayList<>();
                if (doc.has("subject")) {
                    for (JsonNode subjNode : doc.path("subject")) {
                        subjects.add(subjNode.asText());
                    }
                }
                int publishYear = doc.has("first_publish_year") ? doc.path("first_publish_year").asInt(0) : 0;

                String coverId = doc.has("cover_i") ? doc.path("cover_i").asText("") : "";
                String coverImageUrl = coverId.isEmpty()
                        ? null
                        : "https://covers.openlibrary.org/b/id/" + coverId + "-M.jpg";

                String key = doc.path("key").asText(null); // e.g. "/works/OL12345W"
                String openLibraryId = key != null ? key.replace("/works/", "") : null;

                if (title != null && openLibraryId != null) {
                    Book book = new Book(title, authors, coverImageUrl, subjects, publishYear, openLibraryId);
                    results.add(book);

                    // Update local DB asynchronously - TODO: replace with proper persistence
                    bookDb.put(openLibraryId, book);
                }
            }

            SearchResponse searchResponse = new SearchResponse(numFound, results);

            return ResponseEntity.ok(searchResponse);
        } catch (Exception e) {
            logger.error("Error during book search", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to search books");
        }
    }

    /**
     * GET /api/books/{openLibraryId}
     * Retrieve stored book details.
     */
    @GetMapping("/books/{openLibraryId}")
    public ResponseEntity<Book> getBookDetails(@PathVariable String openLibraryId) {
        logger.info("Get book details for id: {}", openLibraryId);
        Book book = bookDb.get(openLibraryId);
        if (book == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found");
        }
        return ResponseEntity.ok(book);
    }

    /**
     * GET /api/filters
     * Return available genres, authors, and publication years.
     * For prototype, return mock data from current DB.
     */
    @GetMapping("/filters")
    public ResponseEntity<FiltersResponse> getFilters() {
        logger.info("Get available filters");

        Set<String> genres = new HashSet<>();
        Set<String> authors = new HashSet<>();
        Set<Integer> years = new HashSet<>();

        for (Book book : bookDb.values()) {
            if (book.getGenres() != null) genres.addAll(book.getGenres());
            if (book.getAuthors() != null) authors.addAll(book.getAuthors());
            if (book.getPublicationYear() != 0) years.add(book.getPublicationYear());
        }

        List<Integer> sortedYears = new ArrayList<>(years);
        Collections.sort(sortedYears);

        FiltersResponse response = new FiltersResponse(
                new ArrayList<>(genres),
                new ArrayList<>(authors),
                sortedYears
        );

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/reports/weekly
     * Trigger weekly report generation (mocked).
     */
    @PostMapping("/reports/weekly")
    public ResponseEntity<JobStatus> generateWeeklyReport(@RequestBody ReportRequest request) {
        logger.info("Trigger weekly report generation for week starting {}", request.getWeekStartDate());

        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();

        JobStatus jobStatus = new JobStatus("processing", requestedAt);
        entityJobs.put(jobId, jobStatus);

        // Fire-and-forget async processing
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Started processing weekly report job {}", jobId);
                Thread.sleep(3000); // simulate long running task

                // TODO: Implement actual aggregation logic here.

                entityJobs.put(jobId, new JobStatus("completed", Instant.now()));
                logger.info("Completed weekly report job {}", jobId);
            } catch (InterruptedException e) {
                logger.error("Weekly report job interrupted", e);
                entityJobs.put(jobId, new JobStatus("failed", Instant.now()));
            }
        });

        return ResponseEntity.ok(new JobStatusResponse(jobId, jobStatus.getStatus(), jobStatus.getTimestamp()));
    }

    /**
     * GET /api/reports/weekly/{reportId}
     * Retrieve weekly report (mocked).
     */
    @GetMapping("/reports/weekly/{reportId}")
    public ResponseEntity<WeeklyReport> getWeeklyReport(@PathVariable String reportId) {
        logger.info("Get weekly report with id {}", reportId);
        JobStatus jobStatus = entityJobs.get(reportId);
        if (jobStatus == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report job not found");
        }
        if (!"completed".equals(jobStatus.getStatus())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(null);
        }

        // TODO: Replace with real report data
        WeeklyReport report = new WeeklyReport(
                List.of(new BookSearchCount("Sample Book", 42)),
                new UserPreferences(List.of("Fantasy", "Science Fiction"), List.of("J. K. Rowling", "Isaac Asimov"))
        );

        return ResponseEntity.ok(report);
    }

    /**
     * POST /api/recommendations
     * Provide personalized recommendations based on user search history.
     */
    @PostMapping("/recommendations")
    public ResponseEntity<RecommendationsResponse> getRecommendations(@RequestBody RecommendationRequest request) {
        logger.info("Get recommendations for user {}", request.getUserId());

        List<String> searchedBookIds = userSearchHistory.getOrDefault(request.getUserId(), Collections.emptyList());

        // TODO: Replace with real recommendation logic
        Set<Book> recommended = new HashSet<>();
        for (String bookId : searchedBookIds) {
            Book b = bookDb.get(bookId);
            if (b != null) {
                // Add books with same genre as recommendations
                if (b.getGenres() != null) {
                    for (Book candidate : bookDb.values()) {
                        if (!candidate.getOpenLibraryId().equals(bookId) &&
                                candidate.getGenres() != null &&
                                candidate.getGenres().stream().anyMatch(g -> b.getGenres().contains(g))) {
                            recommended.add(candidate);
                            if (recommended.size() >= request.getLimit()) break;
                        }
                    }
                }
            }
            if (recommended.size() >= request.getLimit()) break;
        }

        List<Book> recList = new ArrayList<>(recommended);
        if (recList.size() > request.getLimit()) {
            recList = recList.subList(0, request.getLimit());
        }

        return ResponseEntity.ok(new RecommendationsResponse(recList));
    }

    // --- Exception Handling ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException e) {
        logger.error("API error: {}", e.getReason());
        Map<String, String> error = Map.of(
                "error", e.getReason(),
                "status", String.valueOf(e.getStatusCode().value())
        );
        return new ResponseEntity<>(error, e.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception e) {
        logger.error("Unexpected error", e);
        Map<String, String> error = Map.of(
                "error", "Internal Server Error",
                "status", "500"
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // --- Data Classes ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchRequest {
        private String query;
        private Filters filters;
        private Integer page;
        private Integer pageSize;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Filters {
        private List<String> genre;
        private PubYearRange publicationYear;
        private List<String> author;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PubYearRange {
        private Integer from;
        private Integer to;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResponse {
        private int totalResults;
        private List<Book> results;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Book {
        private String title;
        private List<String> authors;
        private String coverImageUrl;
        private List<String> genres;
        private int publicationYear;
        private String openLibraryId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FiltersResponse {
        private List<String> genres;
        private List<String> authors;
        private List<Integer> publicationYears;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportRequest {
        private String weekStartDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobStatus {
        private String status;
        private Instant timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobStatusResponse {
        private String reportId;
        private String status;
        private Instant generatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyReport {
        private List<BookSearchCount> mostSearchedBooks;
        private UserPreferences userPreferences;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookSearchCount {
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
        private int limit;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationsResponse {
        private List<Book> recommendations;
    }
}
```
