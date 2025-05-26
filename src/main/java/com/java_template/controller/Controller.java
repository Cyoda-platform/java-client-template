package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.java_template.common.service.EntityService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ENTITY_NAME = "Photo";

    private static final String COMMENT_ENTITY = "Comment";
    private static final String NOTIFICATION_ENTITY = "Notification";

    private static final String EXTERNAL_API_URL = "https://fakerestapi.azurewebsites.net/api/v1/CoverPhotos";

    // For simplicity, keeping view counts locally, no persistence defined for this.
    private final Map<String, Integer> photoViewCounts = Collections.synchronizedMap(new HashMap<>());

    @Data
    static class IngestResponse {
        private String status;
        private int ingestedCount;

        public IngestResponse(String status, int ingestedCount) {
            this.status = status;
            this.ingestedCount = ingestedCount;
        }
    }

    @Data
    static class CommentRequest {
        @NotBlank
        private String user;
        @NotBlank
        private String comment;
    }

    @Data
    static class ReportRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}")
        private String month;
    }

    @Data
    static class ReportResponse {
        private String reportUrl;

        public ReportResponse(String reportUrl) {
            this.reportUrl = reportUrl;
        }
    }

    @PostMapping("/ingest")
    public CompletableFuture<IngestResponse> ingestPhotos() {
        logger.info("Starting ingestion from external API: {}", EXTERNAL_API_URL);
        return CompletableFuture.supplyAsync(() -> {
            var response = restTemplate.getForEntity(URI.create(EXTERNAL_API_URL), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.error("Failed fetching cover photos. Status: {}", response.getStatusCode());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch external cover photos");
            }
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                logger.error("Empty response from external API");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from external API");
            }
            try {
                JsonNode rootNode = objectMapper.readTree(body);
                if (!rootNode.isArray()) {
                    logger.error("Unexpected JSON format: expected array");
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unexpected JSON format from external API");
                }
                List<CompletableFuture<UUID>> futures = new ArrayList<>();
                for (JsonNode node : rootNode) {
                    String id = node.path("id").asText(null);
                    String name = node.path("name").asText("");
                    String cover = node.path("cover").asText("");
                    if (id == null || id.isBlank()) {
                        logger.warn("Skipping photo with missing id");
                        continue;
                    }
                    ObjectNode photo = JsonNodeFactory.instance.objectNode();
                    photo.put("id", id);
                    photo.put("title", name);
                    photo.put("thumbnailUrl", cover);
                    photo.put("description", "Description for photo " + name);
                    photo.put("imageUrl", cover);

                    futures.add(entityService.addItem(ENTITY_NAME, ENTITY_VERSION, photo));
                }
                List<UUID> createdIds = new ArrayList<>();
                for (CompletableFuture<UUID> future : futures) {
                    createdIds.add(future.join());
                }
                logger.info("Ingested {} photos successfully", createdIds.size());
                return new IngestResponse("success", createdIds.size());
            } catch (Exception e) {
                logger.error("Exception during ingestion: {}", e.getMessage(), e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error ingesting cover photos");
            }
        });
    }

    @GetMapping
    public CompletableFuture<Map<String, Object>> getPhotos(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                            @RequestParam(defaultValue = "20") @Min(1) int size) {
        logger.info("Fetching photos page={} size={}", page, size);
        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION)
                .thenApply(items -> {
                    List<JsonNode> photoNodes = new ArrayList<>();
                    items.forEach(photoNodes::add);
                    int total = photoNodes.size();
                    int fromIndex = Math.min((page - 1) * size, total);
                    int toIndex = Math.min(fromIndex + size, total);
                    List<Map<String, String>> pagePhotos = new ArrayList<>();
                    for (JsonNode p : photoNodes.subList(fromIndex, toIndex)) {
                        Map<String, String> entry = new HashMap<>();
                        entry.put("id", p.path("id").asText(""));
                        entry.put("title", p.path("title").asText(""));
                        entry.put("thumbnailUrl", p.path("thumbnailUrl").asText(""));
                        pagePhotos.add(entry);
                    }
                    return Map.of("photos", pagePhotos, "page", page, "size", size, "total", total);
                });
    }

    @GetMapping("/{photoId}")
    public CompletableFuture<Map<String, Object>> getPhotoDetails(@PathVariable String photoId) {
        logger.info("Fetching details for photoId={}", photoId);
        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION)
                .thenCompose(items -> {
                    JsonNode foundPhoto = null;
                    String technicalId = null;
                    for (JsonNode p : items) {
                        if (photoId.equals(p.path("id").asText())) {
                            foundPhoto = p;
                            technicalId = p.path("technicalId").asText();
                            break;
                        }
                    }
                    if (foundPhoto == null) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found");
                    }
                    int viewCount = photoViewCounts.getOrDefault(technicalId, 0);
                    String condition = String.format("{\"photoTechnicalId\":\"%s\"}", technicalId);
                    return entityService.getItemsByCondition(COMMENT_ENTITY, ENTITY_VERSION, condition)
                            .thenApply(commentsArray -> {
                                List<ObjectNode> comments = new ArrayList<>();
                                commentsArray.forEach(c -> {
                                    if (c.isObject()) {
                                        comments.add((ObjectNode)c);
                                    }
                                });
                                return Map.<String, Object>of(
                                        "id", foundPhoto.path("id").asText(""),
                                        "title", foundPhoto.path("title").asText(""),
                                        "imageUrl", foundPhoto.path("imageUrl").asText(""),
                                        "description", foundPhoto.path("description").asText(""),
                                        "viewCount", viewCount,
                                        "comments", comments
                                );
                            });
                });
    }

    @PostMapping(value = "/{photoId}/comments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<Map<String, String>> postComment(@PathVariable String photoId,
                                                              @RequestBody @Valid CommentRequest request) {
        logger.info("Adding comment to photoId={}", photoId);
        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION)
                .thenCompose(items -> {
                    String photoTechnicalId = null;
                    for (JsonNode p : items) {
                        if (photoId.equals(p.path("id").asText())) {
                            photoTechnicalId = p.path("technicalId").asText();
                            break;
                        }
                    }
                    if (photoTechnicalId == null) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found");
                    }
                    ObjectNode comment = JsonNodeFactory.instance.objectNode();
                    comment.put("photoTechnicalId", photoTechnicalId);
                    comment.put("user", request.getUser());
                    comment.put("comment", request.getComment());

                    return entityService.addItem(COMMENT_ENTITY, ENTITY_VERSION, comment)
                            .thenApply(id -> Map.<String, String>of("status", "success", "commentId", id.toString()));
                });
    }

    @PostMapping("/{photoId}/view")
    public CompletableFuture<Map<String, Object>> incrementViewCount(@PathVariable String photoId) {
        logger.info("Incrementing view count for photoId={}", photoId);
        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION)
                .thenApply(items -> {
                    String technicalId = null;
                    for (JsonNode p : items) {
                        if (photoId.equals(p.path("id").asText())) {
                            technicalId = p.path("technicalId").asText();
                            break;
                        }
                    }
                    if (technicalId == null) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found");
                    }
                    photoViewCounts.merge(technicalId, 1, Integer::sum);
                    int newCount = photoViewCounts.get(technicalId);
                    logger.info("New view count for photoId={} is {}", photoId, newCount);
                    return Map.<String, Object>of("status", "success", "newViewCount", newCount);
                });
    }

    @PostMapping(value = "/reports/monthly-views", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ReportResponse generateMonthlyReport(@RequestBody @Valid ReportRequest request) {
        logger.info("Generating monthly report for month={}", request.getMonth());
        String dummyReportUrl = "https://example.com/reports/view-report-" + request.getMonth() + ".pdf"; // TODO: replace with real logic
        logger.info("Monthly report generated at {}", dummyReportUrl);
        return new ReportResponse(dummyReportUrl);
    }

    @GetMapping("/notifications")
    public CompletableFuture<Map<String, Object>> getNotifications() {
        logger.info("Fetching notifications");
        return entityService.getItems(NOTIFICATION_ENTITY, ENTITY_VERSION)
                .thenApply(items -> Map.of("notifications", items));
    }

    @PostConstruct
    public void init() {
        logger.info("Controller initialized");
    }
}