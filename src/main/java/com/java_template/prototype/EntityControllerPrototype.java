package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
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
import java.util.concurrent.ConcurrentHashMap;

@Validated
@RestController
@RequestMapping(path = "/prototype/api")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String FAKE_REST_API_URL = "https://fakerestapi.azurewebsites.net/api/v1/Books";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Photo> photos = new ConcurrentHashMap<>();
    private final Map<String, Integer> photoViews = new ConcurrentHashMap<>();
    private final Map<String, List<Comment>> photoComments = new ConcurrentHashMap<>();
    private Instant lastIngestionTime = Instant.EPOCH;

    @PostMapping("/photos/ingest")
    public ResponseEntity<Map<String, Object>> ingestPhotos() {
        logger.info("Starting ingestion from Fakerest API.");
        try {
            String response = restTemplate.getForObject(URI.create(FAKE_REST_API_URL), String.class);
            JsonNode root = objectMapper.readTree(response);
            if (!root.isArray()) {
                logger.error("Unexpected JSON structure from Fakerest API: expected array.");
                throw new ResponseStatusException(ResponseStatusException.Status.INTERNAL_SERVER_ERROR, "Invalid external API response");
            }
            int newPhotosCount = 0;
            for (JsonNode node : root) {
                String id = node.path("id").asText();
                String title = node.path("title").asText();
                String imageUrl = node.path("excerpt").asText();
                if (imageUrl == null || imageUrl.isBlank()) {
                    imageUrl = "https://via.placeholder.com/600x400?text=No+Image"; // TODO: replace with real image URL if available
                }
                if (!photos.containsKey(id)) {
                    Photo photo = new Photo(id, title, imageUrl, imageUrl);
                    photos.put(id, photo);
                    photoViews.put(id, 0);
                    photoComments.put(id, Collections.synchronizedList(new ArrayList<>()));
                    newPhotosCount++;
                }
            }
            lastIngestionTime = Instant.now();
            logger.info("Ingestion completed. New photos added: {}", newPhotosCount);
            Map<String, Object> resp = new HashMap<>();
            resp.put("status", "success");
            resp.put("message", "Photos ingested and gallery updated.");
            resp.put("newPhotosCount", newPhotosCount);
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            logger.error("Failed ingestion: {}", ex.getMessage(), ex);
            throw new ResponseStatusException(ResponseStatusException.Status.INTERNAL_SERVER_ERROR, "Failed to ingest photos: " + ex.getMessage());
        }
    }

    @GetMapping("/photos")
    public ResponseEntity<List<PhotoSummary>> getAllPhotos() {
        logger.info("Fetching all photos for gallery display.");
        List<PhotoSummary> summaries = new ArrayList<>();
        for (Photo p : photos.values()) {
            int views = photoViews.getOrDefault(p.getId(), 0);
            summaries.add(new PhotoSummary(p.getId(), p.getTitle(), p.getThumbnailUrl(), views));
        }
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/photos/{photoId}")
    public ResponseEntity<PhotoDetail> getPhotoDetail(@PathVariable @NotBlank String photoId) {
        logger.info("Fetching photo details for photoId={}", photoId);
        Photo photo = photos.get(photoId);
        if (photo == null) {
            throw new ResponseStatusException(ResponseStatusException.Status.NOT_FOUND, "Photo not found");
        }
        photoViews.merge(photoId, 1, Integer::sum);
        List<Comment> comments = photoComments.getOrDefault(photoId, Collections.emptyList());
        PhotoDetail detail = new PhotoDetail(photo.getId(), photo.getTitle(), photo.getImageUrl(), photoViews.get(photoId), comments);
        return ResponseEntity.ok(detail);
    }

    @PostMapping("/photos/{photoId}/comment")
    public ResponseEntity<Map<String, Object>> addComment(
            @PathVariable @NotBlank String photoId,
            @RequestBody @Valid CommentCreateRequest request) {
        logger.info("Adding comment to photoId={}, author={}", photoId, request.getAuthor());
        Photo photo = photos.get(photoId);
        if (photo == null) {
            throw new ResponseStatusException(ResponseStatusException.Status.NOT_FOUND, "Photo not found");
        }
        String commentId = UUID.randomUUID().toString();
        Comment comment = new Comment(commentId, request.getAuthor(), request.getText(), Instant.now());
        photoComments.computeIfAbsent(photoId, k -> Collections.synchronizedList(new ArrayList<>())).add(comment);
        logger.info("Comment added with commentId={}", commentId);
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "success");
        resp.put("commentId", commentId);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/reports/monthly-most-viewed")
    public ResponseEntity<MonthlyReportResponse> generateMonthlyReport(@RequestBody @Valid MonthlyReportRequest request) {
        logger.info("Generating monthly most viewed report for month={}", request.getMonth());
        List<MostViewedPhoto> mostViewed = new ArrayList<>();
        for (Photo p : photos.values()) {
            int views = photoViews.getOrDefault(p.getId(), 0);
            mostViewed.add(new MostViewedPhoto(p.getId(), p.getTitle(), views));
        }
        mostViewed.sort((a, b) -> Integer.compare(b.getViews(), a.getViews()));
        MonthlyReportResponse resp = new MonthlyReportResponse(request.getMonth(), mostViewed);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/notifications/send-new-photos")
    public ResponseEntity<Map<String, Object>> notifyNewPhotos() {
        logger.info("Triggering notifications for new photos since last ingestion at {}", lastIngestionTime);
        int notifiedCount = photos.size(); // prototype: count all photos
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