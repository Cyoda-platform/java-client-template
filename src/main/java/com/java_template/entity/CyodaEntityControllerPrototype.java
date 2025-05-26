package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ENTITY_NAME = "Photo";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Photo {
        private String technicalId; // unique id managed by entityService
        private String id; // original id from external API
        private String title;
        private String description;
        private String thumbnailUrl;
        private String imageUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Comment {
        private String technicalId;
        private String photoTechnicalId;
        private String user;
        private String comment;
        private Instant timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Notification {
        private String technicalId;
        private String message;
        private Instant timestamp;
        private boolean read;
    }

    @Data
    static class IngestResponse {
        private String status;
        private int ingestedCount;
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
    }

    @Data
    static class ViewCount {
        private String photoTechnicalId;
        private int count;
    }

    // We keep view counts and notifications locally as no replacement method provided
    private final Map<String, Integer> photoViewCounts = new HashMap<>();
    private final List<Notification> notifications = Collections.synchronizedList(new ArrayList<>());

    private static final String EXTERNAL_API_URL = "https://fakerestapi.azurewebsites.net/api/v1/CoverPhotos";

    @PostMapping("/ingest")
    public IngestResponse ingestPhotos() {
        logger.info("Starting ingestion from external API: {}", EXTERNAL_API_URL);
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
            List<Photo> photosToAdd = new ArrayList<>();
            for (JsonNode node : rootNode) {
                String id = node.path("id").asText();
                String title = node.path("name").asText();
                String thumbnailUrl = node.path("cover").asText();
                if (id == null || id.isBlank()) {
                    logger.warn("Skipping photo with missing id");
                    continue;
                }
                String description = "Description for photo " + title;
                String imageUrl = thumbnailUrl;
                Photo photo = new Photo(null, id, title, description, thumbnailUrl, imageUrl);
                photosToAdd.add(photo);
            }
            if (photosToAdd.isEmpty()) {
                return new IngestResponse("success", 0);
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(ENTITY_NAME, ENTITY_VERSION, photosToAdd);
            List<UUID> createdIds = idsFuture.join();
            // Map generated technicalIds back into photosToAdd
            for (int i = 0; i < createdIds.size(); i++) {
                photosToAdd.get(i).setTechnicalId(createdIds.get(i).toString());
                photoViewCounts.put(createdIds.get(i).toString(), 0);
            }
            notifications.add(new Notification(UUID.randomUUID().toString(),
                    "New cover photos have been added to the gallery.",
                    Instant.now(),
                    false));
            logger.info("Ingested {} photos successfully", createdIds.size());
            return new IngestResponse("success", createdIds.size());
        } catch (Exception e) {
            logger.error("Exception during ingestion: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error ingesting cover photos");
        }
    }

    @GetMapping
    public Map<String, Object> getPhotos(@RequestParam(defaultValue = "1") @Min(1) int page,
                                         @RequestParam(defaultValue = "20") @Min(1) int size) {
        logger.info("Fetching photos page={} size={}", page, size);
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode items = itemsFuture.join();
        List<Map<String, String>> pagePhotos = new ArrayList<>();
        List<JsonNode> photoNodes = new ArrayList<>();
        items.forEach(photoNodes::add);
        int total = photoNodes.size();
        int fromIndex = Math.min((page - 1) * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        for (JsonNode p : photoNodes.subList(fromIndex, toIndex)) {
            Map<String, String> entry = new HashMap<>();
            entry.put("id", p.path("id").asText());
            entry.put("title", p.path("title").asText());
            entry.put("thumbnailUrl", p.path("thumbnailUrl").asText());
            pagePhotos.add(entry);
        }
        return Map.of("photos", pagePhotos, "page", page, "size", size, "total", total);
    }

    @GetMapping("/{photoId}")
    public Map<String, Object> getPhotoDetails(@PathVariable String photoId) {
        logger.info("Fetching details for photoId={}", photoId);
        // Find photo by matching id field in all photos
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode items = itemsFuture.join();
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
        // Retrieve comments filtered by photoTechnicalId condition
        String condition = String.format("{\"photoTechnicalId\":\"%s\"}", technicalId);
        CompletableFuture<ArrayNode> commentsFuture = entityService.getItemsByCondition("Comment", ENTITY_VERSION, condition);
        ArrayNode commentsArray = commentsFuture.join();
        List<Comment> comments = new ArrayList<>();
        commentsArray.forEach(c -> {
            Comment comment = null;
            try {
                comment = objectMapper.treeToValue(c, Comment.class);
            } catch (Exception ignored) {}
            if (comment != null) comments.add(comment);
        });
        return Map.of(
                "id", foundPhoto.path("id").asText(),
                "title", foundPhoto.path("title").asText(),
                "imageUrl", foundPhoto.path("imageUrl").asText(),
                "description", foundPhoto.path("description").asText(),
                "viewCount", viewCount,
                "comments", comments
        );
    }

    @PostMapping(value = "/{photoId}/comments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> postComment(@PathVariable String photoId,
                                           @RequestBody @Valid CommentRequest request) {
        logger.info("Adding comment to photoId={}", photoId);
        // Find photo technicalId by photo id
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode items = itemsFuture.join();
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
        Comment comment = new Comment(null, photoTechnicalId, request.getUser(), request.getComment(), Instant.now());
        CompletableFuture<UUID> idFuture = entityService.addItem("Comment", ENTITY_VERSION, comment);
        UUID createdId = idFuture.join();
        comment.setTechnicalId(createdId.toString());
        logger.info("Comment added with technicalId={}", comment.getTechnicalId());
        return Map.of("status", "success", "commentId", comment.getTechnicalId());
    }

    @PostMapping("/{photoId}/view")
    public Map<String, Object> incrementViewCount(@PathVariable String photoId) {
        logger.info("Incrementing view count for photoId={}", photoId);
        // Find photo technicalId by photo id
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode items = itemsFuture.join();
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
        return Map.of("status", "success", "newViewCount", newCount);
    }

    @PostMapping(value = "/reports/monthly-views", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ReportResponse generateMonthlyReport(@RequestBody @Valid ReportRequest request) {
        logger.info("Generating monthly report for month={}", request.getMonth());
        String dummyReportUrl = "https://example.com/reports/view-report-" + request.getMonth() + ".pdf"; // TODO: replace with real logic
        logger.info("Monthly report generated at {}", dummyReportUrl);
        return new ReportResponse(dummyReportUrl);
    }

    @GetMapping("/notifications")
    public Map<String, Object> getNotifications() {
        logger.info("Fetching notifications");
        return Map.of("notifications", new ArrayList<>(notifications));
    }

    @Async
    public CompletableFuture<Void> asyncIngest() {
        return CompletableFuture.runAsync(() -> {
            try {
                ingestPhotos();
            } catch (Exception e) {
                logger.error("Async ingestion failed: {}", e.getMessage(), e);
            }
        });
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }
}