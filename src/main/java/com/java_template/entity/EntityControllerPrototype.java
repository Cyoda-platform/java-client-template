package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
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
@Validated
@RestController
@RequestMapping("/recipes")
public class EntityControllerPrototype {

    private final Map<String, Recipe> recipeStore = new ConcurrentHashMap<>();
    private final Map<String, WorkflowState> workflowStates = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    enum WorkflowState {
        DRAFT, SUBMITTED, APPROVED, PUBLISHED, REJECTED
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
        @NotBlank
        @Size(max = 100)
        private String name;

        @NotBlank
        private String ingredients; // CSV format

        @NotBlank
        @Size(max = 1000)
        private String instructions;

        @NotNull
        @Min(1)
        private Integer servings;

        @Pattern(regexp = "DRAFT|SUBMITTED|APPROVED|PUBLISHED|REJECTED")
        private String state;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class EventRequest {
        @NotBlank
        @Pattern(regexp = "(?i)submit|approve|publish|reject")
        private String eventType;
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

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse> createRecipe(@RequestBody @Valid CreateUpdateRecipeRequest request) {
        log.info("Received create recipe request: {}", request);
        String newId = UUID.randomUUID().toString();
        List<String> list = Arrays.asList(request.getIngredients().split(","));
        Recipe newRecipe = new Recipe(newId, request.getName(), list, request.getInstructions(), request.getServings(), WorkflowState.DRAFT);
        recipeStore.put(newId, newRecipe);
        workflowStates.put(newId, WorkflowState.DRAFT);
        log.info("Recipe created with ID {}", newId);
        return ResponseEntity.created(URI.create("/recipes/" + newId))
                .body(new ApiResponse(newId, "created", "Recipe created successfully"));
    }

    @PostMapping(value = "/{recipeId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse> updateRecipe(@PathVariable String recipeId, @RequestBody @Valid CreateUpdateRecipeRequest request) {
        log.info("Received update recipe request for ID {}: {}", recipeId, request);
        Recipe existing = recipeStore.get(recipeId);
        if (existing == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
        existing.setName(request.getName());
        existing.setIngredients(Arrays.asList(request.getIngredients().split(",")));
        existing.setInstructions(request.getInstructions());
        existing.setServings(request.getServings());
        if (request.getState() != null) {
            WorkflowState newState;
            try { newState = WorkflowState.valueOf(request.getState()); }
            catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid state"); }
            existing.setState(newState);
            workflowStates.put(recipeId, newState);
        }
        recipeStore.put(recipeId, existing);
        log.info("Recipe with ID {} updated", recipeId);
        return ResponseEntity.ok(new ApiResponse(recipeId, "updated", "Recipe updated successfully"));
    }

    @PostMapping(value = "/{recipeId}/events", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EventResponse> triggerEvent(@PathVariable String recipeId, @RequestBody @Valid EventRequest eventRequest) {
        log.info("Triggering event '{}' on recipe {}", eventRequest.getEventType(), recipeId);
        Recipe recipe = recipeStore.get(recipeId);
        if (recipe == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
        WorkflowState currentState = workflowStates.getOrDefault(recipeId, WorkflowState.DRAFT);
        WorkflowState newState = currentState;
        switch (eventRequest.getEventType().toLowerCase()) {
            case "submit":
                if (currentState == WorkflowState.DRAFT) newState = WorkflowState.SUBMITTED;
                else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Submit invalid");
                break;
            case "approve":
                if (currentState == WorkflowState.SUBMITTED) newState = WorkflowState.APPROVED;
                else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approve invalid");
                break;
            case "publish":
                if (currentState == WorkflowState.APPROVED) newState = WorkflowState.PUBLISHED;
                else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Publish invalid");
                break;
            case "reject":
                if (currentState == WorkflowState.SUBMITTED) newState = WorkflowState.REJECTED;
                else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reject invalid");
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown event");
        }
        recipe.setState(newState);
        workflowStates.put(recipeId, newState);
        recipeStore.put(recipeId, recipe);
        CompletableFuture.runAsync(() -> log.info("Async workflow for recipe {}", recipeId));
        return ResponseEntity.ok(new EventResponse(recipeId, newState.name(), "Event processed"));
    }

    @GetMapping(value = "/{recipeId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Recipe> getRecipe(@PathVariable String recipeId) {
        log.info("Fetching recipe {}", recipeId);
        Recipe recipe = recipeStore.get(recipeId);
        if (recipe == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
        return ResponseEntity.ok(recipe);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<RecipeSummary>> listRecipes(
        @RequestParam(name = "state", required = false)
        @Pattern(regexp = "DRAFT|SUBMITTED|APPROVED|PUBLISHED|REJECTED")
        String state) {
        log.info("Listing recipes filter state: {}", state);
        List<RecipeSummary> result = new ArrayList<>();
        for (Recipe r : recipeStore.values()) {
            if (state == null || r.getState().name().equals(state)) {
                result.add(new RecipeSummary(r.getRecipeId(), r.getName(), r.getState().name()));
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

    @PostMapping(value = "/external-data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> fetchExternalData() {
        log.info("Fetching external data (mock)");
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://jsonplaceholder.typicode.com/todos/1"))
                .GET()
                .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode node = objectMapper.readTree(resp.body());
            return ResponseEntity.ok(node);
        } catch (Exception e) {
            log.error("Error fetching external data", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed external data");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleEx(ResponseStatusException ex) {
        Map<String, Object> err = new HashMap<>();
        err.put("status", ex.getStatusCode().value());
        err.put("error", ex.getReason());
        log.error("Error: {}", ex.getReason());
        return new ResponseEntity<>(err, ex.getStatusCode());
    }
}