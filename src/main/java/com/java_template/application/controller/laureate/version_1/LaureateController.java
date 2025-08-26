package com.java_template.application.controller.laureate.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/laureate/v1")
@Tag(name = "Laureate", description = "Operations for Laureate entity (proxy controller)")
public class LaureateController {

    private static final Logger logger = LoggerFactory.getLogger(LaureateController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LaureateController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get Laureate by technicalId", description = "Retrieve a Laureate entity by its technical UUID")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LaureateResponse.class)))
    @ApiResponse(responseCode = "400", description = "Bad Request")
    @ApiResponse(responseCode = "404", description = "Not Found")
    @ApiResponse(responseCode = "500", description = "Internal Server Error")
    @GetMapping(value = "/laureates/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getLaureateById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            LaureateResponse resp = objectMapper.convertValue(node, LaureateResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request or id format: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving laureate: {}", ex.getMessage(), ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving laureate: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "List Laureates", description = "Retrieve all Laureate entities or filter by a single field using query params (simple equals)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaureateResponse.class))))
    @ApiResponse(responseCode = "400", description = "Bad Request")
    @ApiResponse(responseCode = "500", description = "Internal Server Error")
    @GetMapping(value = "/laureates", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listLaureates(
            @Parameter(name = "filterField", description = "Field name to filter (e.g. laureateId, category, country)") @RequestParam(value = "filterField", required = false) String filterField,
            @Parameter(name = "operator", description = "Operator for filter, default EQUALS") @RequestParam(value = "operator", required = false, defaultValue = "EQUALS") String operator,
            @Parameter(name = "value", description = "Value for filter") @RequestParam(value = "value", required = false) String value
    ) {
        try {
            ArrayNode arrayNode;
            if (filterField != null && !filterField.isBlank() && value != null) {
                // Build simple search condition: JSONPath $.fieldName
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.%s".formatted(filterField), operator, value)
                );
                CompletableFuture<ArrayNode> filteredFuture = entityService.getItemsByCondition(
                        Laureate.ENTITY_NAME,
                        String.valueOf(Laureate.ENTITY_VERSION),
                        condition,
                        true
                );
                arrayNode = filteredFuture.get();
            } else {
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                        Laureate.ENTITY_NAME,
                        String.valueOf(Laureate.ENTITY_VERSION)
                );
                arrayNode = itemsFuture.get();
            }

            if (arrayNode == null) {
                return ResponseEntity.ok(List.of());
            }
            List<LaureateResponse> list = objectMapper.convertValue(arrayNode, new TypeReference<List<LaureateResponse>>() {});
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request parameters: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while listing laureates: {}", ex.getMessage(), ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while listing laureates: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Static DTOs for request/response payloads
    @Data
    @Schema(name = "LaureateResponse", description = "Laureate response payload")
    public static class LaureateResponse {
        @Schema(description = "Source id from OpenDataSoft", example = "laureate_123")
        private String laureateId;

        @Schema(description = "Full name of the laureate", example = "Ada Example")
        private String fullName;

        @Schema(description = "Prize year", example = "2024")
        private Integer prizeYear;

        @Schema(description = "Prize category", example = "physics")
        private String category;

        @Schema(description = "Country of laureate", example = "United Kingdom")
        private String country;

        @Schema(description = "Affiliations of the laureate")
        private List<String> affiliations;

        @Schema(description = "Original source JSON payload")
        private String rawPayload;

        @Schema(description = "Timestamp when detected", example = "2025-01-01T12:00:00Z")
        private String detectedAt;

        @Schema(description = "Change type (new, updated, deleted)", example = "new")
        private String changeType;

        @Schema(description = "Whether notifications queued")
        private Boolean published;
    }
}