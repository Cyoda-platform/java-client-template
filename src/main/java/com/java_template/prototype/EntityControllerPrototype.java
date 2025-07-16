package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Validated
@RestController
@RequestMapping(path = "/prototype/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String EXTERNAL_PETSTORE_BASE = "https://petstore.swagger.io/v2/pet";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();

    @PostMapping
    public ResponseEntity<PetResponse> addOrUpdatePet(@RequestBody @Valid AddOrUpdatePetRequest request) {
        logger.info("addOrUpdatePet source={}", request.getSource());
        if ("external".equalsIgnoreCase(request.getSource())) {
            if (request.getPetId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "petId required for external source");
            }
            try {
                String url = EXTERNAL_PETSTORE_BASE + "/" + request.getPetId();
                logger.info("Fetching external pet from {}", url);
                String json = restTemplate.getForObject(url, String.class);
                JsonNode node = objectMapper.readTree(json);
                long id = node.path("id").asLong(-1);
                if (id < 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "External pet not found");
                String name = node.path("name").asText("");
                String status = node.path("status").asText("");
                JsonNode cat = node.path("category");
                String type = cat.isMissingNode() ? "unknown" : cat.path("name").asText("unknown");
                int age = 0; // TODO: map age if available
                Pet pet = new Pet(id, name, type, age, status);
                petStore.put(id, pet);
                return ResponseEntity.ok(new PetResponse(true, pet));
            } catch (ResponseStatusException e) {
                throw e;
            } catch (Exception e) {
                logger.error("external fetch error", e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "External fetch failed");
            }
        } else if ("internal".equalsIgnoreCase(request.getSource())) {
            if (request.getName() == null || request.getType() == null || request.getStatus() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name,type,status required for internal source");
            }
            long id = request.getPetId() != null ? request.getPetId() : IdGenerator.nextId();
            Pet pet = new Pet(id, request.getName(), request.getType(), request.getAge(), request.getStatus());
            petStore.put(id, pet);
            logger.info("Stored internal pet id={}", id);
            return ResponseEntity.ok(new PetResponse(true, pet));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid source");
        }
    }

    @PostMapping("/search")
    public ResponseEntity<SearchResponse> searchPets(@RequestBody @Valid SearchRequest request) {
        logger.info("searchPets type={},status={},minAge={},maxAge={}",
                request.getType(), request.getStatus(), request.getMinAge(), request.getMaxAge());
        List<Pet> results = petStore.values().stream()
                .filter(p -> request.getType() == null || p.getType().equalsIgnoreCase(request.getType()))
                .filter(p -> request.getStatus() == null || p.getStatus().equalsIgnoreCase(request.getStatus()))
                .filter(p -> request.getMinAge() == null || p.getAge() >= request.getMinAge())
                .filter(p -> request.getMaxAge() == null || p.getAge() <= request.getMaxAge())
                .toList();
        return ResponseEntity.ok(new SearchResponse(results));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable("id") @Min(1) long id) {
        Pet pet = petStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    @GetMapping
    public ResponseEntity<List<Pet>> getAllPets() {
        return ResponseEntity.ok(petStore.values().stream().toList());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleError(ResponseStatusException ex) {
        logger.error("error {} {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    @Data
    public static class AddOrUpdatePetRequest {
        @NotBlank
        private String source; // external or internal
        private Long petId;
        @Size(min = 1)
        private String name;
        @Size(min = 1)
        private String type;
        @Min(0)
        private Integer age;
        @Size(min = 1)
        private String status;
    }

    @Data
    public static class SearchRequest {
        @Size(min = 1)
        private String type;
        @Size(min = 1)
        private String status;
        @Min(0)
        private Integer minAge;
        @Min(0)
        private Integer maxAge;
    }

    @Data
    public static class PetResponse {
        private final boolean success;
        private final Pet pet;
    }

    @Data
    public static class SearchResponse {
        private final List<Pet> results;
    }

    @Data
    public static class ErrorResponse {
        private final String error;
        private final String message;
    }

    @Data
    public static class Pet {
        private final long id;
        private final String name;
        private final String type;
        private final int age;
        private final String status;
    }

    static class IdGenerator {
        private static long current = 1000;
        static synchronized long nextId() { return current++; }
    }
}