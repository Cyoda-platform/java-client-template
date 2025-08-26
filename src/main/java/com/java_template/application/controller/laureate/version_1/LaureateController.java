package com.java_template.application.controller.laureate.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/laureates")
@Tag(name = "Laureate", description = "API for Laureate entity (version 1)")
public class LaureateController {

    private static final Logger logger = LoggerFactory.getLogger(LaureateController.class);

    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();

    public LaureateController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Laureate", description = "Persist a single Laureate entity and return the technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @PostMapping
    public ResponseEntity<?> createLaureate(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Laureate create request", required = true,
            content = @Content(schema = @Schema(implementation = CreateLaureateRequest.class)))
                                            @RequestBody CreateLaureateRequest request) {
        try {
            Laureate entity = toEntity(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    entity
            );
            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid request for createLaureate", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createLaureate", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating laureate", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in createLaureate", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Create multiple Laureates", description = "Persist multiple Laureate entities and return their technicalIds")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createLaureatesBulk(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Bulk laureate create request", required = true,
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateLaureateRequest.class))))
                                               @RequestBody BulkCreateLaureateRequest request) {
        try {
            List<Laureate> entities = new ArrayList<>();
            for (CreateLaureateRequest r : request.getLaureates()) {
                entities.add(toEntity(r));
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    entities
            );
            List<UUID> ids = idsFuture.get();
            TechnicalIdsResponse resp = new TechnicalIdsResponse();
            List<String> sids = new ArrayList<>();
            for (UUID id : ids) sids.add(id.toString());
            resp.setTechnicalIds(sids);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid request for createLaureatesBulk", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createLaureatesBulk", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating laureates bulk", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in createLaureatesBulk", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Laureate by technicalId", description = "Retrieve a Laureate by its datastore technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LaureateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getLaureateById(@Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
                                             @PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    uuid
            );
            ObjectNode node = itemFuture.get();
            LaureateResponse resp = mapper.treeToValue(node, LaureateResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid technicalId for getLaureateById", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getLaureateById", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting laureate", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (JsonProcessingException ex) {
            logger.error("Error mapping Laureate response", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in getLaureateById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "List all Laureates", description = "Retrieve all Laureate entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @GetMapping
    public ResponseEntity<?> listLaureates() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            List<LaureateResponse> results = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                ObjectNode node = (ObjectNode) array.get(i);
                LaureateResponse resp = mapper.treeToValue(node, LaureateResponse.class);
                results.add(resp);
            }
            return ResponseEntity.ok(results);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            logger.error("ExecutionException in listLaureates", ex);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing laureates", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (JsonProcessingException ex) {
            logger.error("Error mapping laureates list", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in listLaureates", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update Laureate", description = "Update a Laureate by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateLaureate(@Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
                                            @PathVariable String technicalId,
                                            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Laureate update request", required = true,
                                                    content = @Content(schema = @Schema(implementation = UpdateLaureateRequest.class)))
                                            @RequestBody UpdateLaureateRequest request) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            Laureate entity = toEntity(request);
            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    uuid,
                    entity
            );
            UUID updated = updatedFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updated.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid request for updateLaureate", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateLaureate", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating laureate", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in updateLaureate", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete Laureate", description = "Delete a Laureate by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteLaureate(@Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
                                           @PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    uuid
            );
            UUID deleted = deletedFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deleted.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid technicalId for deleteLaureate", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteLaureate", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting laureate", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in deleteLaureate", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Utility mapping methods
    private Laureate toEntity(CreateLaureateRequest req) {
        Laureate l = new Laureate();
        l.setTechnicalId(req.getTechnicalId());
        l.setId(req.getId());
        l.setFirstname(req.getFirstname());
        l.setSurname(req.getSurname());
        l.setName(req.getName());
        l.setGender(req.getGender());
        l.setBorn(req.getBorn());
        l.setDied(req.getDied());
        l.setBornCity(req.getBornCity());
        l.setBornCountry(req.getBornCountry());
        l.setBornCountryCode(req.getBornCountryCode());
        l.setYear(req.getYear());
        l.setCategory(req.getCategory());
        l.setMotivation(req.getMotivation());
        l.setAgeAtAward(req.getAgeAtAward());
        l.setValidationStatus(req.getValidationStatus());
        l.setSourceJobTechnicalId(req.getSourceJobTechnicalId());
        if (req.getAffiliation() != null) {
            Laureate.Affiliation aff = new Laureate.Affiliation();
            aff.setName(req.getAffiliation().getName());
            aff.setCity(req.getAffiliation().getCity());
            aff.setCountry(req.getAffiliation().getCountry());
            l.setAffiliation(aff);
        }
        return l;
    }

    private Laureate toEntity(UpdateLaureateRequest req) {
        // update request uses same mapping as create for simplicity (no business logic)
        CreateLaureateRequest base = new CreateLaureateRequest();
        base.setTechnicalId(req.getTechnicalId());
        base.setId(req.getId());
        base.setFirstname(req.getFirstname());
        base.setSurname(req.getSurname());
        base.setName(req.getName());
        base.setGender(req.getGender());
        base.setBorn(req.getBorn());
        base.setDied(req.getDied());
        base.setBornCity(req.getBornCity());
        base.setBornCountry(req.getBornCountry());
        base.setBornCountryCode(req.getBornCountryCode());
        base.setYear(req.getYear());
        base.setCategory(req.getCategory());
        base.setMotivation(req.getMotivation());
        base.setAgeAtAward(req.getAgeAtAward());
        base.setValidationStatus(req.getValidationStatus());
        base.setSourceJobTechnicalId(req.getSourceJobTechnicalId());
        if (req.getAffiliation() != null) {
            CreateLaureateRequest.Affiliation a = new CreateLaureateRequest.Affiliation();
            a.setName(req.getAffiliation().getName());
            a.setCity(req.getAffiliation().getCity());
            a.setCountry(req.getAffiliation().getCountry());
            base.setAffiliation(a);
        }
        return toEntity(base);
    }

    // DTOs

    @Data
    @Schema(name = "CreateLaureateRequest", description = "Request to create a Laureate")
    public static class CreateLaureateRequest {
        @Schema(description = "Datastore technical id (optional if server generates)", example = "tech-laureate-0853")
        private String technicalId;

        @Schema(description = "Source id from caller", example = "853")
        private Integer id;

        @Schema(description = "Firstname", example = "Akira")
        private String firstname;

        @Schema(description = "Surname", example = "Suzuki")
        private String surname;

        @Schema(description = "Full name or affiliation", example = "Akira Suzuki")
        private String name;

        @Schema(description = "Gender", example = "male")
        private String gender;

        @Schema(description = "Born date (ISO)", example = "1940-04-01")
        private String born;

        @Schema(description = "Died date (ISO) or null", example = "2020-01-01")
        private String died;

        @Schema(description = "Born city", example = "Sapporo")
        private String bornCity;

        @Schema(description = "Born country", example = "Japan")
        private String bornCountry;

        @Schema(description = "Born country code", example = "JP")
        private String bornCountryCode;

        @Schema(description = "Award year", example = "2010")
        private String year;

        @Schema(description = "Category", example = "Chemistry")
        private String category;

        @Schema(description = "Motivation", example = "For contributions to ...")
        private String motivation;

        @Schema(description = "Affiliation information")
        private Affiliation affiliation;

        @Schema(description = "Age at award", example = "80")
        private Integer ageAtAward;

        @Schema(description = "Validation status", example = "VALID")
        private String validationStatus;

        @Schema(description = "Source job technical id", example = "tech-job-0001")
        private String sourceJobTechnicalId;

        @Data
        @Schema(name = "Affiliation", description = "Affiliation details")
        public static class Affiliation {
            @Schema(description = "Affiliation name", example = "Hokkaido University")
            private String name;
            @Schema(description = "Affiliation city", example = "Sapporo")
            private String city;
            @Schema(description = "Affiliation country", example = "Japan")
            private String country;
        }
    }

    @Data
    @Schema(name = "BulkCreateLaureateRequest", description = "Bulk create request for Laureates")
    public static class BulkCreateLaureateRequest {
        @Schema(description = "List of laureates to create")
        private List<CreateLaureateRequest> laureates;
    }

    @Data
    @Schema(name = "UpdateLaureateRequest", description = "Request to update a Laureate")
    public static class UpdateLaureateRequest {
        @Schema(description = "Datastore technical id (optional)", example = "tech-laureate-0853")
        private String technicalId;

        @Schema(description = "Source id from caller", example = "853")
        private Integer id;

        @Schema(description = "Firstname", example = "Akira")
        private String firstname;

        @Schema(description = "Surname", example = "Suzuki")
        private String surname;

        @Schema(description = "Full name or affiliation", example = "Akira Suzuki")
        private String name;

        @Schema(description = "Gender", example = "male")
        private String gender;

        @Schema(description = "Born date (ISO)", example = "1940-04-01")
        private String born;

        @Schema(description = "Died date (ISO) or null", example = "2020-01-01")
        private String died;

        @Schema(description = "Born city", example = "Sapporo")
        private String bornCity;

        @Schema(description = "Born country", example = "Japan")
        private String bornCountry;

        @Schema(description = "Born country code", example = "JP")
        private String bornCountryCode;

        @Schema(description = "Award year", example = "2010")
        private String year;

        @Schema(description = "Category", example = "Chemistry")
        private String category;

        @Schema(description = "Motivation", example = "For contributions to ...")
        private String motivation;

        @Schema(description = "Affiliation information")
        private UpdateAffiliation affiliation;

        @Schema(description = "Age at award", example = "80")
        private Integer ageAtAward;

        @Schema(description = "Validation status", example = "VALID")
        private String validationStatus;

        @Schema(description = "Source job technical id", example = "tech-job-0001")
        private String sourceJobTechnicalId;

        @Data
        @Schema(name = "UpdateAffiliation", description = "Affiliation details for update")
        public static class UpdateAffiliation {
            @Schema(description = "Affiliation name", example = "Hokkaido University")
            private String name;
            @Schema(description = "Affiliation city", example = "Sapporo")
            private String city;
            @Schema(description = "Affiliation country", example = "Japan")
            private String country;
        }
    }

    @Data
    @Schema(name = "LaureateResponse", description = "Laureate response payload")
    public static class LaureateResponse {
        @Schema(description = "Datastore technical id", example = "tech-laureate-0853")
        private String technicalId;

        @Schema(description = "Source id", example = "853")
        private Integer id;

        @Schema(description = "Firstname", example = "Akira")
        private String firstname;

        @Schema(description = "Surname", example = "Suzuki")
        private String surname;

        @Schema(description = "Full name or affiliation", example = "Akira Suzuki")
        private String name;

        @Schema(description = "Gender", example = "male")
        private String gender;

        @Schema(description = "Born date (ISO)", example = "1940-04-01")
        private String born;

        @Schema(description = "Died date (ISO) or null", example = "2020-01-01")
        private String died;

        @Schema(description = "Born city", example = "Sapporo")
        private String bornCity;

        @Schema(description = "Born country", example = "Japan")
        private String bornCountry;

        @Schema(description = "Born country code", example = "JP")
        private String bornCountryCode;

        @Schema(description = "Award year", example = "2010")
        private String year;

        @Schema(description = "Category", example = "Chemistry")
        private String category;

        @Schema(description = "Motivation", example = "For contributions to ...")
        private String motivation;

        @Schema(description = "Affiliation information")
        private Affiliation affiliation;

        @Schema(description = "Age at award", example = "80")
        private Integer ageAtAward;

        @Schema(description = "Validation status", example = "VALID")
        private String validationStatus;

        @Schema(description = "Source job technical id", example = "tech-job-0001")
        private String sourceJobTechnicalId;

        @Data
        @Schema(name = "Affiliation", description = "Affiliation details")
        public static class Affiliation {
            @Schema(description = "Affiliation name", example = "Hokkaido University")
            private String name;
            @Schema(description = "Affiliation city", example = "Sapporo")
            private String city;
            @Schema(description = "Affiliation country", example = "Japan")
            private String country;
        }
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Simple response returning a technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Datastore technical id", example = "tech-laureate-0853")
        private String technicalId;
    }

    @Data
    @Schema(name = "TechnicalIdsResponse", description = "Response returning multiple technicalIds")
    public static class TechnicalIdsResponse {
        @Schema(description = "List of datastore technical ids")
        private List<String> technicalIds;
    }
}