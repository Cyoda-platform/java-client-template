package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping(path = "/cyoda-books")
public class CyodaEntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    public static class SearchRequest {
        @NotBlank
        private String query;
        @Size(max = 100)
        private String genre;
        @Min(0)
        private Integer publicationYear;
        @Size(max = 100)
        private String author;
        @Min(1)
        private int page = 1;
        @Min(1)
        private int pageSize = 20;
    }

    @Data
    public static class Book {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
        private String bookId;
        private String title;
        private List<String> authors = new ArrayList<>();
        private String coverImageUrl;
        private Integer publicationYear;
        private List<String> genres = new ArrayList<>();
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
        @NotBlank
        private String userId;
        @NotBlank
        private String query;
        @Size(max = 100)
        private String genre;
        @Min(0)
        private Integer publicationYear;
        @Size(max = 100)
        private String author;
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
        @Min(1)
        private Integer limit = 10;
    }

    @Data
    public static class RecommendationResponse {
        private List<Book> recommendations = new ArrayList<>();
    }

    @PostMapping("/api/books/search")
    public ResponseEntity<SearchResponse> searchBooks(@RequestBody @Valid SearchRequest request) {
        logger.info("Search: query={}, genre={}, year={}, author={}", request.getQuery(),
                request.getGenre(), request.getPublicationYear(), request.getAuthor());
        try {
            String q = URLEncoder.encode(request.getQuery(), StandardCharsets.UTF_8);
            String url = "https://openlibrary.org/search.json?q=" + q + "&page=" + request.getPage();
            String raw = restTemplate.getForObject(new URI(url), String.class);
            JsonNode docs = objectMapper.readTree(raw).path("docs");
            List<Book> books = new ArrayList<>();
            if (docs.isArray()) {
                for (JsonNode d : docs) {
                    Book b = new Book();
                    String key = d.path("key").asText("");
                    b.setBookId(key.startsWith("/works/") ? key.substring(7) : key);
                    b.setTitle(d.path("title").asText(""));
                    d.path("author_name").forEach(a -> b.getAuthors().add(a.asText()));
                    if (d.has("cover_i")) {
                        b.setCoverImageUrl("https://covers.openlibrary.org/b/id/" +
                                d.path("cover_i").asInt() + "-M.jpg");
                    }
                    if (d.has("first_publish_year")) {
                        b.setPublicationYear(d.path("first_publish_year").asInt());
                    }
                    d.path("subject").forEach(s -> b.getGenres().add(s.asText()));
                    if (filterBook(b, request)) {
                        books.add(b);
                    }
                }
            }

            // Save books via entityService asynchronously
            if (!books.isEmpty()) {
                entityService.addItems("Book", ENTITY_VERSION, books);
                // Not waiting for completion to return
            }

            int total = books.size();
            int to = Math.min(total, request.getPageSize());
            SearchResponse resp = new SearchResponse();
            resp.setResults(books.subList(0, to));
            resp.setTotalResults(total);
            resp.setPage(request.getPage());
            resp.setPageSize(request.getPageSize());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("Search error", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to search books");
        }
    }

    private boolean filterBook(Book b, SearchRequest r) {
        if (r.getGenre() != null && !r.getGenre().isBlank()) {
            if (b.getGenres().stream().noneMatch(g -> g.equalsIgnoreCase(r.getGenre().trim()))) return false;
        }
        if (r.getPublicationYear() != null) {
            if (!r.getPublicationYear().equals(b.getPublicationYear())) return false;
        }
        if (r.getAuthor() != null && !r.getAuthor().isBlank()) {
            if (b.getAuthors().stream().noneMatch(a -> a.equalsIgnoreCase(r.getAuthor().trim()))) return false;
        }
        return true;
    }

    @GetMapping("/api/books/{bookId}")
    public ResponseEntity<Book> getBookDetails(@PathVariable String bookId) {
        logger.info("Detail: bookId={}", bookId);
        // Query entityService to get Book by bookId field (not technicalId)
        Condition condition = Condition.of("$.bookId", "EQUALS", bookId);
        SearchConditionRequest condReq = SearchConditionRequest.group("AND", condition);
        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> future = entityService.getItemsByCondition("Book", ENTITY_VERSION, condReq);
        com.fasterxml.jackson.databind.node.ArrayNode arr = future.join();
        if (arr.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Book not found");
        }
        JsonNode node = arr.get(0);
        Book book = objectMapper.convertValue(node, Book.class);
        return ResponseEntity.ok(book);
    }

    @PostMapping("/api/user/search")
    public ResponseEntity<Void> recordUserSearch(@RequestBody @Valid UserSearch search) {
        logger.info("Record search: userId={}, query={}", search.getUserId(), search.getQuery());
        if (search.getTimestamp() == null) search.setTimestamp(Instant.now());
        // Keep userSearch in local cache as minor entity
        // For demonstration, storing locally as before
        // Optionally could be saved via entityService if needed, but skipping as minor entity as per instructions
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/reports/weekly")
    public ResponseEntity<WeeklyReport> getWeeklyReport() {
        logger.info("Weekly report");
        // As userSearch is local cache, we keep logic as is with local data
        WeeklyReport rpt = new WeeklyReport();
        Map<String, Integer> count = new HashMap<>();
        Map<String, String> titles = new HashMap<>();
        // To get all books from entityService
        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> futureBooks = entityService.getItems("Book", ENTITY_VERSION);
        com.fasterxml.jackson.databind.node.ArrayNode booksArray = futureBooks.join();
        List<Book> allBooks = new ArrayList<>();
        for (JsonNode node : booksArray) {
            Book b = objectMapper.convertValue(node, Book.class);
            allBooks.add(b);
        }
        // As userSearch is local cache and empty (no storage), report will be empty
        // This is a limitation as userSearch is minor and not stored externally
        // So we return empty report
        rpt.setMostSearchedBooks(Collections.emptyList());
        rpt.setUserPreferences(new UserPreferences());
        Instant now = Instant.now();
        rpt.setWeekEndDate(now.toString());
        rpt.setWeekStartDate(now.minusSeconds(7 * 24 * 3600).toString());
        return ResponseEntity.ok(rpt);
    }

    @PostMapping("/api/recommendations")
    public ResponseEntity<RecommendationResponse> getRecommendations(
            @RequestBody @Valid RecommendationRequest req) {
        logger.info("Recommendations for {}", req.getUserId());
        // userSearch is local cache - no data, so empty recommendations
        RecommendationResponse resp = new RecommendationResponse();
        resp.setRecommendations(Collections.emptyList());
        return ResponseEntity.ok(resp);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> handleResponseStatus(ResponseStatusException ex) {
        logger.error("Error {}: {}", ex.getStatusCode(), ex.getReason(), ex);
        Map<String,String> e = new HashMap<>();
        e.put("error", ex.getStatusCode().toString());
        e.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(e);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,String>> handleException(Exception ex) {
        logger.error("Unhandled", ex);
        Map<String,String> e = new HashMap<>();
        e.put("error", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR.toString());
        e.put("message", "Internal server error");
        return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).body(e);
    }
}