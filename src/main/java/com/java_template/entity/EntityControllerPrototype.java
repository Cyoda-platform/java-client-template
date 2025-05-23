package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

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
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String OPEN_LIBRARY_SEARCH_API = "https://openlibrary.org/search.json";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, BookDetails> bookStore = new ConcurrentHashMap<>();
    private final Map<String, List<String>> userSearchHistory = new ConcurrentHashMap<>();
    private final Map<String, Integer> searchedBooksCount = new ConcurrentHashMap<>();
    private final Map<String, JobStatus> ingestionJobs = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        logger.info("EntityControllerPrototype initialized");
    }

    @PostMapping("/books/search")
    public ResponseEntity<SearchResponse> searchBooks(@RequestBody @Valid SearchRequest request) {
        logger.info("Received search request: query='{}', genre='{}', year='{}', author='{}'",
                request.getQuery(), request.getGenre(), request.getPublicationYear(), request.getAuthor());

        try {
            StringBuilder urlBuilder = new StringBuilder(OPEN_LIBRARY_SEARCH_API);
            urlBuilder.append("?q=").append(URLEncoder.encode(request.getQuery(), StandardCharsets.UTF_8));
            URI uri = new URI(urlBuilder.toString());
            logger.info("Calling Open Library API: {}", uri);

            String rawResponse = restTemplate.getForObject(uri, String.class);
            if (rawResponse == null) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Empty response from Open Library");
            }
            JsonNode rootNode = objectMapper.readTree(rawResponse);
            JsonNode docs = rootNode.path("docs");
            if (!docs.isArray()) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected response structure");
            }

            List<BookDetails> filteredBooks = new ArrayList<>();
            for (JsonNode bookNode : docs) {
                String title = bookNode.path("title").asText(null);
                if (title == null) continue;
                JsonNode authorsNode = bookNode.path("author_name");
                String author = (authorsNode.isArray() && authorsNode.size() > 0) ? authorsNode.get(0).asText() : null;
                String coverId = bookNode.path("cover_i").asText(null);
                String coverImage = coverId != null ? "https://covers.openlibrary.org/b/id/" + coverId + "-M.jpg" : null;
                int publicationYear = bookNode.path("first_publish_year").asInt(0);
                String genre = null; // TODO: enrich genre from another source

                if (request.getGenre() != null && (genre == null || !genre.equalsIgnoreCase(request.getGenre()))) continue;
                if (request.getAuthor() != null && (author == null || !author.toLowerCase().contains(request.getAuthor().toLowerCase()))) continue;
                if (request.getPublicationYear() != null && publicationYear != request.getPublicationYear()) continue;

                String key = bookNode.path("key").asText(null);
                String bookId = key != null ? key.replace("/works/", "") :
                        UUID.nameUUIDFromBytes((title + author).getBytes()).toString();

                BookDetails book = new BookDetails(bookId, title, author, coverImage, genre, publicationYear);
                filteredBooks.add(book);
                bookStore.put(bookId, book);
                searchedBooksCount.merge(bookId, 1, Integer::sum);
            }

            if (request.getUserId() != null && !request.getUserId().isBlank()) {
                userSearchHistory.computeIfAbsent(request.getUserId(), k -> new ArrayList<>())
                        .addAll(filteredBooks.stream().map(BookDetails::getBookId).toList());
            }

            return ResponseEntity.ok(new SearchResponse(filteredBooks));
        } catch (ResponseStatusException ex) {
            logger.error("Error during searchBooks: status={}, message={}", ex.getStatusCode(), ex.getReason());
            throw ex;
        } catch (Exception ex) {
            logger.error("Unexpected error during searchBooks", ex);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to search books");
        }
    }

    @GetMapping("/books/results")
    public ResponseEntity<SearchResponse> getSearchResults(
            @RequestParam(required = false) @Size(max = 50) String genre,
            @RequestParam(required = false) @Positive Integer publicationYear,
            @RequestParam(required = false) @Size(max = 100) String author) {

        logger.info("Fetching stored results with filters - genre: {}, year: {}, author: {}", genre, publicationYear, author);
        List<BookDetails> filtered = new ArrayList<>();
        for (BookDetails book : bookStore.values()) {
            if (genre != null && (book.getGenre() == null || !book.getGenre().equalsIgnoreCase(genre))) continue;
            if (author != null && (book.getAuthor() == null || !book.getAuthor().toLowerCase().contains(author.toLowerCase()))) continue;
            if (publicationYear != null && book.getPublicationYear() != publicationYear) continue;
            filtered.add(book);
        }
        return ResponseEntity.ok(new SearchResponse(filtered));
    }

    @GetMapping("/reports/weekly")
    public ResponseEntity<WeeklyReportResponse> getWeeklyReport() {
        logger.info("Generating weekly report");
        List<MostSearchedBook> mostSearched = new ArrayList<>();
        searchedBooksCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> {
                    BookDetails b = bookStore.get(e.getKey());
                    if (b != null) mostSearched.add(new MostSearchedBook(b.getBookId(), b.getTitle(), e.getValue()));
                });

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

        return ResponseEntity.ok(new WeeklyReportResponse(mostSearched, new UserPreferencesSummary(topGenres, topAuthors)));
    }

    @PostMapping("/recommendations")
    public ResponseEntity<RecommendationsResponse> getRecommendations(@RequestBody @Valid RecommendationRequest request) {
        logger.info("Generating recommendations for userId={}", request.getUserId());
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

    @PostMapping("/ingestion/trigger")
    public ResponseEntity<IngestionTriggerResponse> triggerDataIngestion() {
        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        ingestionJobs.put(jobId, new JobStatus("processing", requestedAt));
        logger.info("Ingestion job {} started at {}", jobId, requestedAt);
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Ingestion job {} mock running", jobId);
                Thread.sleep(2000);
                ingestionJobs.put(jobId, new JobStatus("completed", Instant.now()));
                logger.info("Ingestion job {} completed", jobId);
            } catch (Exception e) {
                ingestionJobs.put(jobId, new JobStatus("failed", Instant.now()));
                logger.error("Ingestion job {} failed", jobId, e);
            }
        });
        return ResponseEntity.ok(new IngestionTriggerResponse("started"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled exception: status={}, message={}", ex.getStatusCode(), ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @Data
    public static class SearchRequest {
        @NotBlank
        private String query;
        private String genre;
        private Integer publicationYear;
        private String author;
        private String userId;
    }

    @Data
    public static class RecommendationRequest {
        @NotBlank
        private String userId;
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