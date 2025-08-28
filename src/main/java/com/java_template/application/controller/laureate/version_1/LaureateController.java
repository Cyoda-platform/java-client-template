package com.java_template.application.controller.laureate.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.laureate.version_1.Laureate;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/laureates/v1")
@Tag(name = "Laureate Controller", description = "Controller proxy for Laureate entity operations (version 1)")
public class LaureateController {

    private static final Logger logger = LoggerFactory.getLogger(LaureateController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LaureateController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Laureate", description = "Persist a Laureate entity and start its associated workflow. Returns the generated technicalId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<TechnicalIdResponse> createLaureate(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Laureate payload", required = true, content = @Content(schema = @Schema(implementation = LaureateRequest.class)))
            @RequestBody LaureateRequest request) {
        try {
            // Basic null-check
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            // Map request DTO to entity
            Laureate entity = new Laureate();
            if (request.getId() != null) {
                entity.setId(String.valueOf(request.getId()));
            }
            entity.setFirstname(request.getFirstname());
            entity.setSurname(request.getSurname());
            entity.setGender(request.getGender());
            entity.setBorn(request.getBorn());
            entity.setDied(request.getDied());
            entity.setBornCountry(request.getBorncountry());
            entity.setBornCountryCode(request.getBorncountrycode());
            entity.setBornCity(request.getBorncity());
            entity.setYear(request.getYear());
            entity.setCategory(request.getCategory());
            entity.setMotivation(request.getMotivation());
            entity.setAffiliationName(request.getAffiliation_name());
            entity.setAffiliationCity(request.getAffiliation_city());
            entity.setAffiliationCountry(request.getAffiliation_country());
            entity.setAgeAtAward(request.getAge_at_award());
            entity.setValidated(request.getValidated());
            entity.setNormalizedCountryCode(request.getNormalized_country_code());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Laureate.ENTITY_NAME,
                    Laureate.ENTITY_VERSION,
                    entity
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for creating laureate: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while creating laureate", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating laureate", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while creating laureate", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get Laureate by technicalId", description = "Retrieve a single Laureate by its technicalId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LaureateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<LaureateResponse> getLaureateById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null || dataPayload.getData().isNull()) {
                return ResponseEntity.status(404).build();
            }
            // Convert payload to entity then to response DTO
            Laureate entity = objectMapper.treeToValue(dataPayload.getData(), Laureate.class);
            LaureateResponse response = mapEntityToResponse(entity);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getLaureateById: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while retrieving laureate", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving laureate", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving laureate", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "List Laureates", description = "Retrieve all Laureates (paginated parameters not supported by this proxy endpoint)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<List<LaureateResponse>> listLaureates() {
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
                    if (payload == null || payload.getData() == null || payload.getData().isNull()) {
                        continue;
                    }
                    Laureate entity = objectMapper.treeToValue(payload.getData(), Laureate.class);
                    LaureateResponse resp = mapEntityToResponse(entity);
                    responses.add(resp);
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while listing laureates", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing laureates", ie);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while listing laureates", e);
            return ResponseEntity.status(500).build();
        }
    }

    // Helper mapping from entity to response (adapts camelCase entity fields to payload fields defined in functional requirements)
    private LaureateResponse mapEntityToResponse(Laureate entity) {
        if (entity == null) return null;
        LaureateResponse r = new LaureateResponse();
        // Try to parse original id as Integer if possible
        try {
            if (entity.getId() != null) {
                r.setId(Integer.valueOf(entity.getId()));
            }
        } catch (NumberFormatException nfe) {
            // ignore, leave id null
        }
        r.setFirstname(entity.getFirstname());
        r.setSurname(entity.getSurname());
        r.setGender(entity.getGender());
        r.setBorn(entity.getBorn());
        r.setDied(entity.getDied());
        r.setBorncountry(entity.getBornCountry());
        r.setBorncountrycode(entity.getBornCountryCode());
        r.setBorncity(entity.getBornCity());
        r.setYear(entity.getYear());
        r.setCategory(entity.getCategory());
        r.setMotivation(entity.getMotivation());
        r.setAffiliation_name(entity.getAffiliationName());
        r.setAffiliation_city(entity.getAffiliationCity());
        r.setAffiliation_country(entity.getAffiliationCountry());
        r.setAge_at_award(entity.getAgeAtAward());
        r.setValidated(entity.getValidated());
        r.setNormalized_country_code(entity.getNormalizedCountryCode());
        return r;
    }

