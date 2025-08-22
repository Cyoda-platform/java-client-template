package com.java_template.application.controller.validation_result.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.validation_result.version_1.Validation_Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/validation-results")
@Tag(name = "Validation_Result")
public class Validation_ResultController {

    private static final Logger logger = LoggerFactory.getLogger(Validation_ResultController.class);

    private final EntityService entityService;

    public Validation_ResultController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Validation_Result", description = "Creates a Validation_Result entity. Returns only technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createValidationResult(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Validation_Result payload", required = true,
                    content = @Content(schema = @Schema(implementation = ValidationResultRequest.class)))
            @RequestBody ValidationResultRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Validation_Result entity = new Validation_Result();
            entity.setIsValid(request.getIsValid());
            entity.setMissingFields(request.getMissingFields());
            entity.setErrorMessage(request.getErrorMessage());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Validation_Result.ENTITY_NAME,
                    String.valueOf(Validation_Result.ENTITY_VERSION),
                    entity
            );

            UUID id = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for creating Validation_Result", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException ee) {
            logger.error("ExecutionException while creating Validation_Result", ee);
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while creating Validation_Result", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Validation_Result", description = "Creates multiple Validation_Result entities. Returns technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createValidationResultsBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of Validation_Result payloads", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ValidationResultRequest.class))))
            @RequestBody List<ValidationResultRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request body must contain at least one item");
            }

            List<Validation_Result> entities = requests.stream().map(r -> {
                Validation_Result e = new Validation_Result();
                e.setIsValid(r.getIsValid());
                e.setMissingFields(r.getMissingFields());
                e.setErrorMessage(r.getErrorMessage());
                return e;
            }).collect(Collectors.toList());

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Validation_Result.ENTITY_NAME,
                    String.valueOf(Validation_Result.ENTITY_VERSION),
                    entities
            );

            List<UUID> ids = idsFuture.get();
            TechnicalIdsResponse resp = new TechnicalIdsResponse();
            resp.setTechnicalIds(ids.stream().map(UUID::toString).collect(Collectors.toList()));
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid batch request for creating Validation_Result", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException ee) {
            logger.error("ExecutionException while creating Validation_Result batch", ee);
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while creating Validation_Result batch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Validation_Result by technicalId", description = "Retrieve a Validation_Result by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ValidationResultResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getValidationResult(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Validation_Result.ENTITY_NAME,
                    String.valueOf(Validation_Result.ENTITY_VERSION),
                    id
            );

            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Validation_Result not found");
            }

            ValidationResultResponse resp = new ValidationResultResponse();
            resp.setTechnicalId(technicalId);
            if (node.has("isValid")) {
                resp.setIsValid(node.get("isValid").isNull() ? null : node.get("isValid").asBoolean());
            }
            if (node.has("missingFields") && node.get("missingFields").isArray()) {
                ArrayNode arr = (ArrayNode) node.get("missingFields");
                List<String> mf = new ArrayList<>();
                arr.forEach(n -> mf.add(n.isNull() ? null : n.asText()));
                resp.setMissingFields(mf);
            }
            if (node.has("errorMessage")) {
                resp.setErrorMessage(node.get("errorMessage").isNull() ? null : node.get("errorMessage").asText());
            }

            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid get request for Validation_Result", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException ee) {
            logger.error("ExecutionException while retrieving Validation_Result", ee);
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving Validation_Result", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Validation_Result items", description = "Retrieve all Validation_Result entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ValidationResultResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllValidationResults() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Validation_Result.ENTITY_NAME,
                    String.valueOf(Validation_Result.ENTITY_VERSION)
            );

            ArrayNode array = itemsFuture.get();
            return ResponseEntity.ok(array);

        } catch (ExecutionException ee) {
            logger.error("ExecutionException while retrieving Validation_Result list", ee);
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving Validation_Result list", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Filter Validation_Result items", description = "Retrieve Validation_Result entities by condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ValidationResultResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/filter")
    public ResponseEntity<?> filterValidationResults(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) {
                throw new IllegalArgumentException("condition is required");
            }

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Validation_Result.ENTITY_NAME,
                    String.valueOf(Validation_Result.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode array = filteredItemsFuture.get();
            return ResponseEntity.ok(array);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid filter request for Validation_Result", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException ee) {
            logger.error("ExecutionException while filtering Validation_Result", ee);
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while filtering Validation_Result", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Validation_Result", description = "Update a Validation_Result entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateValidationResult(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Validation_Result payload", required = true,
                    content = @Content(schema = @Schema(implementation = ValidationResultRequest.class)))
            @RequestBody ValidationResultRequest request) {
        try {
            if (technicalId == null || request == null) {
                throw new IllegalArgumentException("technicalId and request body are required");
            }
            UUID id = UUID.fromString(technicalId);

            Validation_Result entity = new Validation_Result();
            entity.setIsValid(request.getIsValid());
            entity.setMissingFields(request.getMissingFields());
            entity.setErrorMessage(request.getErrorMessage());

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Validation_Result.ENTITY_NAME,
                    String.valueOf(Validation_Result.ENTITY_VERSION),
                    id,
                    entity
            );

            UUID updatedId = updatedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid update request for Validation_Result", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException ee) {
            logger.error("ExecutionException while updating Validation_Result", ee);
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while updating Validation_Result", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Validation_Result", description = "Delete a Validation_Result entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteValidationResult(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Validation_Result.ENTITY_NAME,
                    String.valueOf(Validation_Result.ENTITY_VERSION),
                    id
            );

            UUID deletedId = deletedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid delete request for Validation_Result", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException ee) {
            logger.error("ExecutionException while deleting Validation_Result", ee);
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while deleting Validation_Result", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    @Schema(name = "ValidationResultRequest", description = "Request payload for Validation_Result")
    public static class ValidationResultRequest {
        @Schema(description = "Indicates whether the payload is valid", example = "true")
        private Boolean isValid;

        @Schema(description = "List of missing fields", example = "[\"id\",\"type\"]")
        private List<String> missingFields;

        @Schema(description = "Human readable error message", example = "Missing required fields")
        private String errorMessage;
    }

    @Data
    @Schema(name = "ValidationResultResponse", description = "Response payload for Validation_Result")
    public static class ValidationResultResponse {
        @Schema(description = "Technical identifier of the entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        @Schema(description = "Indicates whether the payload is valid", example = "true")
        private Boolean isValid;

        @Schema(description = "List of missing fields", example = "[\"id\",\"type\"]")
        private List<String> missingFields;

        @Schema(description = "Human readable error message", example = "Missing required fields")
        private String errorMessage;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing a single technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical identifier", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "TechnicalIdsResponse", description = "Response containing multiple technicalIds")
    public static class TechnicalIdsResponse {
        @Schema(description = "Technical identifiers")
        private List<String> technicalIds;
    }
}