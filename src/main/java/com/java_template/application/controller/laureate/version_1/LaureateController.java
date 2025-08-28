package com.java_template.application.controller.laureate.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Tag(name = "Laureate", description = "APIs for Laureate entity (version 1)")
@RestController
@RequestMapping("/api/laureates/v1")
public class LaureateController {

    private static final Logger logger = LoggerFactory.getLogger(LaureateController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LaureateController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Laureate", description = "Persist a single Laureate entity. Returns technicalId (UUID).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal error")
    })
    @PostMapping
    public ResponseEntity<?> createLaureate(
            @RequestBody(description = "Laureate payload", required = true, content = @Content(schema = @Schema(implementation = LaureateRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody LaureateRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            Laureate entity = mapToEntity(request);
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Laureate.ENTITY_NAME, Laureate.ENTITY_VERSION, entity);
            UUID id = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createLaureate", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating Laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Error creating Laureate", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error");
        }
    }

    @Operation(summary = "Create multiple Laureates", description = "Persist multiple Laureate entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TechnicalIdResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createLaureatesBulk(
            @RequestBody(description = "List of Laureate payloads", required = true, content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaureateRequest.class))))
            @org.springframework.web.bind.annotation.RequestBody List<LaureateRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request body must contain at least one item");
            List<Laureate> entities = new ArrayList<>();
            for (LaureateRequest r : requests) {
                entities.add(mapToEntity(r));
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Laureate.ENTITY_NAME, Laureate.ENTITY_VERSION, entities);
            List<UUID> ids = idsFuture.get();
            List<TechnicalIdResponse> resp = new ArrayList<>();
            if (ids != null) {
                for (UUID id : ids) {
                    TechnicalIdResponse t = new TechnicalIdResponse();
                    t.setTechnicalId(id.toString());
                    resp.add(t);
                }
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createLaureatesBulk", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating Laureates bulk", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Error creating Laureates bulk", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error");
        }
    }

    @Operation(summary = "Get Laureate by technicalId", description = "Retrieve a Laureate by technicalId (UUID string).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LaureateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "500", description = "Internal error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getLaureateById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Laureate not found");
            }
            LaureateResponse resp = objectMapper.treeToValue(dataPayload.getData(), LaureateResponse.class);
            resp.setTechnicalId(technicalId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getLaureateById", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting Laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Error getting Laureate", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error");
        }
    }

    @Operation(summary = "List Laureates", description = "Retrieve all Laureates (paged parameters are currently ignored and service returns available items).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal error")
    })
    @GetMapping
    public ResponseEntity<?> listLaureates() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    Laureate.ENTITY_NAME, Laureate.ENTITY_VERSION, null, null, null);
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<LaureateResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    if (payload != null && payload.getData() != null) {
                        LaureateResponse r = objectMapper.treeToValue(payload.getData(), LaureateResponse.class);
                        // If payload contains metadata for technical id, it is not guaranteed; leave technicalId null
                        responses.add(r);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing Laureates", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Error listing Laureates", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error");
        }
    }

    @Operation(summary = "Search Laureates by condition", description = "Simple field-based search. Provide JSONPath field, operator and value as query params.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal error")
    })
    @GetMapping("/search")
    public ResponseEntity<?> searchLaureates(
            @RequestParam(name = "field") String field,
            @RequestParam(name = "operator") String operator,
            @RequestParam(name = "value") String value) {
        try {
            if (field == null || operator == null || value == null)
                throw new IllegalArgumentException("field, operator and value are required");
            // Build basic condition using SearchConditionRequest and Condition
            com.java_template.common.util.SearchConditionRequest condition =
                    com.java_template.common.util.SearchConditionRequest.group("AND",
                            com.java_template.common.util.Condition.of(field, operator, value));
            CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                    Laureate.ENTITY_NAME, Laureate.ENTITY_VERSION, condition, true);
            List<DataPayload> dataPayloads = filteredItemsFuture.get();
            List<LaureateResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    if (payload != null && payload.getData() != null) {
                        LaureateResponse r = objectMapper.treeToValue(payload.getData(), LaureateResponse.class);
                        responses.add(r);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search parameters", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching Laureates", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Error searching Laureates", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error");
        }
    }

    @Operation(summary = "Update Laureate", description = "Update a Laureate by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "500", description = "Internal error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId,
            @RequestBody(description = "Laureate payload", required = true, content = @Content(schema = @Schema(implementation = LaureateRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody LaureateRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");
            Laureate entity = mapToEntity(request);
            CompletableFuture<UUID> updated = entityService.updateItem(UUID.fromString(technicalId), entity);
            UUID updatedId = updated.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updateLaureate", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating Laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Error updating Laureate", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error");
        }
    }

    @Operation(summary = "Delete Laureate", description = "Delete a Laureate by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "500", description = "Internal error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<UUID> deleted = entityService.deleteItem(UUID.fromString(technicalId));
            UUID deletedId = deleted.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for deleteLaureate", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting Laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Error deleting Laureate", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error");
        }
    }

    // Helper to map request DTO to entity (no business logic)
    private Laureate mapToEntity(LaureateRequest req) {
        Laureate e = new Laureate();
        e.setId(req.getId());
        e.setFirstname(req.getFirstname());
        e.setSurname(req.getSurname());
        e.setGender(req.getGender());
        e.setMotivation(req.getMotivation());
        e.setCategory(req.getCategory());
        e.setYear(req.getYear());
        e.setBorn(req.getBorn());
        e.setDied(req.getDied());
        e.setBorncity(req.getBorncity());
        e.setBorncountry(req.getBorncountry());
        e.setBorncountrycode(req.getBorncountrycode());
        e.setAffiliation_name(req.getAffiliation_name());
        e.setAffiliation_city(req.getAffiliation_city());
        e.setAffiliation_country(req.getAffiliation_country());
        e.setDerived_ageAtAward(req.getDerived_ageAtAward());
        e.setNormalizedCountryCode(req.getNormalizedCountryCode());
        return e;
    }

    private ResponseEntity<?> handleExecutionException(ExecutionException ee) {
        Throwable cause = ee.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found", cause);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Invalid argument in execution", cause);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
        } else {
            logger.error("Execution exception", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Execution error: " + ee.getMessage());
        }
    }

    // DTOs

    @Data
    @Schema(name = "LaureateRequest", description = "Payload to create/update Laureate")
    public static class LaureateRequest {
        @Schema(description = "Source id from API (optional)", example = "853")
        private Integer id;

        @Schema(description = "Given name", example = "Akira")
        private String firstname;

        @Schema(description = "Family name", example = "Suzuki")
        private String surname;

        @Schema(description = "Gender", example = "male")
        private String gender;

        @Schema(description = "Award motivation")
        private String motivation;

        @Schema(description = "Award category", example = "Chemistry")
        private String category;

        @Schema(description = "Award year", example = "2010")
        private String year;

        @Schema(description = "Birth date", example = "1940-01-01")
        private String born;

        @Schema(description = "Death date (nullable)")
        private String died;

        @Schema(description = "Birth city")
        private String borncity;

        @Schema(description = "Birth country")
        private String borncountry;

        @Schema(description = "Birth country code")
        private String borncountrycode;

        @Schema(description = "Affiliation name")
        private String affiliation_name;

        @Schema(description = "Affiliation city")
        private String affiliation_city;

        @Schema(description = "Affiliation country")
        private String affiliation_country;

        @Schema(description = "Derived age at award (optional)")
        private Integer derived_ageAtAward;

        @Schema(description = "Normalized country code (optional)")
        private String normalizedCountryCode;
    }

    @Data
    @Schema(name = "LaureateResponse", description = "Laureate response payload")
    public static class LaureateResponse {
        @Schema(description = "Technical ID (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        @Schema(description = "Source id from API", example = "853")
        private Integer id;

        @Schema(description = "Given name", example = "Akira")
        private String firstname;

        @Schema(description = "Family name", example = "Suzuki")
        private String surname;

        @Schema(description = "Gender", example = "male")
        private String gender;

        @Schema(description = "Award motivation")
        private String motivation;

        @Schema(description = "Award category", example = "Chemistry")
        private String category;

        @Schema(description = "Award year", example = "2010")
        private String year;

        @Schema(description = "Birth date", example = "1940-01-01")
        private String born;

        @Schema(description = "Death date (nullable)")
        private String died;

        @Schema(description = "Birth city")
        private String borncity;

        @Schema(description = "Birth country")
        private String borncountry;

        @Schema(description = "Birth country code")
        private String borncountrycode;

        @Schema(description = "Affiliation name")
        private String affiliation_name;

        @Schema(description = "Affiliation city")
        private String affiliation_city;

        @Schema(description = "Affiliation country")
        private String affiliation_country;

        @Schema(description = "Derived age at award (optional)")
        private Integer derived_ageAtAward;

        @Schema(description = "Normalized country code (optional)")
        private String normalizedCountryCode;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }
}