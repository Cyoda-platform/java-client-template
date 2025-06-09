package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/prototype/api/pets")
@Validated
public class CyodaEntityControllerPrototype {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/fetch")
    public CompletableFuture<ResponseEntity<?>> fetchAndTransformPetDetails(@RequestBody @Valid FetchRequest request) {
        // Simulate fetching external data
        JsonNode jsonResponse = objectMapper.createArrayNode(); // Replace with actual API call result
        
        // Transform data asynchronously and store using EntityService
        return transformAndStorePetData(jsonResponse)
                .thenApply(ids -> ResponseEntity.ok("Transformed pets stored successfully"));
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<?>> retrieveTransformedPetData() {
        return entityService.getItems("Pet", ENTITY_VERSION)
                .thenApply(items -> {
                    if (items.isEmpty()) {
                        return ResponseEntity.status(404).body("No pets found");
                    }
                    return ResponseEntity.ok(items);
                });
    }

    @PostMapping("/notify")
    public CompletableFuture<ResponseEntity<?>> notifyNoMatchingPets(@RequestBody @Valid FetchRequest request) {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.species", "EQUALS", request.getSpecies()),
                Condition.of("$.status", "EQUALS", request.getStatus()),
                Condition.of("$.categoryId", "EQUALS", request.getCategoryId().toString())
        );

        return entityService.getItemsByCondition("Pet", ENTITY_VERSION, condition)
                .thenApply(filteredItems -> {
                    if (filteredItems.isEmpty()) {
                        logger.info("No pets match the search criteria for species: {}, status: {}, categoryId: {}",
                                request.getSpecies(), request.getStatus(), request.getCategoryId());
                        return ResponseEntity.ok("No pets match the search criteria.");
                    }
                    return ResponseEntity.ok("Pets found.");
                });
    }

    private CompletableFuture<List<UUID>> transformAndStorePetData(JsonNode jsonResponse) {
        List<ObjectNode> transformedPets = transformPetData(jsonResponse);
        return entityService.addItems("Pet", ENTITY_VERSION, transformedPets);
    }

    private List<ObjectNode> transformPetData(JsonNode jsonResponse) {
        // Convert JsonNode to List<ObjectNode> representing pets
        // This is a placeholder implementation; replace with actual transformation logic
        return List.of();
    }

    private String fetchSupplementaryData(ObjectNode pet) {
        // Simulate fetching supplementary data from an external service
        // This is a placeholder implementation; replace with actual logic
        return "Supplementary Data";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class FetchRequest {
        @NotNull(message = "Species cannot be null")
        @Size(min = 1, message = "Species must not be empty")
        private String species;

        @NotNull(message = "Status cannot be null")
        @Size(min = 1, message = "Status must not be empty")
        private String status;

        @NotNull(message = "Category ID cannot be null")
        @Min(value = 1, message = "Category ID must be greater than 0")
        private Integer categoryId;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error: {}", ex.getStatusCode().toString());
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getStatusCode().toString());
    }
}
