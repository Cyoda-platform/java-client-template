package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("prototype/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String EXTERNAL_PETSTORE_BASE = "https://petstore.swagger.io/v2/pet";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Long id;
        @NotBlank
        @Size(max = 50)
        private String name;
        @NotBlank
        @Size(max = 50)
        private String type;
        @NotBlank
        @Size(max = 20)
        private String status;
        @NotNull
        @Size(min = 1)
        private String[] photoUrls;
    }

    @Data
    @NoArgsConstructor
    public static class SearchRequest {
        @Size(max = 30)
        private String type;
        @Size(max = 30)
        private String status;
    }

    @Data
    @NoArgsConstructor
    public static class IdRequest {
        @NotNull
        private Long id;
    }

    @PostMapping("/search")
    public ResponseEntity<JsonNode> searchPets(@RequestBody @Valid SearchRequest request) {
        logger.info("Received searchPets request with type='{}', status='{}'", request.getType(), request.getStatus());
        try {
            String url = EXTERNAL_PETSTORE_BASE + "/findByStatus?status=" +
                    (StringUtils.hasText(request.getStatus()) ? request.getStatus() : "available");
            String raw = restTemplate.getForObject(new URI(url), String.class);
            JsonNode petsNode = objectMapper.readTree(raw);
            if (StringUtils.hasText(request.getType()) && petsNode.isArray()) {
                var arr = objectMapper.createArrayNode();
                petsNode.forEach(petNode -> {
                    JsonNode cat = petNode.get("category");
                    String petType = cat != null && cat.has("name") ? cat.get("name").asText() : "";
                    if (request.getType().equalsIgnoreCase(petType)) {
                        arr.add(petNode);
                    }
                });
                return ResponseEntity.ok(arr);
            }
            return ResponseEntity.ok(petsNode);
        } catch (Exception ex) {
            logger.error("Error while searching pets", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error fetching pets: " + ex.getMessage());
        }
    }

    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> addPet(@RequestBody @Valid Pet newPet) {
        logger.info("Adding new pet: {}", newPet);
        long newId = petStore.keySet().stream().mapToLong(Long::longValue).max().orElse(0L) + 1;
        newPet.setId(newId);
        petStore.put(newId, newPet);
        CompletableFuture.runAsync(() -> {
            try {
                ObjectNode ps = objectMapper.createObjectNode();
                ps.put("id", newId);
                var cat = ps.putObject("category");
                cat.put("id", 0);
                cat.put("name", newPet.getType());
                ps.put("name", newPet.getName());
                var photos = ps.putArray("photoUrls");
                for (String u : newPet.getPhotoUrls()) photos.add(u);
                ps.put("status", newPet.getStatus());
                restTemplate.postForEntity(EXTERNAL_PETSTORE_BASE, ps.toString(), String.class);
                logger.info("External addPet fired for pet id {}", newId);
            } catch (Exception e) {
                logger.error("Failed to add pet externally", e);
            }
        });
        return ResponseEntity.ok(Map.of("id", newId, "message", "Pet added successfully"));
    }

    @PostMapping("/update")
    public ResponseEntity<Map<String, String>> updatePet(@RequestBody @Valid Pet updateRequest) {
        logger.info("Updating pet: {}", updateRequest);
        if (updateRequest.getId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet ID is required");
        Pet existing = petStore.get(updateRequest.getId());
        if (existing == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with id " + updateRequest.getId());
        existing.setName(updateRequest.getName());
        existing.setType(updateRequest.getType());
        existing.setStatus(updateRequest.getStatus());
        existing.setPhotoUrls(updateRequest.getPhotoUrls());
        petStore.put(existing.getId(), existing);
        syncUpdateWithExternalAPI(existing);
        return ResponseEntity.ok(Map.of("message", "Pet updated successfully"));
    }

    @Async
    void syncUpdateWithExternalAPI(Pet pet) {
        try {
            ObjectNode ps = objectMapper.createObjectNode();
            ps.put("id", pet.getId());
            var cat = ps.putObject("category");
            cat.put("id", 0);
            cat.put("name", pet.getType());
            ps.put("name", pet.getName());
            var photos = ps.putArray("photoUrls");
            for (String u : pet.getPhotoUrls()) photos.add(u);
            ps.put("status", pet.getStatus());
            restTemplate.put(EXTERNAL_PETSTORE_BASE, ps.toString());
            logger.info("External updatePet synced for pet id {}", pet.getId());
        } catch (Exception e) {
            logger.error("Failed to sync update externally for pet id {}", pet.getId(), e);
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<Map<String, String>> deletePet(@RequestBody @Valid IdRequest idRequest) {
        logger.info("Deleting pet with id {}", idRequest.getId());
        Pet removed = petStore.remove(idRequest.getId());
        if (removed == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with id " + idRequest.getId());
        return ResponseEntity.ok(Map.of("message", "Pet deleted successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable Long id) {
        logger.info("Retrieving pet by id {}", id);
        Pet pet = petStore.get(id);
        if (pet == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with id " + id);
        return ResponseEntity.ok(pet);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getStatusCode().toString(), "message", ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", HttpStatus.INTERNAL_SERVER_ERROR.toString(), "message", ex.getMessage()));
    }
}
