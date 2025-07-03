package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/prototype/pets")
public class EntityControllerPrototype {

    private final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();
    private long petIdSequence = 1000;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Long id;
        private String name;
        private String status;
        private List<String> tags;
        private String category;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncRequest {
        @Pattern(regexp = "available|pending|sold", message = "Status must be available, pending, or sold")
        private String status;
        @Size(min = 1, message = "At least one tag if tags provided")
        private List<@NotBlank String> tags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetsResponse {
        private List<Pet> pets;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddPetRequest {
        @NotBlank(message = "Name is required")
        private String name;
        @NotBlank(message = "Status is required")
        @Pattern(regexp = "available|pending|sold", message = "Status must be available, pending, or sold")
        private String status;
        private List<@NotBlank String> tags;
        @NotBlank(message = "Category is required")
        private String category;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddPetResponse {
        private Long id;
        private String name;
        private String status;
        private List<String> tags;
        private String category;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateStatusRequest {
        @NotBlank(message = "Status is required")
        @Pattern(regexp = "available|pending|sold", message = "Status must be available, pending, or sold")
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateStatusResponse {
        private Long id;
        private String status;
        private String message;
    }

    @PostMapping("/sync")
    public ResponseEntity<PetsResponse> syncPetsFromExternal(@RequestBody @Valid SyncRequest request) {
        logger.info("Received sync request: {}", request);
        String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + 
            (request.getStatus() != null ? request.getStatus() : "available");
        try {
            String raw = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(raw);
            if (!root.isArray()) {
                logger.error("Expected array from external API");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid external API response format");
            }
            List<Pet> synced = new ArrayList<>();
            for (JsonNode node : root) {
                Long id = node.hasNonNull("id") ? node.get("id").asLong() : null;
                String name = node.path("name").asText("Unnamed");
                String status = node.path("status").asText("unknown");
                List<String> tags = new ArrayList<>();
                if (node.has("tags") && node.get("tags").isArray()) {
                    for (JsonNode t : node.get("tags")) {
                        t.path("name").asText(null);
                        if (t.hasNonNull("name")) tags.add(t.get("name").asText());
                    }
                }
                String category = node.path("category").path("name").asText(null);
                if (request.getTags() != null && !request.getTags().isEmpty()) {
                    boolean match = false;
                    for (String tf : request.getTags()) {
                        if (tags.contains(tf)) { match = true; break; }
                    }
                    if (!match) continue;
                }
                Pet pet = new Pet(id != null ? id : generateNextPetId(), name, status, tags, category);
                petStore.put(pet.getId(), pet);
                synced.add(pet);
            }
            logger.info("Synced {} pets", synced.size());
            return ResponseEntity.ok(new PetsResponse(synced));
        } catch (Exception e) {
            logger.error("Sync error", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to sync external API");
        }
    }

    @GetMapping
    public ResponseEntity<PetsResponse> getAllPets() {
        logger.info("Retrieving all pets, count={}", petStore.size());
        return ResponseEntity.ok(new PetsResponse(new ArrayList<>(petStore.values())));
    }

    @PostMapping
    public ResponseEntity<AddPetResponse> addNewPet(@RequestBody @Valid AddPetRequest request) {
        Long id = generateNextPetId();
        Pet pet = new Pet(id, request.getName(), request.getStatus(), 
            request.getTags() != null ? request.getTags() : Collections.emptyList(), request.getCategory());
        petStore.put(id, pet);
        logger.info("Added pet id={}", id);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new AddPetResponse(id, pet.getName(), pet.getStatus(), pet.getTags(), pet.getCategory(), "Pet added successfully"));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<UpdateStatusResponse> updatePetStatus(
            @PathVariable Long id, @RequestBody @Valid UpdateStatusRequest request) {
        Pet pet = petStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        pet.setStatus(request.getStatus());
        petStore.put(id, pet);
        logger.info("Updated pet id={} to status={}", id, request.getStatus());
        return ResponseEntity.ok(new UpdateStatusResponse(id, request.getStatus(), "Pet status updated"));
    }

    private synchronized Long generateNextPetId() {
        return ++petIdSequence;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatusException(ResponseStatusException ex) {
        Map<String, Object> err = new HashMap<>();
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason() != null ? ex.getReason() : "Error");
        logger.error("Handled status exception: {}", err);
        return ResponseEntity.status(ex.getStatusCode()).body(err);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        Map<String, Object> err = new HashMap<>();
        err.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        err.put("message", "Internal server error");
        logger.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
    }
}