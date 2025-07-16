package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@Validated
@RequestMapping(path = "/prototype/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String EXTERNAL_PETSTORE_API = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, Pet> petStore = new ConcurrentHashMap<>();

    @Data
    public static class Pet {
        private String id;
        private String name;
        private String type;
        private String status;
        private String description;
        private String funFact;
    }

    @Data
    public static class FetchPetsRequest {
        @Pattern(regexp = "cat|dog", message = "filterType must be 'cat' or 'dog'")
        private String filterType;
        private Boolean includeFunFacts;
    }

    @Data
    public static class FetchPetsResponse {
        private int processedCount;
        private String message;
    }

    @Data
    public static class AddPetRequest {
        @NotBlank @Size(max = 100)
        private String name;
        @NotBlank @Pattern(regexp = "cat|dog", message = "type must be 'cat' or 'dog'")
        private String type;
        @NotBlank @Pattern(regexp = "available|sold", message = "status must be 'available' or 'sold'")
        private String status;
        @Size(max = 255)
        private String description;
    }

    @Data
    public static class AddPetResponse {
        private String id;
        private String message;
    }

    @Data
    public static class UpdateStatusRequest {
        @NotBlank @Pattern(regexp = "available|sold", message = "status must be 'available' or 'sold'")
        private String status;
    }

    @Data
    public static class UpdateStatusResponse {
        private String id;
        private String message;
    }

    @Data
    public static class GetPetsQuery {
        @Pattern(regexp = "cat|dog", message = "type must be 'cat' or 'dog'")
        private String type;
        @Pattern(regexp = "available|sold", message = "status must be 'available' or 'sold'")
        private String status;
    }

    @GetMapping
    public List<Pet> getPets(@Valid @ModelAttribute GetPetsQuery query) {
        // Using @ModelAttribute to bind query params in GET
        logger.info("GET /prototype/pets called with type={} status={}", query.getType(), query.getStatus());
        return petStore.values().stream()
            .filter(p -> query.getType() == null || p.getType().equalsIgnoreCase(query.getType()))
            .filter(p -> query.getStatus() == null || p.getStatus().equalsIgnoreCase(query.getStatus()))
            .toList();
    }

    @PostMapping("/fetch")
    public ResponseEntity<FetchPetsResponse> fetchAndProcessPets(@RequestBody @Valid FetchPetsRequest request) {
        logger.info("POST /prototype/pets/fetch called filterType={} includeFunFacts={}",
            request.getFilterType(), request.getIncludeFunFacts());
        try {
            String url = EXTERNAL_PETSTORE_API;
            String json = restTemplate.getForObject(URI.create(url), String.class);
            JsonNode array = objectMapper.readTree(json);
            int count = 0;
            for (JsonNode n : array) {
                String name = n.path("name").asText(null);
                String cat = n.path("category").path("name").asText("unknown");
                if (request.getFilterType() != null && !request.getFilterType().equalsIgnoreCase(cat)) {
                    continue;
                }
                if (name == null) continue;
                Pet p = new Pet();
                p.setId(UUID.randomUUID().toString());
                p.setName(name);
                p.setType(cat.toLowerCase());
                p.setStatus("available");
                p.setDescription("Imported from external API");
                if (Boolean.TRUE.equals(request.getIncludeFunFacts())) {
                    p.setFunFact("Loves sunny spots!"); // TODO: replace with real fun facts source
                }
                petStore.put(p.getId(), p);
                count++;
            }
            FetchPetsResponse resp = new FetchPetsResponse();
            resp.setProcessedCount(count);
            resp.setMessage("Pets fetched and processed successfully");
            logger.info("Processed {} pets", count);
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            logger.error("Error in fetchAndProcessPets", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Fetch/process failed");
        }
    }

    @PostMapping
    public ResponseEntity<AddPetResponse> addPet(@RequestBody @Valid AddPetRequest request) {
        logger.info("POST /prototype/pets addPet name={} type={} status={}",
            request.getName(), request.getType(), request.getStatus());
        try {
            Pet p = new Pet();
            p.setId(UUID.randomUUID().toString());
            p.setName(request.getName());
            p.setType(request.getType().toLowerCase());
            p.setStatus(request.getStatus().toLowerCase());
            p.setDescription(request.getDescription());
            petStore.put(p.getId(), p);
            AddPetResponse resp = new AddPetResponse();
            resp.setId(p.getId());
            resp.setMessage("Pet added successfully");
            logger.info("Added pet id={}", p.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (Exception ex) {
            logger.error("Error in addPet", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Add pet failed");
        }
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<UpdateStatusResponse> updateStatus(@PathVariable String id,
        @RequestBody @Valid UpdateStatusRequest request) {
        logger.info("POST /prototype/pets/{}/status called newStatus={}", id, request.getStatus());
        Pet p = petStore.get(id);
        if (p == null) {
            logger.error("Pet not found id={}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        try {
            p.setStatus(request.getStatus().toLowerCase());
            UpdateStatusResponse resp = new UpdateStatusResponse();
            resp.setId(id);
            resp.setMessage("Pet status updated successfully");
            logger.info("Updated status for id={}", id);
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            logger.error("Error in updateStatus", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Update status failed");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> handleEx(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode())
            .body(Map.of("error", ex.getStatusCode().toString(), "message", ex.getReason()));
    }
}