package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@Validated
@RequestMapping(path = "/pets")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String ENTITY_NAME = "pets";

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public FetchResponse fetchPets(@RequestBody @Valid FetchRequest fetchRequest) throws ExecutionException, InterruptedException {
        String filterStatus = fetchRequest.getStatus();
        logger.info("Received fetchPets request with filter status: {}", filterStatus);

        try {
            String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + filterStatus;
            logger.info("Calling external Petstore API: {}", url);
            String response = new org.springframework.web.client.RestTemplate().getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(response);
            if (!rootNode.isArray()) {
                logger.error("Unexpected response format from Petstore API: not an array");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from external Petstore API");
            }

            List<ObjectNode> petsToAdd = new ArrayList<>();
            int count = 0;
            for (JsonNode petNode : rootNode) {
                ObjectNode petObjectNode = convertPetNodeToEntity(petNode);
                if (petObjectNode != null) {
                    petsToAdd.add(petObjectNode);
                    count++;
                }
            }

            if (!petsToAdd.isEmpty()) {
                CompletableFuture<List<UUID>> idsFuture = entityService.addItems(ENTITY_NAME, ENTITY_VERSION, petsToAdd);
                idsFuture.get();
            }

            logger.info("Fetched and stored {} pets", count);
            return new FetchResponse("Pets data fetched and processed successfully", count);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error fetching pets from external API", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pets from external API");
        }
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Pet> getPets() throws ExecutionException, InterruptedException {
        logger.info("Fetching all pets from entity service");
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode arrayNode = itemsFuture.get();

        List<Pet> pets = new ArrayList<>();
        for (JsonNode jsonNode : arrayNode) {
            Pet pet = objectMapper.convertValue(jsonNode, Pet.class);
            pet.setId(UUIDtoLongSafe(jsonNode.get("technicalId")));
            pets.add(pet);
        }
        logger.info("Returning list of {} stored pets", pets.size());
        return pets;
    }

    @PostMapping(path = "/match", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MatchResponse matchPets(@RequestBody @Valid MatchRequest matchRequest) throws ExecutionException, InterruptedException {
        logger.info("Matching pets for category: {}, status: {}", matchRequest.getPreferredCategory(), matchRequest.getPreferredStatus());

        Condition categoryCondition = Condition.of("$.category", "IEQUALS", matchRequest.getPreferredCategory());
        Condition statusCondition = Condition.of("$.status", "IEQUALS", matchRequest.getPreferredStatus());

        SearchConditionRequest condition = SearchConditionRequest.group("AND", categoryCondition, statusCondition);

        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition);
        ArrayNode arrayNode = filteredItemsFuture.get();

        List<Pet> matches = new ArrayList<>();
        for (JsonNode jsonNode : arrayNode) {
            Pet pet = objectMapper.convertValue(jsonNode, Pet.class);
            pet.setId(UUIDtoLongSafe(jsonNode.get("technicalId")));
            matches.add(pet);
        }

        logger.info("Found {} matching pets", matches.size());
        return new MatchResponse(matches);
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Pet getPetById(@PathVariable("id") Long id) throws ExecutionException, InterruptedException {
        logger.info("Fetching pet with id {}", id);

        CompletableFuture<ArrayNode> allItemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode arrayNode = allItemsFuture.get();

        for (JsonNode jsonNode : arrayNode) {
            Long entityId = UUIDtoLongSafe(jsonNode.get("technicalId"));
            if (entityId != null && entityId.equals(id)) {
                Pet pet = objectMapper.convertValue(jsonNode, Pet.class);
                pet.setId(entityId);
                return pet;
            }
        }
        logger.error("Pet with id {} not found", id);
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getReason(), ex);
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return error;
    }

    private ObjectNode convertPetNodeToEntity(JsonNode petNode) {
        try {
            ObjectNode entity = objectMapper.createObjectNode();

            if (petNode.hasNonNull("name")) {
                entity.put("name", petNode.get("name").asText());
            } else {
                entity.put("name", "Unnamed");
            }

            if (petNode.hasNonNull("status")) {
                entity.put("status", petNode.get("status").asText());
            }

            if (petNode.hasNonNull("category") && petNode.get("category").hasNonNull("name")) {
                entity.put("category", petNode.get("category").get("name").asText());
            }

            if (petNode.hasNonNull("description")) {
                entity.put("description", petNode.get("description").asText());
            }
            return entity;
        } catch (Exception ex) {
            logger.error("Failed to convert pet node to entity", ex);
            return null;
        }
    }

    private Long UUIDtoLongSafe(JsonNode uuidNode) {
        if (uuidNode == null || !uuidNode.isTextual()) return null;
        try {
            UUID uuid = UUID.fromString(uuidNode.asText());
            return uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        } catch (Exception e) {
            logger.warn("Invalid UUID format: {}", uuidNode.asText());
            return null;
        }
    }

    public static class FetchRequest {
        @NotBlank
        @Pattern(regexp = "available|pending|sold", message = "Status must be available, pending, or sold")
        private String status;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class FetchResponse {
        private final String message;
        private final int count;

        public FetchResponse(String message, int count) {
            this.message = message;
            this.count = count;
        }

        public String getMessage() {
            return message;
        }

        public int getCount() {
            return count;
        }
    }

    public static class MatchRequest {
        @NotBlank
        private String preferredCategory;
        @NotBlank
        private String preferredStatus;

        public String getPreferredCategory() {
            return preferredCategory;
        }

        public void setPreferredCategory(String preferredCategory) {
            this.preferredCategory = preferredCategory;
        }

        public String getPreferredStatus() {
            return preferredStatus;
        }

        public void setPreferredStatus(String preferredStatus) {
            this.preferredStatus = preferredStatus;
        }
    }

    public static class MatchResponse {
        private final List<Pet> matches;

        public MatchResponse(List<Pet> matches) {
            this.matches = matches;
        }

        public List<Pet> getMatches() {
            return matches;
        }
    }

    public static class Pet {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private Long id;
        private String name;
        private String category;
        private String status;
        private String description;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}