package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda-pets")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EntityService entityService;

    private static final String PETSTORE_API_URL = "https://petstore.swagger.io/v2/pet/findByStatus?status=%s";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping(path = "/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public FetchResponse fetchPets(@RequestBody @Valid FetchRequest request) throws Exception {
        logger.info("Received fetch request: {}", request);
        String status = (request.getStatus() != null && !request.getStatus().isBlank()) ? request.getStatus() : "available";
        String url = String.format(PETSTORE_API_URL, status);
        String jsonResponse = restTemplate.getForObject(url, String.class);
        JsonNode rootNode = objectMapper.readTree(jsonResponse);
        if (!rootNode.isArray()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Unexpected API response format");
        }
        int limit = (request.getLimit() != null) ? request.getLimit() : rootNode.size();

        List<Pet> petsToAdd = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, rootNode.size()); i++) {
            JsonNode petNode = rootNode.get(i);
            Pet pet = jsonNodeToPet(petNode);
            if (pet != null) {
                petsToAdd.add(pet);
            }
        }

        // Using entityService.addItems to add pets
        CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                "pet",
                ENTITY_VERSION,
                petsToAdd
        );
        List<UUID> addedIds = idsFuture.get();

        CompletableFuture.runAsync(() -> logger.info("Triggering workflows for fetched pets (mocked)"));
        logger.info("Fetched and stored {} pets", addedIds.size());
        return new FetchResponse(addedIds.size(), "Pets fetched and stored successfully");
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Pet> getPets(
            @RequestParam(required = false) @Size(min = 1) String type,
            @RequestParam(required = false) @Size(min = 1) String status,
            @RequestParam(required = false) @Min(1) Integer limit) throws Exception {
        logger.info("Received get pets request with filters: type={}, status={}, limit={}", type, status, limit);

        ArrayNode allItems = entityService.getItems("pet", ENTITY_VERSION).get();
        List<Pet> pets = new ArrayList<>();
        for (JsonNode node : allItems) {
            Pet pet = objectMapper.treeToValue(node, Pet.class);
            if (pet != null) {
                pets.add(pet);
            }
        }

        List<Pet> filtered = pets.stream()
                .filter(pet -> (type == null || type.equalsIgnoreCase(pet.getType())) &&
                               (status == null || status.equalsIgnoreCase(pet.getStatus())))
                .collect(Collectors.toList());

        if (limit != null && filtered.size() > limit) {
            filtered = filtered.subList(0, limit);
        }
        logger.info("Returning {} pets after filtering", filtered.size());
        return filtered;
    }

    @PostMapping(path = "/recommendation", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Pet> getRecommendations(@RequestBody @Valid RecommendationRequest request) throws Exception {
        logger.info("Received recommendation request: {}", request);

        String condition = null;
        if (request.getPreferredType() != null && !request.getPreferredType().isBlank()) {
            // Assuming condition is a string understood by getItemsByCondition; if not, skip condition
            // Since no format specified, skipping condition and filtering after fetch
        }

        ArrayNode allItems = entityService.getItems("pet", ENTITY_VERSION).get();
        List<Pet> candidates = new ArrayList<>();
        for (JsonNode node : allItems) {
            Pet pet = objectMapper.treeToValue(node, Pet.class);
            if (pet != null) {
                candidates.add(pet);
            }
        }

        if (request.getPreferredType() != null && !request.getPreferredType().isBlank()) {
            candidates = candidates.stream()
                    .filter(pet -> request.getPreferredType().equalsIgnoreCase(pet.getType()))
                    .collect(Collectors.toList());
        }

        Collections.shuffle(candidates);
        int maxResults = (request.getMaxResults() != null) ? request.getMaxResults() : 5;
        if (candidates.size() > maxResults) {
            candidates = candidates.subList(0, maxResults);
        }
        logger.info("Returning {} recommended pets", candidates.size());
        return candidates;
    }

    private Pet jsonNodeToPet(JsonNode node) {
        try {
            Long id = node.path("id").asLong();
            String name = node.path("name").asText(null);
            String type = node.path("category").path("name").asText(null);
            String status = node.path("status").asText(null);
            List<String> photoUrls = new ArrayList<>();
            JsonNode photosNode = node.path("photoUrls");
            if (photosNode.isArray()) {
                for (JsonNode urlNode : photosNode) {
                    photoUrls.add(urlNode.asText());
                }
            }
            return new Pet(id, name, type, status, photoUrls);
        } catch (Exception e) {
            logger.error("Failed to parse pet JSON node: {}", node, e);
            return null;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchRequest {
        @Size(min = 1)
        private String type;
        @Size(min = 1)
        private String status;
        @Min(1)
        private Integer limit;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchResponse {
        private int fetchedCount;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationRequest {
        @Size(min = 1)
        private String preferredType;
        @Min(1)
        private Integer maxResults;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Long id;
        private String name;
        private String type;
        private String status;
        private List<String> photoUrls;
    }
}