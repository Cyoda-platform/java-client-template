package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/cyoda/photos")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private static final String ENTITY_MODEL_PHOTO = "Photo";
    private static final String ENTITY_MODEL_COMMENT = "Comment";

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Instant lastIngestionTime = Instant.EPOCH;

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingestPhotos() throws Exception {
        logger.info("Starting ingestion from Fakerest API.");
        String response = restTemplate.getForObject(URI.create("https://fakerestapi.azurewebsites.net/api/v1/Books"), String.class);
        JsonNode root = objectMapper.readTree(response);
        if (!root.isArray()) {
            logger.error("Unexpected JSON structure from Fakerest API: expected array.");
            throw new ResponseStatusException(ResponseStatusException.Status.INTERNAL_SERVER_ERROR, "Invalid external API response");
        }

        List<Photo> newPhotos = new ArrayList<>();
        for (JsonNode node : root) {
            String id = node.path("id").asText();
            String title = node.path("title").asText();
            String imageUrl = node.path("excerpt").asText();
            if (imageUrl == null || imageUrl.isBlank()) {
                imageUrl = "https://via.placeholder.com/600x400?text=No+Image"; // TODO: replace with real image URL if available
            }
            // Check if photo exists by condition on 'id' field
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.id", "EQUALS", id));
            CompletableFuture<ObjectNode> existing = entityService.getItemsByCondition(ENTITY_MODEL_PHOTO, ENTITY_VERSION, condition)
                    .thenApply(arrayNode -> arrayNode.isEmpty() ? null : (ObjectNode) arrayNode.get(0));
            ObjectNode existingPhoto = existing.get();
            if (existingPhoto == null) {
                Photo photo = new Photo(id, title, imageUrl, imageUrl);
                newPhotos.add(photo);
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
    public ResponseEntity<List<PhotoSummary>> getAllPhotos() throws Exception {
        logger.info("Fetching all photos for gallery display.");
        CompletableFuture<ArrayNode> photosFuture = entityService.getItems(ENTITY_MODEL_PHOTO, ENTITY_VERSION);
        ArrayNode photosArray = photosFuture.get();
        List<PhotoSummary> summaries = new ArrayList<>();
        for (JsonNode node : photosArray) {
            String id = node.path("id").asText();
            String technicalId = node.path("technicalId").asText(null);
            String title = node.path("title").asText();
            String thumbnailUrl = node.path("thumbnailUrl").asText();
            // For views, get comments count or zero
            int views = 0;
            // Views are not stored in entityService; keep as 0 or can calculate via comments count
            summaries.add(new PhotoSummary(id, title, thumbnailUrl, views));
        }
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{photoId}")
    public ResponseEntity<PhotoDetail> getPhotoDetail(@PathVariable @NotBlank String photoId) throws Exception {
        logger.info("Fetching photo details for photoId={}", photoId);
        // Find photo by id
        SearchConditionRequest photoCondition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", photoId));
        CompletableFuture<ArrayNode> photoArrayFuture = entityService.getItemsByCondition(ENTITY_MODEL_PHOTO, ENTITY_VERSION, photoCondition);
        ArrayNode photoArray = photoArrayFuture.get();
        if (photoArray.isEmpty()) {
            throw new ResponseStatusException(ResponseStatusException.Status.NOT_FOUND, "Photo not found");
        }
        JsonNode photoNode = photoArray.get(0);
        String id = photoNode.path("id").asText();
        String title = photoNode.path("title").asText();
        String imageUrl = photoNode.path("imageUrl").asText();
        String technicalId = photoNode.path("technicalId").asText(null);

        // Get comments for this photo: condition on photoId field
        SearchConditionRequest commentCondition = SearchConditionRequest.group("AND",
                Condition.of("$.photoId", "EQUALS", id));
        CompletableFuture<ArrayNode> commentsFuture = entityService.getItemsByCondition(ENTITY_MODEL_COMMENT, ENTITY_VERSION, commentCondition);
        ArrayNode commentsArray = commentsFuture.get();
        List<Comment> comments = new ArrayList<>();
        for (JsonNode cNode : commentsArray) {
            String commentId = cNode.path("commentId").asText();
            String author = cNode.path("author").asText();
            String text = cNode.path("text").asText();
            Instant createdAt = Instant.parse(cNode.path("createdAt").asText());
            comments.add(new Comment(commentId, author, text, createdAt));
        }
        // Views are not tracked, set 0
        int views = 0;
        PhotoDetail detail = new PhotoDetail(id, title, imageUrl, views, comments);
        return ResponseEntity.ok(detail);
    }

    @PostMapping("/{photoId}/comment")
    public ResponseEntity<Map<String, Object>> addComment(
            @PathVariable @NotBlank String photoId,
            @RequestBody @Valid CommentCreateRequest request) throws Exception {
        logger.info("Adding comment to photoId={}, author={}", photoId, request.getAuthor());
        // Check photo exists
        SearchConditionRequest photoCondition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", photoId));
        CompletableFuture<ArrayNode> photoArrayFuture = entityService.getItemsByCondition(ENTITY_MODEL_PHOTO, ENTITY_VERSION, photoCondition);
        ArrayNode photoArray = photoArrayFuture.get();
        if (photoArray.isEmpty()) {
            throw new ResponseStatusException(ResponseStatusException.Status.NOT_FOUND, "Photo not found");
        }
        String commentId = UUID.randomUUID().toString();
        Comment comment = new Comment(commentId, request.getAuthor(), request.getText(), Instant.now());
        // Add photoId field to comment for relation
        CommentWithPhotoId commentWithPhotoId = new CommentWithPhotoId(commentId, request.getAuthor(), request.getText(), Instant.now(), photoId);
        CompletableFuture<UUID> addedIdFuture = entityService.addItem(ENTITY_MODEL_COMMENT, ENTITY_VERSION, commentWithPhotoId);
        addedIdFuture.get();
        logger.info("Comment added with commentId={}", commentId);
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "success");
        resp.put("commentId", commentId);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/reports/monthly-most-viewed")
    public ResponseEntity<MonthlyReportResponse> generateMonthlyReport(@RequestBody @Valid MonthlyReportRequest request) throws Exception {
        logger.info("Generating monthly most viewed report for month={}", request.getMonth());
        // Since views are not tracked, use comments count as proxy for popularity
        CompletableFuture<ArrayNode> photosFuture = entityService.getItems(ENTITY_MODEL_PHOTO, ENTITY_VERSION);
        ArrayNode photosArray = photosFuture.get();
        List<MostViewedPhoto> mostViewed = new ArrayList<>();
        for (JsonNode pNode : photosArray) {
            String id = pNode.path("id").asText();
            String title = pNode.path("title").asText();
            // Count comments for photo
            SearchConditionRequest commentCondition = SearchConditionRequest.group("AND",
                    Condition.of("$.photoId", "EQUALS", id));
            CompletableFuture<ArrayNode> commentsFuture = entityService.getItemsByCondition(ENTITY_MODEL_COMMENT, ENTITY_VERSION, commentCondition);
            ArrayNode commentsArray = commentsFuture.get();
            int views = commentsArray.size();
            mostViewed.add(new MostViewedPhoto(id, title, views));
        }
        mostViewed.sort((a, b) -> Integer.compare(b.getViews(), a.getViews()));
        MonthlyReportResponse resp = new MonthlyReportResponse(request.getMonth(), mostViewed);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/notifications/send-new-photos")
    public ResponseEntity<Map<String, Object>> notifyNewPhotos() throws Exception {
        logger.info("Triggering notifications for new photos since last ingestion at {}", lastIngestionTime);
        // Count photos as notified count
        CompletableFuture<ArrayNode> photosFuture = entityService.getItems(ENTITY_MODEL_PHOTO, ENTITY_VERSION);
        int notifiedCount = photosFuture.get().size();
        CompletableFuture.runAsync(() -> {
            logger.info("Sending notifications to users about {} new photos (prototype only)", notifiedCount);
        });
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

    @Data
    static class Photo {
        private final String id;
        private final String title;
        private final String imageUrl;
        private final String thumbnailUrl;
    }

    @Data
    static class PhotoSummary {
        private final String id;
        private final String title;
        private final String thumbnailUrl;
        private final int views;
    }

    @Data
    static class PhotoDetail {
        private final String id;
        private final String title;
        private final String imageUrl;
        private final int views;
        private final List<Comment> comments;
    }

    @Data
    static class Comment {
        private final String commentId;
        private final String author;
        private final String text;
        private final Instant createdAt;
    }

    @Data
    static class CommentWithPhotoId extends Comment {
        private final String photoId;

        public CommentWithPhotoId(String commentId, String author, String text, Instant createdAt, String photoId) {
            super(commentId, author, text, createdAt);
            this.photoId = photoId;
        }
    }

    @Data
    static class CommentCreateRequest {
        private String author;
        @NotBlank(message = "Comment text must not be blank")
        private String text;
    }

    @Data
    static class MonthlyReportRequest {
        @NotBlank(message = "Month must be specified")
        @Pattern(regexp = "\\d{4}-\\d{2}", message = "Month must be in YYYY-MM format")
        private String month;
    }

    @Data
    static class MostViewedPhoto {
        private final String id;
        private final String title;
        private final int views;
    }

    @Data
    static class MonthlyReportResponse {
        private final String month;
        private final List<MostViewedPhoto> mostViewedPhotos;
    }
}