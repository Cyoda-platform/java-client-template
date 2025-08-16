package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/pets")
@Tag(name = "Pet API", description = "APIs to query Pet entities")
public class PetController {

    private static final Logger logger = LoggerFactory.getLogger(PetController.class);
    private final EntityService entityService;

    public PetController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get Pet by technicalId", description = "Retrieves the full persisted Pet by technical id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPet(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "List Pets", description = "Retrieves a list of pets. Supports basic filtering by species and status.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listPets(
            @Parameter(description = "Filter by species (e.g., dog, cat)") @RequestParam(required = false) String species,
            @Parameter(description = "Filter by status (e.g., AVAILABLE)") @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        try {
            CompletableFuture<ArrayNode> future;
            if ((species != null && !species.isBlank()) || (status != null && !status.isBlank())) {
                if (species != null && !species.isBlank() && status != null && !status.isBlank()) {
                    SearchConditionRequest condition = SearchConditionRequest.group("AND",
                            Condition.of("$.species", "EQUALS", species),
                            Condition.of("$.status", "EQUALS", status)
                    );
                    future = entityService.getItemsByCondition(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), condition, true);
                } else if (species != null && !species.isBlank()) {
                    SearchConditionRequest condition = SearchConditionRequest.group("AND",
                            Condition.of("$.species", "EQUALS", species)
                    );
                    future = entityService.getItemsByCondition(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), condition, true);
                } else {
                    SearchConditionRequest condition = SearchConditionRequest.group("AND",
                            Condition.of("$.status", "EQUALS", status)
                    );
                    future = entityService.getItemsByCondition(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), condition, true);
                }
            } else {
                future = entityService.getItems(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION));
            }

            ArrayNode array = future.get();
            return ResponseEntity.ok(array);
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }
}
