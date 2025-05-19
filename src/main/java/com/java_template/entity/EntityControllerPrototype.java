package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Integer, Pet> internalPetStore = new ConcurrentHashMap<>();
    private int petIdSequence = 1000;
    private static final String EXTERNAL_API_BASE = "https://petstore.swagger.io/v2";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Integer id;
        private String name;
        private String category;
        private String status;
        private List<String> photoUrls = new ArrayList<>();
        private List<String> tags = new ArrayList<>();
    }

    @Data
    public static class SearchRequest {
        @Pattern(regexp = "^(available|pending|sold)$", message = "status must be one of available, pending, sold")
        private String status;
        @Size(max = 50, message = "category max length 50")
        private String category;
        @Size(max = 100, message = "name max length 100")
        private String name;
    }

    @Data
    public static class AddPetRequest {
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name max length 100")
        private String name;
        @Size(max = 50, message = "category max length 50")
        private String category;
        @NotNull(message = "status is required")
        @Pattern(regexp = "^(available|pending|sold)$", message = "status must be one of available, pending, sold")
        private String status;
        private List<String> photoUrls = new ArrayList<>();
        private List<String> tags = new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    public static class AddPetResponse {
        private String message;
        private Integer id;
    }

    @Data
    public static class SearchResponse {
        private List<Pet> pets = new ArrayList<>();
    }

    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public SearchResponse searchPets(@RequestBody @Valid SearchRequest request) {
        logger.info("Received search request: status={}, category={}, name={}",
                request.getStatus(), request.getCategory(), request.getName());
        try {
            String statusParam = (request.getStatus() == null || request.getStatus().isEmpty()) ? "available" : request.getStatus();
            URI uri = new URI(EXTERNAL_API_BASE + "/pet/findByStatus?status=" + statusParam);
            String raw = restTemplate.getForObject(uri, String.class);
            JsonNode root = objectMapper.readTree(raw);
            List<Pet> filtered = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    String petCategory = node.path("category").path("name").asText("");
                    String petName = node.path("name").asText("");
                    boolean matchCat = request.getCategory() == null || petCategory.equalsIgnoreCase(request.getCategory());
                    boolean matchName = request.getName() == null || petName.toLowerCase().contains(request.getName().toLowerCase());
                    if (matchCat && matchName) {
                        Pet p = new Pet();
                        p.setId(node.path("id").asInt());
                        p.setName(petName);
                        p.setStatus(statusParam);
                        p.setCategory(petCategory);
                        List<String> photos = new ArrayList<>();
                        if (node.has("photoUrls")) node.get("photoUrls").forEach(u -> photos.add(u.asText()));
                        p.setPhotoUrls(photos);
                        filtered.add(p);
                    }
                }
            }
            SearchResponse resp = new SearchResponse();
            resp.setPets(filtered);
            logger.info("Returning {} pets", filtered.size());
            return resp;
        } catch (Exception ex) {
            logger.error("Search failed", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pets from external source");
        }
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Pet getPetById(@PathVariable("id") @NotNull @Positive Integer id) {
        logger.info("Fetching pet id={}", id);
        Pet pet = internalPetStore.get(id);
        if (pet != null) return pet;
        try {
            URI uri = new URI(EXTERNAL_API_BASE + "/pet/" + id);
            String raw = restTemplate.getForObject(uri, String.class);
            JsonNode node = objectMapper.readTree(raw);
            if (!node.has("id")) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found externally");
            Pet p = new Pet();
            p.setId(node.path("id").asInt());
            p.setName(node.path("name").asText(""));
            p.setStatus(node.path("status").asText(""));
            p.setCategory(node.path("category").path("name").asText(""));
            List<String> photos = new ArrayList<>();
            if (node.has("photoUrls")) node.get("photoUrls").forEach(u -> photos.add(u.asText()));
            p.setPhotoUrls(photos);
            List<String> tags = new ArrayList<>();
            if (node.has("tags")) node.get("tags").forEach(t -> tags.add(t.path("name").asText("")));
            p.setTags(tags);
            return p;
        } catch (ResponseStatusException re) {
            throw re;
        } catch (Exception ex) {
            logger.error("Fetch external pet failed", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pet");
        }
    }

    @PostMapping(value = "/add", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AddPetResponse addPet(@RequestBody @Valid AddPetRequest request) {
        int newId = generatePetId();
        Pet pet = new Pet(newId, request.getName(), request.getCategory(), request.getStatus(),
                Optional.ofNullable(request.getPhotoUrls()).orElse(Collections.emptyList()),
                Optional.ofNullable(request.getTags()).orElse(Collections.emptyList()));
        internalPetStore.put(newId, pet);
        CompletableFuture.runAsync(() -> {
            logger.info("Async processing for pet={}", newId);
            // TODO: trigger workflow
        });
        return new AddPetResponse("Pet added successfully", newId);
    }

    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Pet> listPets() {
        return new ArrayList<>(internalPetStore.values());
    }

    private synchronized int generatePetId() {
        return petIdSequence++;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> err = new HashMap<>();
        err.put("status", ex.getStatusCode().value());
        err.put("error", ex.getStatusCode().getReasonPhrase());
        err.put("message", ex.getReason());
        return err;
    }
}