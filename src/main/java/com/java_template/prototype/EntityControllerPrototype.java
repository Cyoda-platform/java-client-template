package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/pets")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, Pet> petsCache = new ConcurrentHashMap<>();
    private final Set<String> categoriesCache = ConcurrentHashMap.newKeySet();

    @PostMapping("/fetch") // must be first
    public ResponseEntity<FetchResponse> fetchPets(@RequestBody @Valid FetchRequest request) {
        logger.info("Received fetch request with status={} tags={}", request.getStatus(), request.getTags());
        String url = PETSTORE_API_BASE + "/pet/findByStatus?status=" + request.getStatus();
        try {
            String raw = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(raw);
            if (!root.isArray()) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected format");
            petsCache.clear();
            categoriesCache.clear();
            int count = 0;
            for (JsonNode node : root) {
                Pet pet = parsePet(node);
                if (request.getTags() != null && !request.getTags().isEmpty()) {
                    if (pet.getTags() == null || Collections.disjoint(pet.getTags(), request.getTags())) continue;
                }
                petsCache.put(pet.getId(), pet);
                if (pet.getCategory() != null) categoriesCache.add(pet.getCategory());
                count++;
            }
            logger.info("Cached {} pets", count);
            return ResponseEntity.ok(new FetchResponse("Pets fetched and stored successfully", count));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Fetch error", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pets");
        }
    }

    @GetMapping // must be first
    public ResponseEntity<List<Pet>> getPets() {
        logger.info("Returning {} pets", petsCache.size());
        return ResponseEntity.ok(new ArrayList<>(petsCache.values()));
    }

    @PostMapping("/details") // must be first
    public ResponseEntity<Pet> getPetDetails(@RequestBody @Valid PetDetailsRequest request) {
        logger.info("Details request for petId={}", request.getPetId());
        Pet cached = petsCache.get(request.getPetId());
        if (cached != null) return ResponseEntity.ok(cached);
        String url = PETSTORE_API_BASE + "/pet/" + request.getPetId();
        try {
            String raw = restTemplate.getForObject(url, String.class);
            JsonNode node = objectMapper.readTree(raw);
            if (!node.has("id")) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
            Pet pet = parsePet(node);
            return ResponseEntity.ok(pet);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Details fetch error", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pet details");
        }
    }

    @GetMapping("/categories") // must be first
    public ResponseEntity<Set<String>> getCategories() {
        logger.info("Returning categories {}", categoriesCache);
        return ResponseEntity.ok(categoriesCache);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleError(ResponseStatusException ex) {
        logger.error("ErrorHandler status={} reason={}", ex.getStatusCode(), ex.getReason());
        return new ResponseEntity<>(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()), ex.getStatusCode());
    }

    private Pet parsePet(JsonNode node) {
        Pet p = new Pet();
        p.setId(node.path("id").asLong());
        p.setName(node.path("name").asText(null));
        p.setStatus(node.path("status").asText(null));
        JsonNode cat = node.path("category");
        if (cat.has("name")) p.setCategory(cat.get("name").asText());
        List<String> tags = new ArrayList<>();
        for (JsonNode t : node.path("tags")) if (t.has("name")) tags.add(t.get("name").asText());
        p.setTags(tags);
        p.setDescription("Description placeholder"); // TODO replace with real description
        return p;
    }

    @Data
    public static class FetchRequest {
        @NotBlank
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @Size(min = 1)
        private List<@NotBlank String> tags;
    }

    @Data
    public static class FetchResponse {
        private final String message;
        private final int count;
    }

    @Data
    public static class PetDetailsRequest {
        @NotNull
        private Long petId;
    }

    @Data
    public static class ErrorResponse {
        private final String error;
        private final String message;
    }

    @Data
    public static class Pet {
        private long id;
        private String name;
        private String status;
        private String category;
        private List<String> tags;
        private String description;
    }
}