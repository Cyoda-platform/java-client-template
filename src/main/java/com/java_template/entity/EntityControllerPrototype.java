package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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
import java.util.concurrent.ConcurrentHashMap;

@Validated
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, Photo> photos = new ConcurrentHashMap<>();
    private final Map<String, List<Comment>> photoComments = new ConcurrentHashMap<>();
    private final Map<String, Integer> photoViewCounts = new ConcurrentHashMap<>();
    private final List<Notification> notifications = Collections.synchronizedList(new ArrayList<>());

    private static final String EXTERNAL_API_URL = "https://fakerestapi.azurewebsites.net/api/v1/CoverPhotos";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Photo {
        private String id;
        private String title;
        private String description;
        private String thumbnailUrl;
        private String imageUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Comment {
        private String id;
        private String user;
        private String comment;
        private Instant timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Notification {
        private String id;
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

    @PostMapping("/photos/ingest")
    public IngestResponse ingestPhotos() {
        logger.info("Starting ingestion from external API: {}", EXTERNAL_API_URL);
        try {
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
            JsonNode rootNode = objectMapper.readTree(body);
            if (!rootNode.isArray()) {
                logger.error("Unexpected JSON format: expected array");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unexpected JSON format from external API");
            }
            int ingestedCount = 0;
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
                Photo photo = new Photo(id, title, description, thumbnailUrl, imageUrl);
                photos.put(id, photo);
                photoViewCounts.putIfAbsent(id, 0);
                photoComments.putIfAbsent(id, Collections.synchronizedList(new ArrayList<>()));
                ingestedCount++;
            }
            notifications.add(new Notification(UUID.randomUUID().toString(),
                    "New cover photos have been added to the gallery.",
                    Instant.now(),
                    false));
            logger.info("Ingested {} photos successfully", ingestedCount);
            return new IngestResponse("success", ingestedCount);
        } catch (Exception e) {
            logger.error("Exception during ingestion: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error ingesting cover photos");
        }
    }

    @GetMapping("/photos")
    public Map<String, Object> getPhotos(@RequestParam(defaultValue = "1") @Min(1) int page,
                                         @RequestParam(defaultValue = "20") @Min(1) int size) {
        logger.info("Fetching photos page={} size={}", page, size);
        List<Photo> photoList = new ArrayList<>(photos.values());
        int total = photoList.size();
        int fromIndex = Math.min((page - 1) * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<Map<String, String>> pagePhotos = new ArrayList<>();
        for (Photo p : photoList.subList(fromIndex, toIndex)) {
            Map<String, String> entry = new HashMap<>();
            entry.put("id", p.getId());
            entry.put("title", p.getTitle());
            entry.put("thumbnailUrl", p.getThumbnailUrl());
            pagePhotos.add(entry);
        }
        return Map.of("photos", pagePhotos, "page", page, "size", size, "total", total);
    }

    @GetMapping("/photos/{photoId}")
    public Map<String, Object> getPhotoDetails(@PathVariable String photoId) {
        logger.info("Fetching details for photoId={}", photoId);
        Photo photo = photos.get(photoId);
        if (photo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found");
        }
        int viewCount = photoViewCounts.getOrDefault(photoId, 0);
        List<Comment> comments = photoComments.getOrDefault(photoId, Collections.emptyList());
        return Map.of(
                "id", photo.getId(),
                "title", photo.getTitle(),
                "imageUrl", photo.getImageUrl(),
                "description", photo.getDescription(),
                "viewCount", viewCount,
                "comments", comments
        );
    }

    @PostMapping(value = "/photos/{photoId}/comments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> postComment(@PathVariable String photoId,
                                           @RequestBody @Valid CommentRequest request) {
        logger.info("Adding comment to photoId={} by user={}", photoId, request.getUser());
        Photo photo = photos.get(photoId);
        if (photo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found");
        }
        Comment comment = new Comment(UUID.randomUUID().toString(), request.getUser(), request.getComment(), Instant.now());
        photoComments.computeIfAbsent(photoId, k -> Collections.synchronizedList(new ArrayList<>())).add(comment);
        logger.info("Comment added with id={}", comment.getId());
        return Map.of("status", "success", "commentId", comment.getId());
    }

    @PostMapping("/photos/{photoId}/view")
    public Map<String, Object> incrementViewCount(@PathVariable String photoId) {
        logger.info("Incrementing view count for photoId={}", photoId);
        if (!photos.containsKey(photoId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found");
        }
        photoViewCounts.merge(photoId, 1, Integer::sum);
        int newCount = photoViewCounts.get(photoId);
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

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, String> handleResponseStatus(ResponseStatusException ex) {
        logger.error("Handled exception: {} - {}", ex.getStatusCode(), ex.getReason());
        return Map.of("error", ex.getReason());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleGeneric(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        return Map.of("error", "Internal server error");
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
        logger.info("EntityControllerPrototype initialized");
    }
}