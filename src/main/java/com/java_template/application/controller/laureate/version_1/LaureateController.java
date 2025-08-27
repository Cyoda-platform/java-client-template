package com.java_template.application.controller.laureate.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.service.EntityService;
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
@Tag(name = "Laureate", description = "Operations for Laureate entity (read-only proxy)")
public class LaureateController {

    private static final Logger logger = LoggerFactory.getLogger(LaureateController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LaureateController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get Laureate by technicalId", description = "Retrieve a Laureate by its technical UUID identifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = LaureateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getLaureateByTechnicalId(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    uuid
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.notFound().build();
            }
            LaureateResponse resp = mapObjectNodeToDto(node);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request parameter: {}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving laureate", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving laureate", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Laureates", description = "Retrieve all Laureate entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping
    public ResponseEntity<?> getAllLaureates() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            List<LaureateResponse> result = new ArrayList<>();
            if (array != null) {
                for (int i = 0; i < array.size(); i++) {
                    if (array.get(i).isObject()) {
                        ObjectNode node = (ObjectNode) array.get(i);
                        result.add(mapObjectNodeToDto(node));
                    }
                }
            }
            return ResponseEntity.ok(result);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException while retrieving laureates", ee);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving laureates", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    private LaureateResponse mapObjectNodeToDto(ObjectNode node) {
        LaureateResponse resp = new LaureateResponse();
        // technicalId may be present in the stored representation
        if (node.has("technicalId") && !node.get("technicalId").isNull()) {
            resp.setTechnicalId(node.get("technicalId").asText());
        } else if (node.has("technical_id") && !node.get("technical_id").isNull()) {
            resp.setTechnicalId(node.get("technical_id").asText());
        }
        try {
            Laureate laureate = objectMapper.treeToValue(node, Laureate.class);
            if (laureate != null) {
                resp.setId(laureate.getId());
                resp.setFirstname(laureate.getFirstname());
                resp.setSurname(laureate.getSurname());
                resp.setCategory(laureate.getCategory());
                resp.setYear(laureate.getYear());
                resp.setMotivation(laureate.getMotivation());
                resp.setAffiliationName(laureate.getAffiliationName());
                resp.setAffiliationCity(laureate.getAffiliationCity());
                resp.setAffiliationCountry(laureate.getAffiliationCountry());
                resp.setBorn(laureate.getBorn());
                resp.setBornCity(laureate.getBornCity());
                resp.setBornCountry(laureate.getBornCountry());
                resp.setBornCountryCode(laureate.getBornCountryCode());
                resp.setDerivedAgeAtAward(laureate.getDerivedAgeAtAward());
                resp.setDied(laureate.getDied());
                resp.setGender(laureate.getGender());
                resp.setNormalizedCountryCode(laureate.getNormalizedCountryCode());
                resp.setIngestJobId(laureate.getIngestJobId());
            }
        } catch (Exception e) {
            // If mapping fails, log and still attempt to extract fields individually
            logger.debug("Mapping ObjectNode to Laureate failed, attempting manual extraction: {}", e.getMessage());
            if (node.has("id") && !node.get("id").isNull()) {
                resp.setId(node.get("id").isInt() ? node.get("id").asInt() : null);
            }
            if (node.has("firstname") && !node.get("firstname").isNull()) {
                resp.setFirstname(node.get("firstname").asText());
            }
            if (node.has("surname") && !node.get("surname").isNull()) {
                resp.setSurname(node.get("surname").asText());
            }
            if (node.has("category") && !node.get("category").isNull()) {
                resp.setCategory(node.get("category").asText());
            }
            if (node.has("year") && !node.get("year").isNull()) {
                resp.setYear(node.get("year").asText());
            }
            if (node.has("derivedAgeAtAward") && !node.get("derivedAgeAtAward").isNull()) {
                resp.setDerivedAgeAtAward(node.get("derivedAgeAtAward").isInt() ? node.get("derivedAgeAtAward").asInt() : null);
            }
            if (node.has("normalizedCountryCode") && !node.get("normalizedCountryCode").isNull()) {
                resp.setNormalizedCountryCode(node.get("normalizedCountryCode").asText());
            }
            if (node.has("ingestJobId") && !node.get("ingestJobId").isNull()) {
                resp.setIngestJobId(node.get("ingestJobId").asText());
            }
        }
        return resp;
    }

    @Data
    @Schema(name = "LaureateResponse", description = "Laureate representation returned by the API")
    public static class LaureateResponse {
        @Schema(description = "Technical UUID identifier of the entity")
        private String technicalId;

        @Schema(description = "Business id of the laureate")
        private Integer id;

        @Schema(description = "Given name")
        private String firstname;

        @Schema(description = "Family name")
        private String surname;

        @Schema(description = "Award category")
        private String category;

        @Schema(description = "Award year")
        private String year;

        @Schema(description = "Award motivation")
        private String motivation;

        @Schema(description = "Affiliation name")
        private String affiliationName;

        @Schema(description = "Affiliation city")
        private String affiliationCity;

        @Schema(description = "Affiliation country")
        private String affiliationCountry;

        @Schema(description = "Birth date (ISO)")
        private String born;

        @Schema(description = "Birth city")
        private String bornCity;

        @Schema(description = "Birth country")
        private String bornCountry;

        @Schema(description = "Birth country code")
        private String bornCountryCode;

        @Schema(description = "Derived age at award")
        private Integer derivedAgeAtAward;

        @Schema(description = "Death date (ISO) or null")
        private String died;

        @Schema(description = "Gender")
        private String gender;

        @Schema(description = "Normalized country code")
        private String normalizedCountryCode;

        @Schema(description = "Ingest job business id that created/updated this record")
        private String ingestJobId;
    }
}