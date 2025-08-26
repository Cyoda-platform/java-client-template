package com.java_template.application.controller.laureate.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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
@Tag(name = "Laureate Controller", description = "Dumb proxy controller for Laureate entity operations")
public class LaureateController {

    private static final Logger logger = LoggerFactory.getLogger(LaureateController.class);

    private final EntityService entityService;

    public LaureateController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Laureate", description = "Persist a single Laureate entity. Returns only technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createLaureate(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Laureate payload"
    ) @RequestBody CreateLaureateRequest request) {
        try {
            Laureate data = mapToEntity(request);
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    data
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new IdResponse(technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request payload", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating laureate", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating laureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while creating laureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Laureates", description = "Persist multiple Laureate entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = IdResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createLaureates(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Array of Laureate payloads"
    ) @RequestBody List<CreateLaureateRequest> requests) {
        try {
            List<Laureate> entities = requests.stream().map(this::mapToEntity).toList();
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    entities
            );
            List<UUID> ids = idsFuture.get();
            List<IdResponse> responses = ids.stream().map(id -> new IdResponse(id.toString())).toList();
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid batch payload", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating laureates", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating laureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while creating laureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Laureates", description = "Retrieve all persisted Laureates")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllLaureates() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving laureates", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving laureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving laureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Laureate by technicalId", description = "Retrieve a single Laureate by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LaureateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getLaureateById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    uuid
            );
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId or request", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving laureate", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving laureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving laureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Laureates by condition", description = "Filter Laureates using SearchConditionRequest")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchLaureates(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Search condition payload"
    ) @RequestBody SearchConditionRequest condition) {
        try {
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid search condition", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while searching laureates", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching laureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while searching laureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Laureate", description = "Update a Laureate by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Laureate payload") @RequestBody CreateLaureateRequest request) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            Laureate data = mapToEntity(request);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    uuid,
                    data
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(updatedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId or payload", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while updating laureate", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating laureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while updating laureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Laureate", description = "Delete a Laureate by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    uuid
            );
            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(deletedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while deleting laureate", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting laureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while deleting laureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Map incoming DTO to entity instance (no business logic)
    private Laureate mapToEntity(CreateLaureateRequest req) {
        Laureate l = new Laureate();
        l.setId(req.getId());
        l.setFirstname(req.getFirstname());
        l.setSurname(req.getSurname());
        l.setGender(req.getGender());
        l.setBorn(req.getBorn());
        l.setDied(req.getDied());
        l.setBornCountry(req.getBorncountry());
        l.setBornCountryCode(req.getBorncountrycode());
        l.setBornCity(req.getBorncity());
        l.setYear(req.getYear());
        l.setCategory(req.getCategory());
        l.setMotivation(req.getMotivation());
        l.setAffiliationName(req.getAffiliation_name());
        l.setAffiliationCity(req.getAffiliation_city());
        l.setAffiliationCountry(req.getAffiliation_country());
        l.setAge(req.getAge());
        l.setNormalizedCountryCode(req.getNormalizedCountryCode());
        l.setSourceJobId(req.getSourceJobId());
        l.setCreatedAt(req.getCreatedAt());
        return l;
    }

    // DTOs

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "CreateLaureateRequest", description = "Payload for creating/updating a Laureate")
    public static class CreateLaureateRequest {
        @Schema(description = "Source laureate id")
        private Integer id;

        @Schema(description = "First name")
        private String firstname;

        @Schema(description = "Surname")
        private String surname;

        @Schema(description = "Gender")
        private String gender;

        @Schema(description = "Date of birth ISO")
        private String born;

        @Schema(description = "Date of death ISO or null")
        private String died;

        @JsonProperty("borncountry")
        @Schema(description = "Born country (raw)")
        private String borncountry;

        @JsonProperty("borncountrycode")
        @Schema(description = "Born country code (raw)")
        private String borncountrycode;

        @JsonProperty("borncity")
        @Schema(description = "Born city")
        private String borncity;

        @Schema(description = "Award year")
        private String year;

        @Schema(description = "Award category")
        private String category;

        @Schema(description = "Award motivation")
        private String motivation;

        @JsonProperty("affiliation_name")
        @Schema(description = "Affiliation name")
        private String affiliation_name;

        @JsonProperty("affiliation_city")
        @Schema(description = "Affiliation city")
        private String affiliation_city;

        @JsonProperty("affiliation_country")
        @Schema(description = "Affiliation country")
        private String affiliation_country;

        @Schema(description = "Calculated age")
        private Integer age;

        @Schema(description = "Normalized country code")
        private String normalizedCountryCode;

        @Schema(description = "Source Job id")
        private String sourceJobId;

        @Schema(description = "Created timestamp ISO")
        private String createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "LaureateResponse", description = "Laureate retrieval response")
    public static class LaureateResponse {
        @Schema(description = "Technical id")
        private String technicalId;

        @Schema(description = "Source laureate id")
        private Integer id;

        @Schema(description = "First name")
        private String firstname;

        @Schema(description = "Surname")
        private String surname;

        @Schema(description = "Born date ISO")
        private String born;

        @Schema(description = "Died date ISO or null")
        private String died;

        @JsonProperty("borncountry")
        @Schema(description = "Born country")
        private String borncountry;

        @JsonProperty("borncountrycode")
        @Schema(description = "Born country code")
        private String borncountrycode;

        @JsonProperty("borncity")
        @Schema(description = "Born city")
        private String borncity;

        @Schema(description = "Gender")
        private String gender;

        @Schema(description = "Year")
        private String year;

        @Schema(description = "Category")
        private String category;

        @Schema(description = "Motivation")
        private String motivation;

        @JsonProperty("affiliation_name")
        @Schema(description = "Affiliation name")
        private String affiliation_name;

        @JsonProperty("affiliation_city")
        @Schema(description = "Affiliation city")
        private String affiliation_city;

        @JsonProperty("affiliation_country")
        @Schema(description = "Affiliation country")
        private String affiliation_country;

        @Schema(description = "Age")
        private Integer age;

        @Schema(description = "Source job id")
        private String sourceJobId;

        @Schema(description = "CreatedAt timestamp")
        private String createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "IdResponse", description = "Technical id response")
    public static class IdResponse {
        @Schema(description = "Technical id")
        private String technicalId;
    }
}