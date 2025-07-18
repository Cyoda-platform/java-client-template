package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping(path = "/prototype")
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Book> bookDb = new ConcurrentHashMap<>();
    private final Map<String, List<UserSearch>> userSearchDb = new ConcurrentHashMap<>();

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
                        bookDb.put(b.getBookId(), b);
                    }
                }
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
        Book b = bookDb.get(bookId);
        if (b == null) throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                "Book not found");
        return ResponseEntity.ok(b);
    }

    @PostMapping("/api/user/search")
    public ResponseEntity<Void> recordUserSearch(@RequestBody @Valid UserSearch search) {
        logger.info("Record search: userId={}, query={}", search.getUserId(), search.getQuery());
        if (search.getTimestamp() == null) search.setTimestamp(Instant.now());
        userSearchDb.computeIfAbsent(search.getUserId(), k -> new ArrayList<>()).add(search);
        CompletableFuture.runAsync(() -> {
            logger.info("Async record for {}", search.getUserId());
            // TODO: process for reports/recommendations
        });
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/reports/weekly")
    public ResponseEntity<WeeklyReport> getWeeklyReport() {
        logger.info("Weekly report");
        WeeklyReport rpt = new WeeklyReport();
        Map<String, Integer> count = new HashMap<>();
        Map<String, String> titles = new HashMap<>();
        userSearchDb.values().forEach(list -> list.forEach(s -> bookDb.values().forEach(b -> {
            if (b.getTitle().toLowerCase().contains(s.getQuery().toLowerCase())) {
                count.merge(b.getBookId(), 1, Integer::sum);
                titles.putIfAbsent(b.getBookId(), b.getTitle());
            }
        })));
        List<MostSearchedBook> ms = new ArrayList<>();
        count.forEach((id, c) -> {
            MostSearchedBook m = new MostSearchedBook();
            m.setBookId(id);
            m.setTitle(titles.get(id));
            m.setSearchCount(c);
            ms.add(m);
        });
        ms.sort(Comparator.comparingInt(MostSearchedBook::getSearchCount).reversed());
        rpt.setMostSearchedBooks(ms.size() > 10 ? ms.subList(0, 10) : ms);
        Map<String, Integer> gCount = new HashMap<>();
        Map<String, Integer> aCount = new HashMap<>();
        userSearchDb.values().forEach(list -> list.forEach(s -> {
            if (s.getGenre() != null) gCount.merge(s.getGenre(), 1, Integer::sum);
            if (s.getAuthor() != null) aCount.merge(s.getAuthor(), 1, Integer::sum);
        }));
        UserPreferences up = new UserPreferences();
        up.setTopGenres(topN(gCount, 5));
        up.setTopAuthors(topN(aCount, 5));
        rpt.setUserPreferences(up);
        Instant now = Instant.now();
        rpt.setWeekEndDate(now.toString());
        rpt.setWeekStartDate(now.minusSeconds(7*24*3600).toString());
        return ResponseEntity.ok(rpt);
    }

    private List<String> topN(Map<String, Integer> m, int n) {
        return m.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed())
                .limit(n).map(Map.Entry::getKey).toList();
    }

    @PostMapping("/api/recommendations")
    public ResponseEntity<RecommendationResponse> getRecommendations(
            @RequestBody @Valid RecommendationRequest req) {
        logger.info("Recommendations for {}", req.getUserId());
        List<UserSearch> list = userSearchDb.getOrDefault(req.getUserId(), Collections.emptyList());
        Map<String, Integer> authorFreq = new HashMap<>();
        list.forEach(s -> {
            if (s.getAuthor() != null) authorFreq.merge(s.getAuthor(), 1, Integer::sum);
        });
        List<String> topAuth = authorFreq.entrySet().stream()
                .sorted(Map.Entry.<String,Integer>comparingByValue().reversed())
                .limit(5).map(Map.Entry::getKey).toList();
        List<Book> recs = new ArrayList<>();
        for (Book b : bookDb.values()) {
            if (b.getAuthors().stream().anyMatch(topAuth::contains)) {
                recs.add(b);
            }
            if (recs.size() >= req.getLimit()) break;
        }
        RecommendationResponse resp = new RecommendationResponse();
        resp.setRecommendations(recs);
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