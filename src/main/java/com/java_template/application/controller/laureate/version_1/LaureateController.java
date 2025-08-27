package com.java_template.application.controller.laureate.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.java_template.application.entity.laureate.version_1.Laureate;

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
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Tag(name = "Laureate")
@RestController
@RequestMapping("/api/laureates/v1")
public class LaureateController {

    private static final Logger logger = LoggerFactory.getLogger(LaureateController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public LaureateController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Laureate", description = "Persist a Laureate entity (triggers Laureate workflow). Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = LaureateController.TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createLaureate(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Laureate payload", required = true,
                    content = @Content(schema = @Schema(implementation = LaureateRequest.class)))
            @RequestBody LaureateRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is missing");
            }

            Laureate entity = mapRequestToEntity(request);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Laureate.ENTITY_NAME,
                    Laureate.ENTITY_VERSION,
                    entity
            );
            UUID entityId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(entityId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create laureate: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating laureate", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted when creating laureate", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Unexpected error when creating laureate", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Get Laureate by technicalId", description = "Retrieve a Laureate by technical UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = LaureateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getLaureateById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            ObjectNode node = dataPayload != null ? (ObjectNode) dataPayload.getData() : null;
            if (node == null || node.isEmpty(null)) {
                return ResponseEntity.status(404).body("Laureate not found");
            }
            LaureateResponse response = objectMapper.treeToValue(node, LaureateResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get laureate: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when retrieving laureate", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted when retrieving laureate", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Unexpected error when retrieving laureate", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "List Laureates", description = "Retrieve all Laureates")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE, params = {})
    public ResponseEntity<?> listLaureates() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    Laureate.ENTITY_NAME,
                    Laureate.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<LaureateResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    LaureateResponse r = objectMapper.treeToValue(data, LaureateResponse.class);
                    responses.add(r);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when listing laureates", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted when listing laureates", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Unexpected error when listing laureates", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Update Laureate", description = "Update an existing Laureate by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = LaureateController.TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(value = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Laureate payload", required = true,
                    content = @Content(schema = @Schema(implementation = LaureateRequest.class)))
            @RequestBody LaureateRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is missing");
            }

            Laureate entity = mapRequestToEntity(request);

            CompletableFuture<UUID> updatedId = entityService.updateItem(UUID.fromString(technicalId), entity);
            UUID entityId = updatedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(entityId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to update laureate: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when updating laureate", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted when updating laureate", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Unexpected error when updating laureate", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Delete Laureate", description = "Delete a Laureate by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = LaureateController.TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<UUID> deletedId = entityService.deleteItem(UUID.fromString(technicalId));
            UUID entityId = deletedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(entityId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to delete laureate: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when deleting laureate", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted when deleting laureate", ie);
            return ResponseEntity.status(500).body("Internal server error");
        } catch (Exception e) {
            logger.error("Unexpected error when deleting laureate", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    // Manual mapping from request DTO to entity to avoid reflective population in controller logic
    private Laureate mapRequestToEntity(LaureateRequest req) {
        Laureate e = new Laureate();
        e.setId(req.getId());
        e.setFirstname(req.getFirstname());
        e.setSurname(req.getSurname());
        e.setCategory(req.getCategory());
        e.setYear(req.getYear());
        e.setMotivation(req.getMotivation());
        e.setGender(req.getGender());
        e.setBorn(req.getBorn());
        e.setDied(req.getDied());
        e.setBorncity(req.getBorncity());
        e.setBorncountry(req.getBorncountry());
        e.setBorncountrycode(req.getBorncountrycode());
        e.setAffiliationName(req.getAffiliationName());
        e.setAffiliationCity(req.getAffiliationCity());
        e.setAffiliationCountry(req.getAffiliationCountry());
        e.setEnrichedAgeAtAward(req.getEnrichedAgeAtAward());
        e.setNormalizedCountryCode(req.getNormalizedCountryCode());
        e.setValidationErrors(req.getValidationErrors());
        e.setValidationStatus(req.getValidationStatus());
        return e;
    }

    // DTOs

    @Data
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the persisted entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    public static class LaureateRequest {
        @Schema(description = "Source id", example = "853")
        private Integer id;
        @Schema(description = "First name", example = "Akira")
        private String firstname;
        @Schema(description = "Surname", example = "Suzuki")
        private String surname;
        @Schema(description = "Category", example = "Chemistry")
        private String category;
        @Schema(description = "Award year", example = "2010")
        private String year;
        @Schema(description = "Motivation for award")
        private String motivation;
        @Schema(description = "Gender", example = "male")
        private String gender;
        @Schema(description = "Born date (ISO)", example = "1930-09-12")
        private String born;
        @Schema(description = "Died date (ISO) or null", example = "null")
        private String died;
        @Schema(description = "Born city", example = "Sapporo")
        private String borncity;
        @Schema(description = "Born country", example = "Japan")
        private String borncountry;
        @Schema(description = "Born country code", example = "JP")
        private String borncountrycode;
        @Schema(description = "Affiliation name", example = "Hokkaido University")
        private String affiliationName;
        @Schema(description = "Affiliation city", example = "Sapporo")
        private String affiliationCity;
        @Schema(description = "Affiliation country", example = "Japan")
        private String affiliationCountry;
        @Schema(description = "Enriched age at award", example = "80")
        private Integer enrichedAgeAtAward;
        @Schema(description = "Normalized country code", example = "JP")
        private String normalizedCountryCode;
        @Schema(description = "Validation errors")
        private List<String> validationErrors;
        @Schema(description = "Validation status", example = "OK")
        private String validationStatus;
    }

    @Data
    public static class LaureateResponse {
        @Schema(description = "Source id", example = "853")
        private Integer id;
        @Schema(description = "First name", example = "Akira")
        private String firstname;
        @Schema(description = "Surname", example = "Suzuki")
        private String surname;
        @Schema(description = "Category", example = "Chemistry")
        private String category;
        @Schema(description = "Award year", example = "2010")
        private String year;
        @Schema(description = "Motivation for award")
        private String motivation;
        @Schema(description = "Gender", example = "male")
        private String gender;
        @Schema(description = "Born date (ISO)", example = "1930-09-12")
        private String born;
        @Schema(description = "Died date (ISO) or null", example = "null")
        private String died;
        @Schema(description = "Born city", example = "Sapporo")
        private String borncity;
        @Schema(description = "Born country", example = "Japan")
        private String borncountry;
        @Schema(description = "Born country code", example = "JP")
        private String borncountrycode;
        @Schema(description = "Affiliation name", example = "Hokkaido University")
        private String affiliationName;
        @Schema(description = "Affiliation city", example = "Sapporo")
        private String affiliationCity;
        @Schema(description = "Affiliation country", example = "Japan")
        private String affiliationCountry;
        @Schema(description = "Enriched age at award", example = "80")
        private Integer enrichedAgeAtAward;
        @Schema(description = "Normalized country code", example = "JP")
        private String normalizedCountryCode;
        @Schema(description = "Validation errors")
        private List<String> validationErrors;
        @Schema(description = "Validation status", example = "OK")
        private String validationStatus;
    }
}