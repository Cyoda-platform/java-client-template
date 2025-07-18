package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Validated
@RestController
@RequestMapping(path = "/cyoda/photos")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private static final String ENTITY_MODEL_PHOTO = "Photo";
    private static final String ENTITY_MODEL_COMMENT = "Comment";

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private Instant lastIngestionTime = Instant.EPOCH;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingestPhotos() throws Exception {
        logger.info("Starting ingestion from Fakerest API.");
        String response = restTemplate.getForObject(URI.create("https://fakerestapi.azurewebsites.net/api/v1/Books"), String.class);
        JsonNode root = objectMapper.readTree(response);
        if (!root.isArray()) {
            logger.error("Unexpected JSON structure from Fakerest API: expected array.");
            throw new ResponseStatusException(ResponseStatusException.Status.INTERNAL_SERVER_ERROR, "Invalid external API response");
        }

        List<Map<String, Object>> newPhotos = new ArrayList<>();
        for (JsonNode node : root) {
            String id = node.path("id").asText();
            String title = node.path("title").asText();
            String imageUrl = node.path("excerpt").asText();
            if (imageUrl == null || imageUrl.isBlank()) {
                imageUrl = "https://via.placeholder.com/600x400?text=No+Image";
            }
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.id", "EQUALS", id));
            CompletableFuture<JsonNode> existing = entityService.getItemsByCondition(ENTITY_MODEL_PHOTO, ENTITY_VERSION, condition)
                    .thenApply(arrayNode -> arrayNode.isEmpty() ? null : arrayNode.get(0));
            JsonNode existingPhoto = existing.get();
            if (existingPhoto == null) {
                Map<String, Object> photoMap = new HashMap<>();
                photoMap.put("id", id);
                photoMap.put("title", title);
                photoMap.put("imageUrl", imageUrl);
                photoMap.put("thumbnailUrl", imageUrl);
                newPhotos.add(photoMap);
            }
        }
        int newPhotosCount = 0;
        if (!newPhotos.isEmpty()) {
            CompletableFuture<List<UUID>> addedIdsFuture = entityService.addItems(ENTITY_MODEL_PHOTO, ENTITY_VERSION, newPhotos);
            List<UUID> addedIds = addedIdsFuture.get();
            newPhotosCount = addedIds.size();
        }
        lastIngestionTime = Instant.now();
        logger.info("Ingestion completed. New photos added: {}", newPhotosCount);
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "success");
        resp.put("message", "Photos ingested and gallery updated.");
        resp.put("newPhotosCount", newPhotosCount);
        return ResponseEntity.ok(resp);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllPhotos() throws Exception {
        logger.info("Fetching all photos for gallery display.");
        CompletableFuture<JsonNode> photosFuture = entityService.getItems(ENTITY_MODEL_PHOTO, ENTITY_VERSION);
        JsonNode photosArray = photosFuture.get();
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (JsonNode node : photosArray) {
            Map<String, Object> summary = new HashMap<>();
            summary.put("id", node.path("id").asText());
            summary.put("title", node.path("title").asText());
            summary.put("thumbnailUrl", node.path("thumbnailUrl").asText());
            summary.put("views", 0); // Views not tracked here
            summaries.add(summary);
        }
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{photoId}")
    public ResponseEntity<Map<String, Object>> getPhotoDetail(@PathVariable @NotBlank String photoId) throws Exception {
        logger.info("Fetching photo details for photoId={}", photoId);
        SearchConditionRequest photoCondition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", photoId));
        CompletableFuture<JsonNode> photoArrayFuture = entityService.getItemsByCondition(ENTITY_MODEL_PHOTO, ENTITY_VERSION, photoCondition);
        JsonNode photoArray = photoArrayFuture.get();
        if (photoArray.isEmpty()) {
            throw new ResponseStatusException(ResponseStatusException.Status.NOT_FOUND, "Photo not found");
        }
        JsonNode photoNode = photoArray.get(0);

        SearchConditionRequest commentCondition = SearchConditionRequest.group("AND",
                Condition.of("$.photoId", "EQUALS", photoId));
        CompletableFuture<JsonNode> commentsFuture = entityService.getItemsByCondition(ENTITY_MODEL_COMMENT, ENTITY_VERSION, commentCondition);
        JsonNode commentsArray = commentsFuture.get();
        List<Map<String, Object>> comments = new ArrayList<>();
        for (JsonNode cNode : commentsArray) {
            Map<String, Object> commentMap = new HashMap<>();
            commentMap.put("commentId", cNode.path("commentId").asText());
            commentMap.put("author", cNode.path("author").asText());
            commentMap.put("text", cNode.path("text").asText());
            commentMap.put("createdAt", cNode.path("createdAt").asText());
            comments.add(commentMap);
        }
        Map<String, Object> detail = new HashMap<>();
        detail.put("id", photoNode.path("id").asText());
        detail.put("title", photoNode.path("title").asText());
        detail.put("imageUrl", photoNode.path("imageUrl").asText());
        detail.put("views", 0); // Views not tracked
        detail.put("comments", comments);
        return ResponseEntity.ok(detail);
    }

    @PostMapping("/{photoId}/comment")
    public ResponseEntity<Map<String, Object>> addComment(
            @PathVariable @NotBlank String photoId,
            @RequestBody @Valid CommentCreateRequest request) throws Exception {
        logger.info("Adding comment to photoId={}, author={}", photoId, request.getAuthor());
        SearchConditionRequest photoCondition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", photoId));
        CompletableFuture<JsonNode> photoArrayFuture = entityService.getItemsByCondition(ENTITY_MODEL_PHOTO, ENTITY_VERSION, photoCondition);
        JsonNode photoArray = photoArrayFuture.get();
        if (photoArray.isEmpty()) {
            throw new ResponseStatusException(ResponseStatusException.Status.NOT_FOUND, "Photo not found");
        }
        String commentId = UUID.randomUUID().toString();
        Map<String, Object> commentWithPhotoId = new HashMap<>();
        commentWithPhotoId.put("commentId", commentId);
        commentWithPhotoId.put("author", request.getAuthor());
        commentWithPhotoId.put("text", request.getText());
        commentWithPhotoId.put("createdAt", Instant.now().toString());
        commentWithPhotoId.put("photoId", photoId);
        CompletableFuture<UUID> addedIdFuture = entityService.addItem(ENTITY_MODEL_COMMENT, ENTITY_VERSION, commentWithPhotoId);
        addedIdFuture.get();
        logger.info("Comment added with commentId={}", commentId);
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "success");
        resp.put("commentId", commentId);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/reports/monthly-most-viewed")
    public ResponseEntity<Map<String, Object>> generateMonthlyReport(@RequestBody @Valid MonthlyReportRequest request) throws Exception {
        logger.info("Generating monthly most viewed report for month={}", request.getMonth());
        CompletableFuture<JsonNode> photosFuture = entityService.getItems(ENTITY_MODEL_PHOTO, ENTITY_VERSION);
        JsonNode photosArray = photosFuture.get();
        List<Map<String, Object>> mostViewed = new ArrayList<>();
        for (JsonNode pNode : photosArray) {
            String id = pNode.path("id").asText();
            String title = pNode.path("title").asText();
            SearchConditionRequest commentCondition = SearchConditionRequest.group("AND",
                    Condition.of("$.photoId", "EQUALS", id));
            CompletableFuture<JsonNode> commentsFuture = entityService.getItemsByCondition(ENTITY_MODEL_COMMENT, ENTITY_VERSION, commentCondition);
            JsonNode commentsArray = commentsFuture.get();
            int views = commentsArray.size();
            Map<String, Object> mvPhoto = new HashMap<>();
            mvPhoto.put("id", id);
            mvPhoto.put("title", title);
            mvPhoto.put("views", views);
            mostViewed.add(mvPhoto);
        }
        mostViewed.sort((a, b) -> Integer.compare((int) b.get("views"), (int) a.get("views")));
        Map<String, Object> resp = new HashMap<>();
        resp.put("month", request.getMonth());
        resp.put("mostViewedPhotos", mostViewed);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/notifications/send-new-photos")
    public ResponseEntity<Map<String, Object>> notifyNewPhotos() throws Exception {
        logger.info("Triggering notifications for new photos since last ingestion at {}", lastIngestionTime);
        CompletableFuture<JsonNode> photosFuture = entityService.getItems(ENTITY_MODEL_PHOTO, ENTITY_VERSION);
        int notifiedCount = photosFuture.get().size();
        CompletableFuture.runAsync(() -> logger.info("Sending notifications to users about {} new photos (prototype only)", notifiedCount));
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "success");
        resp.put("notifiedCount", notifiedCount);
        return ResponseEntity.ok(resp);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        logger.error("Handled exception: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    public static class CommentCreateRequest {
        private String author;
        @NotBlank(message = "Comment text must not be blank")
        private String text;

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    public static class MonthlyReportRequest {
        @NotBlank(message = "Month must be specified")
        @Pattern(regexp = "\\d{4}-\\d{2}", message = "Month must be in YYYY-MM format")
        private String month;

        public String getMonth() {
            return month;
        }

        public void setMonth(String month) {
            this.month = month;
        }
    }
}