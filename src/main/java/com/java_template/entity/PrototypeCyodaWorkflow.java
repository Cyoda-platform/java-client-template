package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/api/cyoda-entity")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String OPEN_LIBRARY_SEARCH_API = "https://openlibrary.org/search.json";
    private static final String ENTITY_NAME = "bookDetails";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/books/search")
    public ResponseEntity<SearchResponse> searchBooks(@RequestBody @Valid SearchRequest request) throws Exception {
        logger.info("Received search request: query='{}', genre='{}', year='{}', author='{}'",
                request.getQuery(), request.getGenre(), request.getPublicationYear(), request.getAuthor());

        String url = OPEN_LIBRARY_SEARCH_API + "?q=" + URLEncoder.encode(request.getQuery(), StandardCharsets.UTF_8);
        String rawResponse = restTemplate.getForObject(new URI(url), String.class);
        if (rawResponse == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Empty response from Open Library");
        }

        JsonNode rootNode = objectMapper.readTree(rawResponse);
        JsonNode docs = rootNode.path("docs");
        if (!docs.isArray()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected response structure");
        }

        List<ObjectNode> toAddEntities = new ArrayList<>();
        List<BookDetails> filteredBooks = new ArrayList<>();

        for (JsonNode bookNode : docs) {
            String title = bookNode.path("title").asText(null);
            if (title == null) continue;

            String author = null;
            JsonNode authorsNode = bookNode.path("author_name");
            if (authorsNode.isArray() && authorsNode.size() > 0) {
                author = authorsNode.get(0).asText();
            }
            int publicationYear = bookNode.path("first_publish_year").asInt(0);

            // Skip genre filtering here because genre is not reliably present in OpenLibrary API response
            // Filter author and publicationYear only
            if (request.getAuthor() != null && (author == null || !author.toLowerCase().contains(request.getAuthor().toLowerCase())))
                continue;
            if (request.getPublicationYear() != null && publicationYear != request.getPublicationYear())
                continue;

            String key = bookNode.path("key").asText(null);
            String bookId = key != null ? key.replace("/works/", "") :
                    UUID.nameUUIDFromBytes((title + (author != null ? author : "")).getBytes(StandardCharsets.UTF_8)).toString();

            ObjectNode entity = objectMapper.createObjectNode();
            entity.put("bookId", bookId);
            entity.put("title", title);
            if (author != null) entity.put("author", author);
            entity.put("publicationYear", publicationYear);
            entity.put("genre", request.getGenre() != null ? request.getGenre() : "Unknown");
            if (request.getUserId() != null && !request.getUserId().isBlank()) {
                entity.put("userId", request.getUserId());
            }

            toAddEntities.add(entity);

            filteredBooks.add(new BookDetails(bookId, title, author, null, entity.get("genre").asText(), publicationYear));
        }

        if (!toAddEntities.isEmpty()) {
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    toAddEntities,
                    this::processBookDetails
            );
            idsFuture.get(); // wait for persistence completion, handle exceptions outside
        }

        return ResponseEntity.ok(new SearchResponse(filteredBooks));
    }

    @GetMapping("/books/results")
    public ResponseEntity<SearchResponse> getSearchResults(
            @RequestParam(required = false) @Size(max = 50) String genre,
            @RequestParam(required = false) @Positive Integer publicationYear,
            @RequestParam(required = false) @Size(max = 100) String author
    ) throws ExecutionException, InterruptedException {
        logger.info("Fetching stored results with filters - genre: {}, year: {}, author: {}", genre, publicationYear, author);

        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                ENTITY_NAME,
                ENTITY_VERSION
        );
        ArrayNode items = itemsFuture.get();

        List<BookDetails> filtered = new ArrayList<>();
        for (JsonNode node : items) {
            BookDetails book = objectMapper.treeToValue(node, BookDetails.class);
            if (genre != null && (book.getGenre() == null || !book.getGenre().equalsIgnoreCase(genre)))
                continue;
            if (author != null && (book.getAuthor() == null || !book.getAuthor().toLowerCase().contains(author.toLowerCase())))
                continue;
            if (publicationYear != null && book.getPublicationYear() != publicationYear)
                continue;
            filtered.add(book);
        }
        return ResponseEntity.ok(new SearchResponse(filtered));
    }

    @GetMapping("/reports/weekly")
    public ResponseEntity<WeeklyReportResponse> getWeeklyReport() throws ExecutionException, InterruptedException {
        logger.info("Generating weekly report");

        // Generate report from stored data in supplementary entities
        // Fetch most searched books from userSearchHistory counting occurrences
        CompletableFuture<ArrayNode> userSearchesFuture = entityService.getItems("userSearchHistory", ENTITY_VERSION);
        ArrayNode userSearches = userSearchesFuture.get();

        Map<String, Integer> searchCountMap = new HashMap<>();
        Map<String, String> bookTitlesMap = new HashMap<>();

        // To get titles, fetch bookDetails entities
        CompletableFuture<ArrayNode> bookDetailsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode bookDetails = bookDetailsFuture.get();

        for (JsonNode bookNode : bookDetails) {
            String bookId = bookNode.path("bookId").asText(null);
            String title = bookNode.path("title").asText(null);
            if (bookId != null && title != null) {
                bookTitlesMap.put(bookId, title);
            }
        }

        for (JsonNode searchNode : userSearches) {
            String bookId = searchNode.path("bookId").asText(null);
            if (bookId != null) {
                searchCountMap.put(bookId, searchCountMap.getOrDefault(bookId, 0) + 1);
            }
        }

        List<MostSearchedBook> mostSearchedList = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : searchCountMap.entrySet()) {
            String bookId = entry.getKey();
            int count = entry.getValue();
            String title = bookTitlesMap.getOrDefault(bookId, "Unknown");
            mostSearchedList.add(new MostSearchedBook(bookId, title, count));
        }

        // Sort descending by count
        mostSearchedList.sort((a, b) -> Integer.compare(b.getSearchCount(), a.getSearchCount()));

        // Limit to top 10
        if (mostSearchedList.size() > 10) {
            mostSearchedList = mostSearchedList.subList(0, 10);
        }

        // Aggregate top genres and authors from bookDetails weighted by search counts
        Map<String, Integer> genreCount = new HashMap<>();
        Map<String, Integer> authorCount = new HashMap<>();

        for (MostSearchedBook msb : mostSearchedList) {
            for (JsonNode bookNode : bookDetails) {
                String bookId = bookNode.path("bookId").asText(null);
                if (bookId != null && bookId.equals(msb.getBookId())) {
                    String genre = bookNode.path("genre").asText(null);
                    String author = bookNode.path("author").asText(null);
                    if (genre != null) genreCount.put(genre, genreCount.getOrDefault(genre, 0) + msb.getSearchCount());
                    if (author != null) authorCount.put(author, authorCount.getOrDefault(author, 0) + msb.getSearchCount());
                    break;
                }
            }
        }

        List<String> topGenres = getTopKeys(genreCount, 5);
        List<String> topAuthors = getTopKeys(authorCount, 5);

        UserPreferencesSummary preferencesSummary = new UserPreferencesSummary(topGenres, topAuthors);

        return ResponseEntity.ok(new WeeklyReportResponse(mostSearchedList, preferencesSummary));
    }

    @PostMapping("/recommendations")
    public ResponseEntity<RecommendationsResponse> getRecommendations(@RequestBody @Valid RecommendationRequest request) throws ExecutionException, InterruptedException {
        logger.info("Generating recommendations for userId={}", request.getUserId());

        // Fetch user search history
        CompletableFuture<ArrayNode> userSearchesFuture = entityService.getItems("userSearchHistory", ENTITY_VERSION);
        ArrayNode userSearches = userSearchesFuture.get();

        Set<String> searchedBookIds = new HashSet<>();
        for (JsonNode searchNode : userSearches) {
            String userId = searchNode.path("userId").asText(null);
            if (userId != null && userId.equals(request.getUserId())) {
                String bookId = searchNode.path("bookId").asText(null);
                if (bookId != null) searchedBookIds.add(bookId);
            }
        }

        // Fetch all bookDetails
        CompletableFuture<ArrayNode> bookDetailsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode bookDetails = bookDetailsFuture.get();

        List<BookDetails> recommendations = new ArrayList<>();
        // Recommend books not searched by user yet, limit 10
        for (JsonNode bookNode : bookDetails) {
            String bookId = bookNode.path("bookId").asText(null);
            if (bookId == null) continue;
            if (!searchedBookIds.contains(bookId)) {
                BookDetails book = objectMapper.treeToValue(bookNode, BookDetails.class);
                recommendations.add(book);
                if (recommendations.size() >= 10) break;
            }
        }

        return ResponseEntity.ok(new RecommendationsResponse(recommendations));
    }

    @PostMapping("/ingestion/trigger")
    public ResponseEntity<IngestionTriggerResponse> triggerDataIngestion() {
        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        logger.info("Ingestion job {} started at {}", jobId, requestedAt);
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Ingestion job {} mock running", jobId);
                Thread.sleep(2000);
                logger.info("Ingestion job {} completed", jobId);
            } catch (Exception e) {
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

    // Workflow function applied asynchronously before persistence.
    // Modifies entity state and triggers async side effects.
    private CompletableFuture<ObjectNode> processBookDetails(ObjectNode entity) {
        // Ensure genre is present
        if (!entity.hasNonNull("genre") || entity.get("genre").asText().isEmpty()) {
            entity.put("genre", "Unknown");
        }

        // Add supplementary entity "bookMetadata"
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("bookId", entity.get("bookId").asText());
        metadata.put("title", entity.get("title").asText());
        metadata.put("timestamp", Instant.now().toString());
        entityService.addItem("bookMetadata", ENTITY_VERSION, metadata, e -> CompletableFuture.completedFuture(e));

        // Add user search history if userId present
        if (entity.hasNonNull("userId")) {
            ObjectNode userSearch = objectMapper.createObjectNode();
            userSearch.put("userId", entity.get("userId").asText());
            userSearch.put("bookId", entity.get("bookId").asText());
            userSearch.put("searchedAt", Instant.now().toString());
            entityService.addItem("userSearchHistory", ENTITY_VERSION, userSearch, e -> CompletableFuture.completedFuture(e));
        }

        return CompletableFuture.completedFuture(entity);
    }

    private static List<String> getTopKeys(Map<String, Integer> map, int limit) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(map.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<String> topKeys = new ArrayList<>();
        for (int i = 0; i < entries.size() && i < limit; i++) {
            topKeys.add(entries.get(i).getKey());
        }
        return topKeys;
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
}