import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/prototype/api/photos")
@Validated
public class EntityControllerPrototype {

    private static final String FAKEREST_API_URL = "https://fakerestapi.azurewebsites.net/api/v1/Books";
    private final Map<Long, Photo> photoStorage = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/ingest")
    public ResponseEntity<String> ingestData() {
        RestTemplate restTemplate = new RestTemplate();
        try {
            String response = restTemplate.getForObject(FAKEREST_API_URL, String.class);
            JsonNode photos = objectMapper.readTree(response);

            if (photos.isArray()) {
                for (JsonNode photo : photos) {
                    long id = photo.path("ID").asLong();
                    String title = photo.path("Title").asText();
                    String url = "https://example.com/photo" + id + ".jpg"; // Placeholder for photo URL
                    photoStorage.put(id, new Photo(id, title, url, 0));
                }
            }
            return ResponseEntity.ok("Data ingestion completed");
        } catch (Exception e) {
            log.error("Error during data ingestion", e);
            throw new ResponseStatusException(500, "Data ingestion failed");
        }
    }

    @GetMapping("/gallery")
    public ResponseEntity<Iterable<Photo>> getGallery() {
        return ResponseEntity.ok(photoStorage.values());
    }

    @PostMapping("/{photoId}/view")
    public ResponseEntity<String> viewAndComment(@PathVariable Long photoId, @RequestBody @Valid CommentRequest commentRequest) {
        Photo photo = photoStorage.get(photoId);
        if (photo == null) {
            throw new ResponseStatusException(404, "Photo not found");
        }
        photo.setViews(photo.getViews() + 1);
        log.info("Comment added: {}", commentRequest.getComment());
        return ResponseEntity.ok("View and comment recorded");
    }

    @PostMapping("/reports/monthly")
    public ResponseEntity<String> generateMonthlyReport() {
        CompletableFuture.runAsync(() -> {
            log.info("Report generation started");
        });
        return ResponseEntity.ok("Report generation initiated");
    }

    @PostMapping("/notify")
    public ResponseEntity<String> notifyUsers(@RequestBody @Valid NotificationRequest notificationRequest) {
        CompletableFuture.runAsync(() -> {
            log.info("Notifying users via {}", notificationRequest.getNotificationType());
        });
        return ResponseEntity.ok("Notifications sent");
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class Photo {
        private Long id;
        private String title;
        private String url;
        private int views;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class CommentRequest {
        @NotNull
        @Size(min = 1, max = 255)
        private String comment;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class NotificationRequest {
        @NotNull
        @Pattern(regexp = "email|push", message = "Notification type must be either 'email' or 'push'")
        private String notificationType;
    }
}