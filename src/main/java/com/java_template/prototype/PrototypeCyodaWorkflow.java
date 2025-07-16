package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity/pets")
@Validated
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";
    private static final String ENTITY_NAME = "Pet";

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Workflow function to process a Pet entity asynchronously before persistence.
     * We receive ObjectNode representing the entity.
     * We can modify it directly, add supplementary entities of different model, etc.
     */
    private CompletableFuture<ObjectNode> processPet(ObjectNode petNode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Ensure description field exists and is not empty
                if (!petNode.hasNonNull("description") || petNode.get("description").asText().trim().isEmpty()) {
                    petNode.put("description", "Processed description");
                }

                // Add supplementary Category entity if category exists
                if (petNode.hasNonNull("category")) {
                    String categoryName = petNode.get("category").asText().trim();
                    if (!categoryName.isEmpty()) {
                        ObjectNode categoryEntity = objectMapper.createObjectNode();
                        categoryEntity.put("name", categoryName);

                        // Fire-and-forget adding category entity asynchronously with its own workflow
                        entityService.addItem("Category", ENTITY_VERSION, categoryEntity, this::processCategory)
                                .exceptionally(ex -> {
                                    logger.error("Failed to add category entity asynchronously", ex);
                                    return null;
                                });
                    }
                }

                // Process tags to add supplementary Tag entities if any
                if (petNode.hasNonNull("tags") && petNode.get("tags").isArray()) {
                    for (JsonNode tagNode : petNode.get("tags")) {
                        if (tagNode.hasNonNull("name")) {
                            String tagName = tagNode.get("name").asText().trim();
                            if (!tagName.isEmpty()) {
                                ObjectNode tagEntity = objectMapper.createObjectNode();
                                tagEntity.put("name", tagName);

                                // Add tag entity fire-and-forget with its workflow
                                entityService.addItem("Tag", ENTITY_VERSION, tagEntity, this::processTag)
                                        .exceptionally(ex -> {
                                            logger.error("Failed to add tag entity asynchronously", ex);
                                            return null;
                                        });
                            }
                        }
                    }
                }

                // Potential for further enrichment or async calls here

                return petNode;
            } catch (Exception ex) {
                logger.error("Error in processPet workflow", ex);
                // Returning entity as is on error to avoid failing persistence unnecessarily
                return petNode;
            }
        });
    }

    /**
     * Workflow for Category entity.
     * Add default description if missing.
     */
    private CompletableFuture<ObjectNode> processCategory(ObjectNode categoryNode) {
        return CompletableFuture.supplyAsync(() -> {
            if (!categoryNode.hasNonNull("description") || categoryNode.get("description").asText().trim().isEmpty()) {
                categoryNode.put("description", "Category created by Pet processing workflow");
            }
            return categoryNode;
        });
    }

    /**
     * Workflow for Tag entity.
     * Add default description if missing.
     */
    private CompletableFuture<ObjectNode> processTag(ObjectNode tagNode) {
        return CompletableFuture.supplyAsync(() -> {
            if (!tagNode.hasNonNull("description") || tagNode.get("description").asText().trim().isEmpty()) {
                tagNode.put("description", "Tag created by Pet processing workflow");
            }
            return tagNode;
        });
    }

    @PostMapping("/fetch") // must be first
    public ResponseEntity<FetchResponse> fetchPets(@RequestBody @Valid FetchRequest request) {
        logger.info("Received fetch request with status={} tags={}", request.getStatus(), request.getTags());
        String url = PETSTORE_API_BASE + "/pet/findByStatus?status=" + request.getStatus();

        try {
            String raw = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(raw);
            if (!root.isArray()) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected format");

            // Filter pets by tags if provided, directly on ObjectNode level
            List<ObjectNode> filteredPets = new ArrayList<>();
            for (JsonNode node : root) {
                if (!node.isObject()) continue;
                ObjectNode petNode = (ObjectNode) node;

                if (request.getTags() != null && !request.getTags().isEmpty()) {
                    boolean hasTag = false;
                    if (petNode.has("tags") && petNode.get("tags").isArray()) {
                        for (JsonNode tagNode : petNode.get("tags")) {
                            if (tagNode.has("name") && request.getTags().contains(tagNode.get("name").asText())) {
                                hasTag = true;
                                break;
                            }
                        }
                    }
                    if (!hasTag) continue; // skip pet without requested tags
                }
                filteredPets.add(petNode);
            }

            // Defensive check: If no pets after filtering, return success with zero count
            if (filteredPets.isEmpty()) {
                logger.info("No pets matched status={} and tags={}", request.getStatus(), request.getTags());
                return ResponseEntity.ok(new FetchResponse("No pets matched given criteria", 0));
            }

            // Add filtered pets with workflow processing
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    filteredPets,
                    this::processPet
            );

            int count = idsFuture.get().size();

            logger.info("Stored {} pets via EntityService", count);
            return ResponseEntity.ok(new FetchResponse("Pets fetched and stored successfully", count));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Fetch interrupted", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Operation interrupted");
        } catch (ExecutionException ex) {
            logger.error("Fetch execution error", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pets");
        } catch (Exception ex) {
            logger.error("Fetch error", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pets");
        }
    }

    @GetMapping
    public ResponseEntity<List<ObjectNode>> getPets() {
        logger.info("Retrieving all pets");
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    ENTITY_NAME,
                    ENTITY_VERSION
            );
            ArrayNode arrayNode = itemsFuture.get();
            List<ObjectNode> pets = new ArrayList<>();
            for (JsonNode node : arrayNode) {
                if (node.isObject()) {
                    pets.add((ObjectNode) node);
                }
            }
            logger.info("Returning {} pets", pets.size());
            return ResponseEntity.ok(pets);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Get pets interrupted", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Operation interrupted");
        } catch (ExecutionException ex) {
            logger.error("Error retrieving pets", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to retrieve pets");
        }
    }

    @PostMapping("/details")
    public ResponseEntity<ObjectNode> getPetDetails(@RequestBody @Valid PetDetailsRequest request) {
        logger.info("Details request for pet technicalId={}", request.getTechnicalId());
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    request.getTechnicalId()
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
            return ResponseEntity.ok(node);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Get pet details interrupted", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Operation interrupted");
        } catch (ExecutionException ex) {
            logger.error("Details fetch error", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pet details");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleError(ResponseStatusException ex) {
        logger.error("ErrorHandler status={} reason={}", ex.getStatusCode(), ex.getReason());
        return new ResponseEntity<>(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()), ex.getStatusCode());
    }

    @Data
    public static class FetchRequest {
        @NotBlank
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
        @NotBlank
        private String technicalId;
    }

    @Data
    public static class ErrorResponse {
        private final String error;
        private final String message;
    }
}