    @Data
    @Schema(name = "LaureateRequest", description = "Request payload to create a Laureate (fields follow functional requirement naming)")
    public static class LaureateRequest {
        @Schema(description = "Original laureate id from source", example = "123")
        private Integer id;
        @Schema(description = "First name", example = "Albert")
        private String firstname;
        @Schema(description = "Surname", example = "Einstein")
        private String surname;
        @Schema(description = "Gender", example = "male")
        private String gender;
        @Schema(description = "Born date (yyyy-MM-dd)", example = "1879-03-14")
        private String born;
        @Schema(description = "Died date (yyyy-MM-dd) or null", example = "1955-04-18")
        private String died;
        @Schema(description = "Born country", example = "Germany")
        private String borncountry;
        @Schema(description = "Born country code", example = "DE")
        private String borncountrycode;
        @Schema(description = "Born city", example = "Ulm")
        private String borncity;
        @Schema(description = "Award year", example = "1921")
        private String year;
        @Schema(description = "Category", example = "Physics")
        private String category;
        @Schema(description = "Motivation", example = "for his services to Theoretical Physics")
        private String motivation;
        @Schema(description = "Affiliation name", example = "University of Zurich")
        private String affiliation_name;
        @Schema(description = "Affiliation city", example = "Zurich")
        private String affiliation_city;
        @Schema(description = "Affiliation country", example = "Switzerland")
        private String affiliation_country;
        @Schema(description = "Age at award", example = "42")
        private Integer age_at_award;
        @Schema(description = "Validation state (VALIDATED or INVALID)", example = "VALIDATED")
        private String validated;
        @Schema(description = "Normalized country code", example = "DE")
        private String normalized_country_code;
    }

    @Data
    @Schema(name = "LaureateResponse", description = "Response payload for Laureate (fields follow functional requirement naming)")
    public static class LaureateResponse {
        @Schema(description = "Original laureate id from source", example = "123")
        private Integer id;
        @Schema(description = "First name", example = "Albert")
        private String firstname;
        @Schema(description = "Surname", example = "Einstein")
        private String surname;
        @Schema(description = "Gender", example = "male")
        private String gender;
        @Schema(description = "Born date (yyyy-MM-dd)", example = "1879-03-14")
        private String born;
        @Schema(description = "Died date (yyyy-MM-dd) or null", example = "1955-04-18")
        private String died;
        @Schema(description = "Born country", example = "Germany")
        private String borncountry;
        @Schema(description = "Born country code", example = "DE")
        private String borncountrycode;
        @Schema(description = "Born city", example = "Ulm")
        private String borncity;
        @Schema(description = "Award year", example = "1921")
        private String year;
        @Schema(description = "Category", example = "Physics")
        private String category;
        @Schema(description = "Motivation", example = "for his services to Theoretical Physics")
        private String motivation;
        @Schema(description = "Affiliation name", example = "University of Zurich")
        private String affiliation_name;
        @Schema(description = "Affiliation city", example = "Zurich")
        private String affiliation_city;
        @Schema(description = "Affiliation country", example = "Switzerland")
        private String affiliation_country;
        @Schema(description = "Age at award", example = "42")
        private Integer age_at_award;
        @Schema(description = "Validation state (VALIDATED or INVALID)", example = "VALIDATED")
        private String validated;
        @Schema(description = "Normalized country code", example = "DE")
        private String normalized_country_code;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the generated technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        public TechnicalIdResponse() {}

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}