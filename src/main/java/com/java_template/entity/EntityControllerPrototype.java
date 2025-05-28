```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/recipes")
public class EntityControllerPrototype {

    private final Map<String, Recipe> recipeStore = new ConcurrentHashMap<>();
    private final Map<String, WorkflowState> workflowStates = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Sample states for state machine
    enum WorkflowState {
        DRAFT,
        SUBMITTED,
        APPROVED,
        PUBLISHED,
        REJECTED
    }

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Recipe {
        private String recipeId;
        private String name;
        private List<String> ingredients;
        private String instructions;
        private Integer servings;
        private WorkflowState state;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CreateUpdateRecipeRequest {
        private String name;
        private List<String> ingredients;
        private String instructions;
        private Integer servings;
        private String state; // optional in create, required in update
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class EventRequest {
        private String eventType; // e.g. submit, approve, reject
    }

    @Data
    @AllArgsConstructor
    static class ApiResponse {
        private String recipeId;
        private String status;
        private String message;
    }

    @Data
    @AllArgsConstructor
    static class EventResponse {
        private String recipeId;
        private String newState;
        private String message;
    }

    /**
     * POST /recipes
     * Create a new pumpkin wings recipe.
     * Business logic or external calls can be added here.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse> createRecipe(@RequestBody CreateUpdateRecipeRequest request) {
        log.info("Received create recipe request: {}", request);

        // Basic validation
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipe name is required");
        }
        if (request.getIngredients() == null || request.getIngredients().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingredients are required");
        }

        String newId = UUID.randomUUID().toString();
        WorkflowState initialState = WorkflowState.DRAFT;
        Recipe newRecipe = new Recipe(
                newId,
                request.getName(),
                request.getIngredients(),
                request.getInstructions(),
                request.getServings(),
                initialState
        );

        recipeStore.put(newId, newRecipe);
        workflowStates.put(newId, initialState);

        log.info("Recipe created with ID {}", newId);
        return ResponseEntity.created(URI.create("/recipes/" + newId))
                .body(new ApiResponse(newId, "created", "Recipe created successfully"));
    }

    /**
     * POST /recipes/{recipeId}
     * Update an existing recipe or trigger workflow state change.
     */
    @PostMapping(value = "/{recipeId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse> updateRecipe(
            @PathVariable String recipeId,
            @RequestBody CreateUpdateRecipeRequest request) {
        log.info("Received update recipe request for ID {}: {}", recipeId, request);

        Recipe existing = recipeStore.get(recipeId);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
        }

        // Update fields if provided
        if (request.getName() != null) existing.setName(request.getName());
        if (request.getIngredients() != null) existing.setIngredients(request.getIngredients());
        if (request.getInstructions() != null) existing.setInstructions(request.getInstructions());
        if (request.getServings() != null) existing.setServings(request.getServings());

        // Handle state update if provided and valid
        if (request.getState() != null) {
            try {
                WorkflowState newState = WorkflowState.valueOf(request.getState().toUpperCase(Locale.ROOT));
                existing.setState(newState);
                workflowStates.put(recipeId, newState);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid state value");
            }
        }

        recipeStore.put(recipeId, existing);

        log.info("Recipe with ID {} updated", recipeId);
        return ResponseEntity.ok(new ApiResponse(recipeId, "updated", "Recipe updated successfully"));
    }

    /**
     * POST /recipes/{recipeId}/events
     * Trigger an event on the recipe entity to change workflow state.
     */
    @PostMapping(value = "/{recipeId}/events",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EventResponse> triggerEvent(
            @PathVariable String recipeId,
            @RequestBody EventRequest eventRequest) {
        log.info("Triggering event '{}' on recipe {}", eventRequest.getEventType(), recipeId);

        Recipe recipe = recipeStore.get(recipeId);
        if (recipe == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
        }

        WorkflowState currentState = workflowStates.getOrDefault(recipeId, WorkflowState.DRAFT);
        WorkflowState newState = currentState;

        String event = eventRequest.getEventType().toLowerCase(Locale.ROOT);
        switch (event) {
            case "submit":
                if (currentState == WorkflowState.DRAFT) newState = WorkflowState.SUBMITTED;
                else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Submit event invalid in current state");
                break;
            case "approve":
                if (currentState == WorkflowState.SUBMITTED) newState = WorkflowState.APPROVED;
                else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approve event invalid in current state");
                break;
            case "publish":
                if (currentState == WorkflowState.APPROVED) newState = WorkflowState.PUBLISHED;
                else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Publish event invalid in current state");
                break;
            case "reject":
                if (currentState == WorkflowState.SUBMITTED) newState = WorkflowState.REJECTED;
                else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reject event invalid in current state");
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown event type");
        }

        recipe.setState(newState);
        workflowStates.put(recipeId, newState);
        recipeStore.put(recipeId, recipe);

        log.info("Recipe {} state changed from {} to {}", recipeId, currentState, newState);

        // TODO: Fire-and-forget async processing if needed
        CompletableFuture.runAsync(() -> {
            logger.info("Async workflow processing for recipe {}", recipeId);
            // Placeholder for actual async workflow logic
        });

        return ResponseEntity.ok(new EventResponse(recipeId, newState.name(), "Event processed successfully"));
    }

    /**
     * GET /recipes/{recipeId}
     * Get details of a specific recipe.
     */
    @GetMapping(value = "/{recipeId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Recipe> getRecipe(@PathVariable String recipeId) {
        log.info("Fetching recipe details for ID {}", recipeId);

        Recipe recipe = recipeStore.get(recipeId);
        if (recipe == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
        }
        return ResponseEntity.ok(recipe);
    }

    /**
     * GET /recipes
     * List recipes with optional filter by state.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<RecipeSummary>> listRecipes(@RequestParam(name = "state", required = false) String state) {
        log.info("Listing recipes with state filter: {}", state);

        List<RecipeSummary> result = new ArrayList<>();
        for (Recipe recipe : recipeStore.values()) {
            if (state == null || recipe.getState().name().equalsIgnoreCase(state)) {
                result.add(new RecipeSummary(recipe.getRecipeId(), recipe.getName(), recipe.getState().name()));
            }
        }
        return ResponseEntity.ok(result);
    }

    @Data
    @AllArgsConstructor
    static class RecipeSummary {
        private String recipeId;
        private String name;
        private String state;
    }

    /**
     * Example external API call: (Mock)
     * Fetch some external data, parse JSON with ObjectMapper.readTree(...)
     * TODO: Replace with actual external API URL and logic.
     */
    @PostMapping(value = "/external-data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> fetchExternalData() {
        log.info("Fetching external data (mock)");

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    // TODO: Replace with real external API URL
                    .uri(URI.create("https://jsonplaceholder.typicode.com/todos/1"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode jsonNode = objectMapper.readTree(response.body());

            return ResponseEntity.ok(jsonNode);
        } catch (Exception e) {
            log.error("Error fetching external data", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch external data");
        }
    }

    // Minimal global exception handler for ResponseStatusException
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getReason());
        log.error("ResponseStatusException: {}", ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }
}
```