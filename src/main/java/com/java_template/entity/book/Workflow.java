package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda/books")
@Validated
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ENTITY_NAME = "book";

    // Store job statuses for reports
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    // Store user search histories for recommendations
    private final Map<String, List<String>> userSearchHistory = new ConcurrentHashMap<>();

    public Workflow(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/search")
    public CompletableFuture<ResponseEntity<SearchResponse>> processSearchBooks(@RequestBody @Valid SearchRequest request) throws Exception {
        return CompletableFuture.supplyAsync(() -> {
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

                String responseStr;
                try (java.io.InputStream is = new java.net.URL(url.toString()).openStream();
                     java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A")) {
                    responseStr = s.hasNext() ? s.next() : "";
                }

                JsonNode root = objectMapper.readTree(responseStr);
                int numFound = root.path("numFound").asInt(0);
                JsonNode docs = root.path("docs");
                List<ObjectNode> entitiesToAdd = new ArrayList<>();
                List<Book> results = new ArrayList<>();

                for (JsonNode doc : docs) {
                    String title = doc.path("title").asText(null);
                    if (title == null) continue;

                    ArrayNode authorsArray = objectMapper.createArrayNode();
                    if (doc.has("author_name")) {
                        for (JsonNode n : doc.path("author_name")) authorsArray.add(n.asText());
                    }

                    ArrayNode subjectsArray = objectMapper.createArrayNode();
                    if (doc.has("subject")) {
                        for (JsonNode n : doc.path("subject")) subjectsArray.add(n.asText());
                    }

                    int year = doc.has("first_publish_year") ? doc.path("first_publish_year").asInt(0) : 0;
                    String coverId = doc.has("cover_i") ? doc.path("cover_i").asText("") : "";
                    String coverImageUrl = coverId.isEmpty() ? null : "https://covers.openlibrary.org/b/id/" + coverId + "-M.jpg";
                    String key = doc.path("key").asText(null);
                    String id = key != null ? key.replace("/works/", "") : null;
                    if (id == null) continue;

                    ObjectNode bookEntity = objectMapper.createObjectNode();
                    bookEntity.put("title", title);
                    bookEntity.set("authors", authorsArray);
                    bookEntity.put("coverImageUrl", coverImageUrl);
                    bookEntity.set("genres", subjectsArray);
                    bookEntity.put("publicationYear", year);
                    bookEntity.put("openLibraryId", id);

                    entitiesToAdd.add(bookEntity);

                    Book pojo = new Book(title,
                            toStringList(authorsArray),
                            coverImageUrl,
                            toStringList(subjectsArray),
                            year,
                            id);
                    results.add(pojo);
                }

                if (!entitiesToAdd.isEmpty()) {
                    try {
                        entityService.addItems(
                                ENTITY_NAME,
                                ENTITY_VERSION,
                                entitiesToAdd,
                                this::processbook
                        ).get();
                    } catch (Exception e) {
                        logger.error("Failed to add book entities", e);
                        throw new RuntimeException(e);
                    }
                }

                CompletableFuture.runAsync(() -> {
                    String userId = "anonymous";
                    List<String> history = userSearchHistory.computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()));
                    for (Book b : results) {
                        if (b.getOpenLibraryId() != null) {
                            synchronized (history) {
                                if (!history.contains(b.getOpenLibraryId())) {
                                    history.add(b.getOpenLibraryId());
                                    if (history.size() > 100) history.remove(0);
                                }
                            }
                        }
                    }
                });

                return ResponseEntity.ok(new SearchResponse(numFound, results));
            } catch (Exception e) {
                logger.error("Error during book search", e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<ObjectNode> processbook(ObjectNode entity) {
        // workflow orchestration only
        return processNormalizePublicationYear(entity)
                .thenCompose(this::processAddIsClassic)
                .thenCompose(this::processAsyncLogging)
                .thenCompose(this::processAddAuthorProfiles);
    }

    private CompletableFuture<ObjectNode> processNormalizePublicationYear(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (entity.has("publicationYear")) {
                int pubYear = entity.get("publicationYear").asInt(0);
                if (pubYear < 0) entity.put("publicationYear", 0);
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processAddIsClassic(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (entity.has("publicationYear")) {
                int pubYear = entity.get("publicationYear").asInt(0);
                entity.put("isClassic", pubYear > 0 && pubYear < 1970);
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processAsyncLogging(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            CompletableFuture.runAsync(() -> {
                logger.info("Persisting book entity with title: {}", entity.path("title").asText("[unknown]"));
            });
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processAddAuthorProfiles(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (entity.has("authors") && entity.get("authors").isArray()) {
                    ArrayNode authors = (ArrayNode) entity.get("authors");
                    for (JsonNode authorNode : authors) {
                        String authorName = authorNode.asText();
                        if (authorName == null || authorName.isEmpty()) continue;
                        ObjectNode authorProfile = objectMapper.createObjectNode();
                        authorProfile.put("name", authorName);
                        authorProfile.put("type", "authorProfile");
                        entityService.addItem("authorProfile", ENTITY_VERSION, authorProfile, this::processauthorProfile);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to add supplementary authorProfile entities", e);
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processauthorProfile(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            entity.put("createdAt", Instant.now().toString());
            return entity;
        });
    }

    private List<String> toStringList(ArrayNode arrayNode) {
        List<String> list = new ArrayList<>();
        for (JsonNode n : arrayNode) {
            list.add(n.asText());
        }
        return list;
    }
}