package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.java_template.common.service.EntityService;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/laureates")
@Tag(name = "Laureate Controller", description = "Read-only access for laureates. Laureates are created by system workflows.")
public class LaureateController {
    private final Logger logger = LoggerFactory.getLogger(LaureateController.class);
    private final EntityService entityService;

    public LaureateController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get Laureate by technicalId", description = "Retrieve a Laureate by its technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getLaureate(@Parameter(name = "technicalId", description = "Technical ID of the laureate") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            if (node == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid getLaureate request", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.badRequest().body(cause.getMessage());
            logger.error("Execution error fetching laureate", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error fetching laureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Laureates", description = "List all laureates")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class)))
    })
    @GetMapping
    public ResponseEntity<?> listLaureates() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("Execution error listing laureates", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing laureates", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error listing laureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Filter Laureates", description = "Filter laureates by simple field condition")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class)))
    })
    @GetMapping("/filter")
    public ResponseEntity<?> filterLaureates(@RequestParam String field, @RequestParam(defaultValue = "EQUALS") String operator, @RequestParam String value) {
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$." + field, operator, value)
            );
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("Execution error filtering laureates", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while filtering laureates", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid filter params", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error filtering laureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Laureate by businessId", description = "Retrieve a Laureate by its business id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @GetMapping("/by-business/{businessId}")
    public ResponseEntity<?> getLaureateByBusinessId(@Parameter(name = "businessId", description = "Business ID of the laureate") @PathVariable String businessId) {
        try {
            if (businessId == null || businessId.isBlank()) throw new IllegalArgumentException("businessId is required");
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.id", "EQUALS", businessId)
            );
            CompletableFuture<ArrayNode> filtered = entityService.getItemsByCondition(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode arr = filtered.get();
            if (arr == null || arr.size() == 0) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            return ResponseEntity.ok(arr.get(0));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("Execution error fetching laureate by business id", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching laureate by business id", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error fetching laureate by business id", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class LaureateResponse {
        @Schema(description = "Technical id")
        private String technicalId;
        @Schema(description = "Business id")
        private String businessId;
        @Schema(description = "Full name")
        private String fullName;
        @Schema(description = "Year")
        private String year;
        @Schema(description = "Category")
        private String category;
        @Schema(description = "Affiliation")
        private String affiliation;
        @Schema(description = "Current version")
        private Integer currentVersion;
        @Schema(description = "Last updated timestamp")
        private String lastUpdated;
    }
}
