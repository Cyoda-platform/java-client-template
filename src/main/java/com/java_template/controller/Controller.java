package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.util.function.Function;
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

        List<ObjectNode> petsToAdd = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, rootNode.size()); i++) {
            JsonNode petNode = rootNode.get(i);
            ObjectNode petObjNode = petNodeToObjectNode(petNode);
            if (petObjNode != null) {
                petsToAdd.add(petObjNode);
            }
        }

        CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                "pet",
                ENTITY_VERSION,
                petsToAdd
        );
        List<UUID> addedIds = idsFuture.get();

        logger.info("Fetched and stored {} pets", addedIds.size());
        return new FetchResponse(addedIds.size(), "Pets fetched and stored successfully");
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ObjectNode> getPets(
            @RequestParam(required = false) @Size(min = 1) String type,
            @RequestParam(required = false) @Size(min = 1) String status,
            @RequestParam(required = false) @Min(1) Integer limit) throws Exception {
        logger.info("Received get pets request with filters: type={}, status={}, limit={}", type, status, limit);

        ArrayNode allItems = entityService.getItems("pet", ENTITY_VERSION).get();
        List<ObjectNode> pets = new ArrayList<>();
        for (JsonNode node : allItems) {
            if (node.isObject()) {
                pets.add((ObjectNode) node);
            }
        }

        List<ObjectNode> filtered = pets.stream()
                .filter(pet -> (type == null || type.equalsIgnoreCase(pet.path("type").asText(null))) &&
                        (status == null || status.equalsIgnoreCase(pet.path("status").asText(null))))
                .collect(Collectors.toList());

        if (limit != null && filtered.size() > limit) {
            filtered = filtered.subList(0, limit);
        }
        logger.info("Returning {} pets after filtering", filtered.size());
        return filtered;
    }

    @PostMapping(path = "/recommendation", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ObjectNode> getRecommendations(@RequestBody @Valid RecommendationRequest request) throws Exception {
        logger.info("Received recommendation request: {}", request);

        ArrayNode allItems = entityService.getItems("pet", ENTITY_VERSION).get();
        List<ObjectNode> candidates = new ArrayList<>();
        for (JsonNode node : allItems) {
            if (node.isObject()) {
                candidates.add((ObjectNode) node);
            }
        }

        if (request.getPreferredType() != null && !request.getPreferredType().isBlank()) {
            candidates = candidates.stream()
                    .filter(pet -> request.getPreferredType().equalsIgnoreCase(pet.path("type").asText(null)))
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

    private ObjectNode petNodeToObjectNode(JsonNode node) {
        if (!node.isObject()) return null;
        ObjectNode petObjNode = (ObjectNode) node.deepCopy();

        if (!petObjNode.hasNonNull("type") && petObjNode.has("category") && petObjNode.get("category").hasNonNull("name")) {
            petObjNode.put("type", petObjNode.get("category").get("name").asText());
        }

        petObjNode.remove("category");

        return petObjNode;
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
}