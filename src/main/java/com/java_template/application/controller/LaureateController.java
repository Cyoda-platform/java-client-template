package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/laureates")
@Tag(name = "Laureate API", description = "CRUD endpoints for Laureate entity (proxy to EntityService)")
public class LaureateController {

    private static final Logger logger = LoggerFactory.getLogger(LaureateController.class);

    private final EntityService entityService;

    public LaureateController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Laureate", description = "Persist a Laureate and trigger laureate workflows asynchronously. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createLaureate(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Laureate payload") @RequestBody LaureateRequest request) {
        try {
            // basic validation
            if ((request.getFirstname() == null || request.getFirstname().isBlank()) && (request.getSurname() == null || request.getSurname().isBlank())) {
                throw new IllegalArgumentException("At least one of firstname or surname is required");
            }
            if (request.getYear() == null || request.getYear().isBlank()) {
                throw new IllegalArgumentException("year is required");
            }
            if (request.getCategory() == null || request.getCategory().isBlank()) {
                throw new IllegalArgumentException("category is required");
            }

            Laureate laureate = new Laureate();
            laureate.setExternalId(request.getExternalId());
            laureate.setFirstname(request.getFirstname());
            laureate.setSurname(request.getSurname());
            laureate.setBorn(request.getBorn());
            laureate.setDied(request.getDied());
            laureate.setBorncountry(request.getBorncountry());
            laureate.setBorncountrycode(request.getBorncountrycode());
            laureate.setBorncity(request.getBorncity());
            laureate.setGender(request.getGender());
            laureate.setYear(request.getYear());
            laureate.setCategory(request.getCategory());
            laureate.setMotivation(request.getMotivation());
            laureate.setAffiliation_name(request.getAffiliation_name());
            laureate.setAffiliation_city(request.getAffiliation_city());
            laureate.setAffiliation_country(request.getAffiliation_country());
            laureate.setRawPayload(request.getRawPayload());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    laureate
            );
            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createLaureate: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during createLaureate", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in createLaureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Laureate by technicalId", description = "Retrieve stored Laureate by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            if (node == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Laureate not found");
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during getLaureate", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getLaureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Laureates", description = "Retrieve all Laureates")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class))))
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
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            }
            logger.error("ExecutionException during listLaureates", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in listLaureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Laureates by condition", description = "Filter Laureates using SearchConditionRequest")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class))))
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchLaureates(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition") @RequestBody SearchConditionRequest condition) {
        try {
            CompletableFuture<ArrayNode> filtered = entityService.getItemsByCondition(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode arr = filtered.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            }
            logger.error("ExecutionException during searchLaureates", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in searchLaureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Laureate", description = "Update existing Laureate by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class)))
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateLaureate(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId,
                                           @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Laureate payload") @RequestBody LaureateRequest request) {
        try {
            Laureate laureate = new Laureate();
            laureate.setExternalId(request.getExternalId());
            laureate.setFirstname(request.getFirstname());
            laureate.setSurname(request.getSurname());
            laureate.setBorn(request.getBorn());
            laureate.setDied(request.getDied());
            laureate.setBorncountry(request.getBorncountry());
            laureate.setBorncountrycode(request.getBorncountrycode());
            laureate.setBorncity(request.getBorncity());
            laureate.setGender(request.getGender());
            laureate.setYear(request.getYear());
            laureate.setCategory(request.getCategory());
            laureate.setMotivation(request.getMotivation());
            laureate.setAffiliation_name(request.getAffiliation_name());
            laureate.setAffiliation_city(request.getAffiliation_city());
            laureate.setAffiliation_country(request.getAffiliation_country());
            laureate.setRawPayload(request.getRawPayload());

            CompletableFuture<UUID> updated = entityService.updateItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    laureate
            );
            UUID id = updated.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during updateLaureate", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in updateLaureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Laureate", description = "Delete Laureate by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class)))
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteLaureate(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            CompletableFuture<UUID> deleted = entityService.deleteItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID id = deleted.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException during deleteLaureate", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in deleteLaureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class LaureateRequest {
        @Schema(description = "External ID from source dataset")
        private Integer externalId;
        @Schema(description = "First name")
        private String firstname;
        @Schema(description = "Surname")
        private String surname;
        @Schema(description = "Born date or string")
        private String born;
        @Schema(description = "Died date or null")
        private String died;
        @Schema(description = "Born country")
        private String borncountry;
        @Schema(description = "Born country code from source")
        private String borncountrycode;
        @Schema(description = "Born city")
        private String borncity;
        @Schema(description = "Gender")
        private String gender;
        @Schema(description = "Award year as string")
        private String year;
        @Schema(description = "Category")
        private String category;
        @Schema(description = "Motivation text")
        private String motivation;
        @Schema(description = "Affiliation name")
        private String affiliation_name;
        @Schema(description = "Affiliation city")
        private String affiliation_city;
        @Schema(description = "Affiliation country")
        private String affiliation_country;
        @Schema(description = "Raw original payload")
        private Object rawPayload;
    }

    @Data
    static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the created/updated/deleted entity")
        private String technicalId;
    }
}
