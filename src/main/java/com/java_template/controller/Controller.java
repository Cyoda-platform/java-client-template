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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
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

@Validated
@RestController
@RequestMapping(path = "/cyoda/pets")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String ENTITY_NAME = "pet";

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Data
    public static class Pet {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
        private Long id;
        @NotBlank
        @Size(max = 100)
        private String name;
        @NotBlank
        @Pattern(regexp = "cat|dog|bird")
        private String type;
        @NotBlank
        @Pattern(regexp = "available|pending|sold")
        private String status;
        private List<String> photoUrls;
        private List<String> tags;
    }

    @Data
    public static class SearchRequest {
        @NotNull
        @Pattern(regexp = "cat|dog|bird|all")
        private String type;
        @NotNull
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @Size(max = 100)
        private String name;
    }

    @Data
    public static class SearchResponse {
        private List<Pet> pets;
    }

    @Data
    public static class RecommendationsRequest {
        @NotBlank
        @Pattern(regexp = "cat|dog|bird|all")
        private String preferredType;
        @NotBlank
        @Pattern(regexp = "available|pending|sold")
        private String preferredStatus;
    }

    @Data
    public static class RecommendationsResponse {
        private List<Pet> recommendedPets;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<UUID> addPet(@RequestBody @Valid Pet pet) {
        ObjectNode petNode = objectMapper.valueToTree(pet);
        logger.info("Adding pet: name={}, type={}, status={}", pet.getName(), pet.getType(), pet.getStatus());
        return entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                petNode
        );
    }

    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public SearchResponse searchPets(@RequestBody @Valid SearchRequest request) throws ExecutionException, InterruptedException {
        logger.info("searchPets request: type={}, status={}, name={}", request.getType(), request.getStatus(), request.getName());

        List<Condition> conditions = new ArrayList<>();
        if (!"all".equalsIgnoreCase(request.getType())) {
            conditions.add(Condition.of("$.type", "IEQUALS", request.getType()));
        }
        conditions.add(Condition.of("$.status", "EQUALS", request.getStatus()));
        if (request.getName() != null && !request.getName().isBlank()) {
            conditions.add(Condition.of("$.name", "ICONTAINS", request.getName()));
        }

        SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));

        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, conditionRequest);
        ArrayNode itemsNode = filteredItemsFuture.get();

        List<Pet> filteredPets = new ArrayList<>();
        for (JsonNode node : itemsNode) {
            Pet pet = objectMapper.convertValue(node, Pet.class);
            filteredPets.add(pet);
        }

        logger.info("found {} pets", filteredPets.size());
        SearchResponse response = new SearchResponse();
        response.setPets(filteredPets);
        return response;
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Pet getPetById(@PathVariable @NotNull UUID id) throws ExecutionException, InterruptedException {
        logger.info("getPetById id={}", id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            logger.warn("pet not found id={}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return objectMapper.convertValue(node, Pet.class);
    }

    @PostMapping(path = "/recommendations", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public RecommendationsResponse getRecommendations(@RequestBody @Valid RecommendationsRequest request) throws ExecutionException, InterruptedException {
        logger.info("getRecommendations preferredType={}, preferredStatus={}", request.getPreferredType(), request.getPreferredStatus());

        List<Condition> conditions = new ArrayList<>();
        if (!"all".equalsIgnoreCase(request.getPreferredType())) {
            conditions.add(Condition.of("$.type", "IEQUALS", request.getPreferredType()));
        }
        conditions.add(Condition.of("$.status", "EQUALS", request.getPreferredStatus()));

        SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));

        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, conditionRequest);
        ArrayNode itemsNode = filteredItemsFuture.get();

        List<Pet> recommended = new ArrayList<>();
        for (JsonNode node : itemsNode) {
            Pet pet = objectMapper.convertValue(node, Pet.class);
            recommended.add(pet);
        }

        RecommendationsResponse response = new RecommendationsResponse();
        response.setRecommendedPets(recommended);
        return response;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getMessage());
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return error;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleException(Exception ex) {
        logger.error("Unhandled exception", ex);
        Map<String, Object> error = new HashMap<>();
        error.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        error.put("message", "Internal server error");
        return error;
    }
}