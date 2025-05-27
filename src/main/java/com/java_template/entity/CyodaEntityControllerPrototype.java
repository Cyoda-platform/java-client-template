package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda/books")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, List<String>> userSearchHistory = new ConcurrentHashMap<>();
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    private static final String ENTITY_NAME = "book";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/search")
    public ResponseEntity<SearchResponse> searchBooks(@RequestBody @Valid SearchRequest request) throws Exception {
        logger.info("Search request received: query='{}', page={}, pageSize={}", request.getQuery(), request.getPage(), request.getPageSize());
        try {
            StringBuilder url = new StringBuilder("https://openlibrary.org/search.json?");
            url.append("q=").append(URLEncoder.encode(request.getQuery(), StandardCharsets.UTF_8));
            if (request.getFilters() != null) {
                Filters f = request.getFilters();
                if (f.getAuthor() != null) for (String author : f.getAuthor())
                    url.append("&author=").append(URLEncoder.encode(author, StandardCharsets.UTF_8));
                if (f.getGenre() != null) for (String genre : f.getGenre())
                    url.append("&subject=").append(URLEncoder.encode(genre, StandardCharsets.UTF_8));
                if (f.getPublicationYearFrom() != null) url.append("&publish_year>").append(f.getPublicationYearFrom());
                if (f.getPublicationYearTo() != null) url.append("&publish_year<").append(f.getPublicationYearTo());
            }
            url.append("&page=").append(request.getPage()).append("&limit=").append(request.getPageSize());
            logger.info("Calling Open Library API: {}", url);
            String responseStr = new URI(url.toString()).toURL().openStream().transferTo(new java.io.ByteArrayOutputStream()).toString();
            // Using restTemplate was removed, replaced with openStream().transferTo to avoid RestTemplate
            
            // Actually using java.net.URL directly
            java.net.URL urlObj = new java.net.URL(url.toString());
            try (java.io.InputStream is = urlObj.openStream(); java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A")) {
                responseStr = s.hasNext() ? s.next() : "";
            }

            JsonNode root = objectMapper.readTree(responseStr);
            int numFound = root.path("numFound").asInt(0);
            JsonNode docs = root.path("docs");
            List<Book> results = new ArrayList<>();
            List<Book> toAdd = new ArrayList<>();
            for (JsonNode doc : docs) {
                String title = doc.path("title").asText(null);
                if (title == null) continue;
                List<String> authors = new ArrayList<>();
                if (doc.has("author_name")) for (JsonNode n : doc.path("author_name")) authors.add(n.asText());
                List<String> subjects = new ArrayList<>();
                if (doc.has("subject")) for (JsonNode n : doc.path("subject")) subjects.add(n.asText());
                int year = doc.has("first_publish_year") ? doc.path("first_publish_year").asInt(0) : 0;
                String coverId = doc.has("cover_i") ? doc.path("cover_i").asText("") : "";
                String coverImageUrl = coverId.isEmpty() ? null : "https://covers.openlibrary.org/b/id/" + coverId + "-M.jpg";
                String key = doc.path("key").asText(null);
                String id = key != null ? key.replace("/works/", "") : null;
                if (id != null) {
                    Book book = new Book(title, authors, coverImageUrl, subjects, year, id);
                    results.add(book);
                    toAdd.add(book);
                }
            }
            // Persist found books using entityService.addItems
            if (!toAdd.isEmpty()) {
                CompletableFuture<List<UUID>> idsFuture = entityService.addItems(ENTITY_NAME, ENTITY_VERSION, toAdd);
                idsFuture.get(); // wait for completion
            }

            return ResponseEntity.ok(new SearchResponse(numFound, results));
        } catch (Exception e) {
            logger.error("Error during book search", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to search books");
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<Book> getBookDetails(@PathVariable @NotBlank String technicalId) throws ExecutionException, InterruptedException {
        logger.info("Get book details for technicalId: {}", technicalId);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, UUID.fromString(technicalId));
        ObjectNode obj = itemFuture.get();
        if (obj == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found");
        Book book = objectMapper.convertValue(obj, Book.class);
        return ResponseEntity.ok(book);
    }

    @GetMapping("/filters")
    public ResponseEntity<FiltersResponse> getFilters() throws ExecutionException, InterruptedException {
        logger.info("Get available filters");
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode items = itemsFuture.get();
        Set<String> genres = new HashSet<>();
        Set<String> authors = new HashSet<>();
        Set<Integer> years = new HashSet<>();
        for (JsonNode node : items) {
            Book b = objectMapper.convertValue(node, Book.class);
            if (b.getGenres() != null) genres.addAll(b.getGenres());
            if (b.getAuthors() != null) authors.addAll(b.getAuthors());
            if (b.getPublicationYear() != 0) years.add(b.getPublicationYear());
        }
        List<Integer> sortedYears = new ArrayList<>(years);
        Collections.sort(sortedYears);
        return ResponseEntity.ok(new FiltersResponse(new ArrayList<>(genres), new ArrayList<>(authors), sortedYears));
    }

    @PostMapping("/reports/weekly")
    public ResponseEntity<JobStatusResponse> generateWeeklyReport(@RequestBody @Valid ReportRequest request) {
        logger.info("Trigger weekly report generation for week starting {}", request.getWeekStartDate());
        String jobId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        entityJobs.put(jobId, new JobStatus("processing", now));
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(3000);
                entityJobs.put(jobId, new JobStatus("completed", Instant.now()));
                logger.info("Completed weekly report job {}", jobId);
            } catch (InterruptedException e) {
                logger.error("Weekly report job interrupted", e);
                entityJobs.put(jobId, new JobStatus("failed", Instant.now()));
            }
        });
        return ResponseEntity.ok(new JobStatusResponse(jobId, "processing", now));
    }

    @GetMapping("/reports/weekly/{reportId}")
    public ResponseEntity<WeeklyReport> getWeeklyReport(@PathVariable @NotBlank String reportId) {
        logger.info("Get weekly report with id {}", reportId);
        JobStatus status = entityJobs.get(reportId);
        if (status == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report job not found");
        if (!"completed".equals(status.getStatus())) return ResponseEntity.status(HttpStatus.ACCEPTED).body(null);
        WeeklyReport report = new WeeklyReport(
                List.of(new BookSearchCount("Sample Book", 42)),
                new UserPreferences(List.of("Fantasy","Science Fiction"), List.of("Rowling","Asimov"))
        );
        return ResponseEntity.ok(report);
    }

    @PostMapping("/recommendations")
    public ResponseEntity<RecommendationsResponse> getRecommendations(@RequestBody @Valid RecommendationRequest request) throws ExecutionException, InterruptedException {
        logger.info("Get recommendations for user {}", request.getUserId());
        List<String> history = userSearchHistory.getOrDefault(request.getUserId(), Collections.emptyList());
        if (history.isEmpty()) return ResponseEntity.ok(new RecommendationsResponse(Collections.emptyList()));
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode allItems = itemsFuture.get();
        Map<String, Book> bookMap = new HashMap<>();
        for (JsonNode node : allItems) {
            Book b = objectMapper.convertValue(node, Book.class);
            String technicalId = node.path("technicalId").asText(null);
            if (technicalId != null) bookMap.put(technicalId, b);
        }

        Set<Book> recs = new LinkedHashSet<>();
        for (String id : history) {
            Book b = bookMap.get(id);
            if (b != null && b.getGenres() != null) {
                for (Book c : bookMap.values()) {
                    if (!c.getOpenLibraryId().equals(b.getOpenLibraryId()) && c.getGenres() != null &&
                            c.getGenres().stream().anyMatch(g -> b.getGenres().contains(g))) {
                        recs.add(c);
                        if (recs.size() >= request.getLimit()) break;
                    }
                }
            }
            if (recs.size() >= request.getLimit()) break;
        }
        List<Book> list = new ArrayList<>(recs);
        if (list.size() > request.getLimit()) list = list.subList(0, request.getLimit());
        return ResponseEntity.ok(new RecommendationsResponse(list));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchRequest {
        @NotBlank private String query;
        @Valid private Filters filters;
        @NotNull @Min(1) private Integer page;
        @NotNull @Min(1) private Integer pageSize;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Filters {
        @Size(min = 1) private List<@NotBlank String> genre;
        @Min(0) private Integer publicationYearFrom;
        @Min(0) private Integer publicationYearTo;
        @Size(min = 1) private List<@NotBlank String> author;
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
        @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") private String weekStartDate;
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
        @NotBlank private String userId;
        @Min(1) private int limit;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationsResponse {
        private List<Book> recommendations;
    }
}