package com.java_template.application.controller.laureate.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.laureate.version_1.Laureate;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Dull proxy controller for Laureate entity. All business logic lives in workflows/processors.
 */
@RestController
@RequestMapping("/api/v1/laureates")
@Tag(name = "Laureate", description = "Read-only API for Laureate entities produced by workflows")
public class LaureateController {

    private static final Logger logger = LoggerFactory.getLogger(LaureateController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LaureateController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "List Laureates", description = "Retrieve all Laureate entities produced by ingestion workflows")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getLaureates() {
        try {
            ArrayNode items = entityService.getItems(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION)
            ).get();

            List<LaureateResponse> results = new ArrayList<>();
            for (JsonNode node : items) {
                LaureateResponse dto = objectMapper.treeToValue(node, LaureateResponse.class);
                results.add(dto);
            }
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid request to get laureates", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException while getting laureates", ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting laureates", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while getting laureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Laureate by technicalId", description = "Retrieve a single Laureate by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = LaureateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getLaureateById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            ObjectNode item = entityService.getItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            ).get();

            LaureateResponse dto = objectMapper.treeToValue(item, LaureateResponse.class);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid technicalId provided: {}", technicalId, iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException while getting laureate {}", technicalId, ee);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting laureate {}", technicalId, ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while getting laureate {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    @Schema(name = "LaureateResponse", description = "Laureate response payload")
    public static class LaureateResponse {
        @Schema(description = "Technical ID of the entity")
        private String technicalId;

        @Schema(description = "Domain laureate id from source", example = "853")
        private Integer laureateId;

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

        @Schema(description = "Country name of birth")
        private String bornCountry;

        @Schema(description = "Country code of birth")
        private String bornCountryCode;

        @Schema(description = "Birth city")
        private String bornCity;

        @Schema(description = "Award year")
        private String year;

        @Schema(description = "Award category")
        private String category;

        @Schema(description = "Award motivation")
        private String motivation;

        @Schema(description = "Affiliation name")
        private String affiliationName;

        @Schema(description = "Affiliation city")
        private String affiliationCity;

        @Schema(description = "Affiliation country")
        private String affiliationCountry;

        @Schema(description = "Computed age")
        private Integer computedAge;

        @Schema(description = "Validation status (VALID or INVALID)")
        private String validationStatus;

        @Schema(description = "Enrichment status (ENRICHED or PENDING)")
        private String enrichmentStatus;

        @Schema(description = "Source job technicalId")
        private String sourceJobId;

        @Schema(description = "Merged from technicalIds")
        private List<String> mergedFrom;

        @Schema(description = "Created at timestamp")
        private String createdAt;
    }
}