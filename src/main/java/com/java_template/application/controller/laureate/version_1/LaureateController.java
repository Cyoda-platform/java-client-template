package com.java_template.application.controller.laureate.version_1;

import static com.java_template.common.config.Config.*;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
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
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/laureates")
@Tag(name = "Laureate Controller", description = "API for Laureate entity (version 1)")
public class LaureateController {

    private static final Logger logger = LoggerFactory.getLogger(LaureateController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LaureateController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Laureate", description = "Persist a Laureate entity and trigger the Laureate workflow. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> createLaureate(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Laureate payload", required = true, content = @Content(schema = @Schema(implementation = CreateLaureateRequest.class)))
            @RequestBody CreateLaureateRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Laureate laureate = mapRequestToEntity(request);

            UUID id = entityService.addItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    laureate
            ).get();

            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createLaureate: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createLaureate", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in createLaureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Laureates", description = "Persist multiple Laureate entities and trigger Laureate workflows. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TechnicalIdResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(path = "/batch", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> createLaureatesBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Array of laureate payloads", required = true, content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateLaureateRequest.class))))
            @RequestBody List<CreateLaureateRequest> requests
    ) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request body must contain at least one Laureate");
            }

            List<Laureate> entities = new ArrayList<>();
            for (CreateLaureateRequest req : requests) {
                entities.add(mapRequestToEntity(req));
            }

            List<UUID> ids = entityService.addItems(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    entities
            ).get();

            List<TechnicalIdResponse> resp = new ArrayList<>();
            for (UUID u : ids) {
                resp.add(new TechnicalIdResponse(u.toString()));
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createLaureatesBatch: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createLaureatesBatch", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in createLaureatesBatch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Laureate by technicalId", description = "Retrieve a Laureate entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LaureateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = "application/json")
    public ResponseEntity<?> getLaureateById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID uuid = UUID.fromString(technicalId);

            ObjectNode node = entityService.getItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    uuid
            ).get();

            Laureate entity = objectMapper.treeToValue(node, Laureate.class);
            LaureateResponse response = mapEntityToResponse(entity);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for getLaureateById: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getLaureateById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in getLaureateById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Laureates", description = "Retrieve all Laureate entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = "application/json")
    public ResponseEntity<?> getAllLaureates() {
        try {
            ArrayNode node = entityService.getItems(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION)
            ).get();

            List<LaureateResponse> responses = new ArrayList<>();
            for (int i = 0; i < node.size(); i++) {
                ObjectNode on = (ObjectNode) node.get(i);
                Laureate entity = objectMapper.treeToValue(on, Laureate.class);
                responses.add(mapEntityToResponse(entity));
            }

            return ResponseEntity.ok(responses);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getAllLaureates", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in getAllLaureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Laureates by simple condition", description = "Search Laureates by a single field condition. Example: ?field=borncountry&operator=EQUALS&value=Japan")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/search", produces = "application/json")
    public ResponseEntity<?> searchLaureates(
            @RequestParam(name = "field") String field,
            @RequestParam(name = "value") String value,
            @RequestParam(name = "operator", required = false, defaultValue = "EQUALS") String operator
    ) {
        try {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("field parameter is required");
            }
            if (value == null) {
                throw new IllegalArgumentException("value parameter is required");
            }

            // Build simple condition using provided field and operator.
            // Field will be used as JSON path under the entity value (e.g., $.borncountry)
            String jsonPath = "$." + field;
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of(jsonPath, operator, value)
            );

            ArrayNode node = entityService.getItemsByCondition(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    condition,
                    true
            ).get();

            List<LaureateResponse> responses = new ArrayList<>();
            for (int i = 0; i < node.size(); i++) {
                ObjectNode on = (ObjectNode) node.get(i);
                Laureate entity = objectMapper.treeToValue(on, Laureate.class);
                responses.add(mapEntityToResponse(entity));
            }

            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for searchLaureates: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in searchLaureates", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in searchLaureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- Mapping helpers ---

    private Laureate mapRequestToEntity(CreateLaureateRequest req) {
        Laureate e = new Laureate();
        e.setId(req.getId());
        e.setFirstname(req.getFirstname());
        e.setSurname(req.getSurname());
        e.setGender(req.getGender());
        e.setBorn(req.getBorn());
        e.setDied(req.getDied());
        e.setBornCountry(req.getBorncountry());
        e.setBornCountryCode(req.getBorncountrycode());
        e.setBornCity(req.getBorncity());
        e.setYear(req.getYear());
        e.setCategory(req.getCategory());
        e.setMotivation(req.getMotivation());
        e.setAffiliationName(req.getAffiliation_name());
        e.setAffiliationCity(req.getAffiliation_city());
        e.setAffiliationCountry(req.getAffiliation_country());
        e.setDerivedAgeAtAward(req.getDerived_ageAtAward());
        e.setRecordStatus(req.getRecordStatus());
        e.setPersistedAt(req.getPersistedAt());
        return e;
    }

    private LaureateResponse mapEntityToResponse(Laureate e) {
        LaureateResponse r = new LaureateResponse();
        r.setId(e.getId());
        r.setFirstname(e.getFirstname());
        r.setSurname(e.getSurname());
        r.setGender(e.getGender());
        r.setBorn(e.getBorn());
        r.setDied(e.getDied());
        r.setBorncountry(e.getBornCountry());
        r.setBorncountrycode(e.getBornCountryCode());
        r.setBorncity(e.getBornCity());
        r.setYear(e.getYear());
        r.setCategory(e.getCategory());
        r.setMotivation(e.getMotivation());
        r.setAffiliation_name(e.getAffiliationName());
        r.setAffiliation_city(e.getAffiliationCity());
        r.setAffiliation_country(e.getAffiliationCountry());
        r.setDerived_ageAtAward(e.getDerivedAgeAtAward());
        r.setRecordStatus(e.getRecordStatus());
        r.setPersistedAt(e.getPersistedAt());
        return r;
    }

    // --- DTOs ---

    @Data
    @Schema(name = "CreateLaureateRequest", description = "Request payload to create a Laureate")
    public static class CreateLaureateRequest {
        @Schema(description = "Source id from dataset")
        private Integer id;

        @Schema(description = "Given name")
        private String firstname;

        @Schema(description = "Family name")
        private String surname;

        @Schema(description = "Gender")
        private String gender;

        @Schema(description = "Birth date")
        private String born;

        @Schema(description = "Death date")
        private String died;

        @JsonProperty("borncountry")
        @Schema(description = "Birth country")
        private String borncountry;

        @JsonProperty("borncountrycode")
        @Schema(description = "Birth country code")
        private String borncountrycode;

        @JsonProperty("borncity")
        @Schema(description = "Birth city")
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

        @JsonProperty("derived_ageAtAward")
        @Schema(description = "Derived age at award")
        private Integer derived_ageAtAward;

        @Schema(description = "Record status")
        private String recordStatus;

        @Schema(description = "Persisted at timestamp")
        private String persistedAt;
    }

    @Data
    @Schema(name = "LaureateResponse", description = "Laureate representation returned by API")
    public static class LaureateResponse {
        @Schema(description = "Source id from dataset")
        private Integer id;

        @Schema(description = "Given name")
        private String firstname;

        @Schema(description = "Family name")
        private String surname;

        @Schema(description = "Gender")
        private String gender;

        @Schema(description = "Birth date")
        private String born;

        @Schema(description = "Death date")
        private String died;

        @JsonProperty("borncountry")
        @Schema(description = "Birth country")
        private String borncountry;

        @JsonProperty("borncountrycode")
        @Schema(description = "Birth country code")
        private String borncountrycode;

        @JsonProperty("borncity")
        @Schema(description = "Birth city")
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

        @JsonProperty("derived_ageAtAward")
        @Schema(description = "Derived age at award")
        private Integer derived_ageAtAward;

        @Schema(description = "Record status")
        private String recordStatus;

        @Schema(description = "Persisted timestamp")
        private String persistedAt;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing only the technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id")
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}