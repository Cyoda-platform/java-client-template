```java
package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping(path = "/prototype")
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory mock DB for books and user searches
    private final Map<String, Book> bookDb = new ConcurrentHashMap<>();
    private final Map<String, List<UserSearch>> userSearchDb = new ConcurrentHashMap<>();

    // Mock in-memory job store for async ingestion/report generation - optional extension
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    // ========== DTOs ==========

    @Data
    public static class SearchRequest {
        @NotBlank
        private String query;
        private Filters filters = new Filters();
        private Integer page = 1;
        private Integer pageSize = 20;
    }

    @Data
    public static class Filters {
        private String genre;
        private Integer publicationYear;
        private String author;
    }

    @Data
    public static class Book {
        private String bookId;
        private String title;
        private List<String> authors = new ArrayList<>();
        private String coverImageUrl;
        private Integer publicationYear;
        private List<String> genres = new ArrayList<>();
        // Additional fields for detail:
        private String description;
        private String publisher;
        private String isbn;
    }

    @Data
    public static class SearchResponse {
        private List<Book> results = new ArrayList<>();
        private int totalResults;
        private int page;
        private int pageSize;
    }

    @Data
    public static class UserSearch {
        private String userId;
        private String query;
        private Filters filters = new Filters();
        private Instant timestamp;
    }

    @Data
    public static class WeeklyReport {
        private List<MostSearchedBook> mostSearchedBooks = new ArrayList<>();
        private UserPreferences userPreferences = new UserPreferences();
        private String weekStartDate;
        private String weekEndDate;
    }

    @Data
    public static class MostSearchedBook {
        private String bookId;
        private String title;
        private int searchCount;
    }

    @Data
    public static class UserPreferences {
        private List<String> topGenres = new ArrayList<>();
        private List<String> topAuthors = new ArrayList<>();
    }

    @Data
    public static class RecommendationRequest {
        @NotBlank
        private String userId;
        private Integer limit = 10;
    }

    @Data
    public static class RecommendationResponse {
        private List<Book> recommendations = new ArrayList<>();
    }

    @Data
    public static class JobStatus {
        private String status;
        private Instant requestedAt;
    }

    // ========== API Endpoints ==========

    /**
     * POST /prototype/api/books/search
     * Search books via Open Library API + local filtering.
     */
    @PostMapping("/api/books/search")
    public ResponseEntity<SearchResponse> searchBooks(@RequestBody SearchRequest request) {
        logger.info("Search request received: query='{}', filters={}, page={}, pageSize={}",
                request.getQuery(), request.getFilters(), request.getPage(), request.getPageSize());

        try {
            // Encode query for URL
            String encodedQuery = URLEncoder.encode(request.getQuery(), StandardCharsets.UTF_8);

            // Build Open Library API URL (search.json?q=...)
            String url = "https://openlibrary.org/search.json?q=" + encodedQuery
                    + "&page=" + request.getPage();

            // Fetch raw JSON from Open Library (no POST endpoint, so GET here is acceptable as external call)
            String rawResponse = restTemplate.getForObject(new URI(url), String.class);

            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode docs = root.path("docs");

            List<Book> books = new ArrayList<>();

            if (docs.isArray()) {
                for (JsonNode doc : docs) {
                    Book book = new Book();

                    String key = doc.path("key").asText("");
                    // key example: "/works/OL262758W"
                    book.setBookId(key.startsWith("/works/") ? key.substring(7) : key);

                    book.setTitle(doc.path("title").asText(""));

                    // authors (array of names)
                    List<String> authors = new ArrayList<>();
                    JsonNode authorNode = doc.path("author_name");
                    if (authorNode.isArray()) {
                        for (JsonNode a : authorNode) {
                            authors.add(a.asText());
                        }
                    }
                    book.setAuthors(authors);

                    // cover image URL (Open Library cover API)
                    JsonNode coverIdNode = doc.path("cover_i");
                    if (coverIdNode.isInt()) {
                        book.setCoverImageUrl("https://covers.openlibrary.org/b/id/" + coverIdNode.asInt() + "-M.jpg");
                    }

                    // publication year (first_publish_year)
                    if (doc.has("first_publish_year")) {
                        book.setPublicationYear(doc.path("first_publish_year").asInt());
                    }

                    // genres - Open Library does not provide genres directly here, so leave empty or use subject
                    List<String> genres = new ArrayList<>();
                    JsonNode subjectNode = doc.path("subject");
                    if (subjectNode.isArray()) {
                        for (JsonNode s : subjectNode) {
                            genres.add(s.asText());
                        }
                    }
                    book.setGenres(genres);

                    // Apply local filters (genre, publicationYear, author)
                    if (filterBook(book, request.getFilters())) {
                        books.add(book);

                        // Save/update in local mock DB for demo purposes
                        bookDb.put(book.getBookId(), book);
                    }
                }
            }

            // Prepare response with pagination (simple slice for safety)
            int fromIndex = 0;
            int toIndex = Math.min(books.size(), request.getPageSize());
            List<Book> pageResults = books.subList(fromIndex, toIndex);

            SearchResponse response = new SearchResponse();
            response.setResults(pageResults);
            response.setTotalResults(books.size());
            response.setPage(request.getPage());
            response.setPageSize(request.getPageSize());

            logger.info("Returning {} results (total {}) for query '{}'", pageResults.size(), books.size(), request.getQuery());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error during book search", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to search books");
        }
    }

    private boolean filterBook(Book book, Filters filters) {
        if (filters == null) return true;

        if (filters.getGenre() != null && !filters.getGenre().isBlank()) {
            boolean foundGenre = book.getGenres().stream()
                    .anyMatch(g -> g.equalsIgnoreCase(filters.getGenre().trim()));
            if (!foundGenre) return false;
        }

        if (filters.getPublicationYear() != null) {
            if (!filters.getPublicationYear().equals(book.getPublicationYear())) return false;
        }

        if (filters.getAuthor() != null && !filters.getAuthor().isBlank()) {
            boolean foundAuthor = book.getAuthors().stream()
                    .anyMatch(a -> a.equalsIgnoreCase(filters.getAuthor().trim()));
            if (!foundAuthor) return false;
        }
        return true;
    }

    /**
     * GET /prototype/api/books/{bookId}
     * Return book details from local "database".
     */
    @GetMapping("/api/books/{bookId}")
    public ResponseEntity<Book> getBookDetails(@PathVariable String bookId) {
        logger.info("Get book details for bookId={}", bookId);
        Book book = bookDb.get(bookId);
        if (book == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found");
        }
        return ResponseEntity.ok(book);
    }

    /**
     * POST /prototype/api/user/search
     * Record user search activity.
     */
    @PostMapping("/api/user/search")
    public ResponseEntity<Void> recordUserSearch(@RequestBody UserSearch search) {
        logger.info("Recording user search for userId={}, query='{}'", search.getUserId(), search.getQuery());

        if (search.getTimestamp() == null) {
            search.setTimestamp(Instant.now());
        }

        userSearchDb.computeIfAbsent(search.getUserId(), k -> new ArrayList<>()).add(search);

        // TODO: Fire-and-forget async processing to update reports/recommendations if needed
        CompletableFuture.runAsync(() -> {
            logger.info("Async processing user search record for userId={}", search.getUserId());
            // placeholder for actual processing logic
        });

        return ResponseEntity.ok().build();
    }

    /**
     * GET /prototype/api/reports/weekly
     * Return mock weekly report based on user search data.
     */
    @GetMapping("/api/reports/weekly")
    public ResponseEntity<WeeklyReport> getWeeklyReport() {
        logger.info("Generating weekly report");

        WeeklyReport report = new WeeklyReport();

        // Mock: Aggregate most searched books across all users
        Map<String, Integer> bookSearchCount = new HashMap<>();
        Map<String, String> bookTitleMap = new HashMap<>();

        userSearchDb.values().forEach(searches -> {
            for (UserSearch s : searches) {
                // We only have query text here, so match by title substring (mock)
                bookDb.values().forEach(book -> {
                    if (book.getTitle().toLowerCase().contains(s.getQuery().toLowerCase())) {
                        bookSearchCount.merge(book.getBookId(), 1, Integer::sum);
                        bookTitleMap.putIfAbsent(book.getBookId(), book.getTitle());
                    }
                });
            }
        });

        List<MostSearchedBook> mostSearchedBooks = new ArrayList<>();
        bookSearchCount.forEach((bookId, count) -> {
            MostSearchedBook msb = new MostSearchedBook();
            msb.setBookId(bookId);
            msb.setTitle(bookTitleMap.getOrDefault(bookId, "Unknown"));
            msb.setSearchCount(count);
            mostSearchedBooks.add(msb);
        });

        mostSearchedBooks.sort(Comparator.comparingInt(MostSearchedBook::getSearchCount).reversed());

        report.setMostSearchedBooks(mostSearchedBooks.size() > 10 ? mostSearchedBooks.subList(0, 10) : mostSearchedBooks);

        // Mock user preferences aggregate (top genres/authors from all user searches)
        Map<String, Integer> genreCount = new HashMap<>();
        Map<String, Integer> authorCount = new HashMap<>();

        userSearchDb.values().forEach(searches -> {
            for (UserSearch s : searches) {
                Filters f = s.getFilters();
                if (f != null) {
                    if (f.getGenre() != null) {
                        genreCount.merge(f.getGenre(), 1, Integer::sum);
                    }
                    if (f.getAuthor() != null) {
                        authorCount.merge(f.getAuthor(), 1, Integer::sum);
                    }
                }
            }
        });

        UserPreferences prefs = new UserPreferences();
        prefs.setTopGenres(topNKeys(genreCount, 5));
        prefs.setTopAuthors(topNKeys(authorCount, 5));
        report.setUserPreferences(prefs);

        // Set week dates as last 7 days ISO strings (mock)
        Instant now = Instant.now();
        report.setWeekEndDate(now.toString());
        report.setWeekStartDate(now.minusSeconds(7 * 24 * 3600).toString());

        return ResponseEntity.ok(report);
    }

    private List<String> topNKeys(Map<String, Integer> map, int n) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * POST /prototype/api/recommendations
     * Provide simple heuristic recommendations based on user previous searches.
     */
    @PostMapping("/api/recommendations")
    public ResponseEntity<RecommendationResponse> getRecommendations(@RequestBody RecommendationRequest request) {
        logger.info("Generating recommendations for userId={}", request.getUserId());

        List<UserSearch> searches = userSearchDb.getOrDefault(request.getUserId(), Collections.emptyList());

        // Simple heuristic: recommend books authored by authors user searched for most
        Map<String, Integer> authorFreq = new HashMap<>();
        for (UserSearch search : searches) {
            Filters f = search.getFilters();
            if (f != null && f.getAuthor() != null) {
                authorFreq.merge(f.getAuthor(), 1, Integer::sum);
            }
        }

        // Get top authors
        List<String> topAuthors = authorFreq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        List<Book> recommendations = new ArrayList<>();

        for (Book book : bookDb.values()) {
            for (String author : book.getAuthors()) {
                if (topAuthors.contains(author)) {
                    recommendations.add(book);
                    break;
                }
            }
            if (recommendations.size() >= (request.getLimit() != null ? request.getLimit() : 10)) {
                break;
            }
        }

        RecommendationResponse response = new RecommendationResponse();
        response.setRecommendations(recommendations);

        return ResponseEntity.ok(response);
    }

    // ========== Basic error handling ==========

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled error: {} - {}", ex.getStatusCode(), ex.getReason(), ex);

        Map<String, String> err = new HashMap<>();
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(err);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception", ex);
        Map<String, String> err = new HashMap<>();
        err.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        err.put("message", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
    }
}
```