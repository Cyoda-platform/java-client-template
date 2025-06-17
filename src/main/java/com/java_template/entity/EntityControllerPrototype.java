import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/prototype/api/pets")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Pet> petDataStore = new HashMap<>(); // Mock data store

    @PostMapping("/search") // must be first
    public ResponseEntity<?> searchPets(@RequestBody @Valid SearchCriteria searchCriteria) {
        logger.info("Searching for pets with criteria: {}", searchCriteria);

        try {
            // Mock external API call
            String apiUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + searchCriteria.getStatus();
            String response = restTemplate.getForObject(apiUrl, String.class);

            JsonNode petArray = objectMapper.readTree(response);
            petArray.forEach(petNode -> {
                String petName = petNode.get("name").asText();
                String petId = petNode.get("id").asText();
                petDataStore.put(petId, new Pet(petName, searchCriteria.getSpecies(), searchCriteria.getStatus(), searchCriteria.getCategoryId(), "Available"));
            });

            return ResponseEntity.ok(petDataStore.values());

        } catch (Exception e) {
            logger.error("Error fetching pet details", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error fetching pet details");
        }
    }

    @GetMapping("/results") // must be first
    public ResponseEntity<?> getResults() {
        logger.info("Retrieving pet search results");
        return ResponseEntity.ok(petDataStore.values());
    }

    @PostMapping("/notify") // must be first
    public ResponseEntity<?> notifyUser(@RequestBody @Valid Notification notification) {
        logger.info("Sending notification: {}", notification.getMessage());

        // TODO: Implement actual notification logic
        return ResponseEntity.ok(Map.of("status", "success", "notificationSent", true));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", ex.getStatusCode().toString());
        logger.error("Handling exception: {}", ex.getStatusCode().toString());
        return new ResponseEntity<>(errorResponse, ex.getStatusCode());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class SearchCriteria {
        @NotBlank
        @Size(max = 50)
        private String species;

        @NotBlank
        @Pattern(regexp = "available|pending|sold", message = "Status must be one of: available, pending, sold")
        private String status;

        @NotBlank
        @Size(max = 10)
        private String categoryId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class Pet {
        @NotBlank
        private String name;

        @NotBlank
        private String species;

        @NotBlank
        private String status;

        @NotBlank
        private String category;

        @NotBlank
        private String availabilityStatus;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class Notification {
        @NotBlank
        private String message;
    }
}