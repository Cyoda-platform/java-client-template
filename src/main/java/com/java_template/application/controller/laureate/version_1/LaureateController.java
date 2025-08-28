package com.java_template.application.controller.laureate.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.service.EntityService;
import lombok.Data;
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

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/laureates/v1")
@Tag(name = "Laureate API", description = "Read-only API for Laureate entities (version 1)")
public class LaureateController {

    private static final Logger logger = LoggerFactory.getLogger(LaureateController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LaureateController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get a Laureate by technicalId", description = "Retrieve a Laureate entity by its technical identifier.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = LaureateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getLaureateById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId must be provided");
            }
            UUID uuid = UUID.fromString(technicalId);
            DataPayload dataPayload = entityService.getItem(uuid).get();
            ObjectNode node = dataPayload != null ? (ObjectNode) dataPayload.getData() : null;
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Laureate not found");
            }
            LaureateResponse response = objectMapper.treeToValue(node, LaureateResponse.class);
            // preserve the request technicalId in the response as examples indicate
            response.setTechnicalId(technicalId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for getLaureateById: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException in getLaureateById: {}", cause != null ? cause.getMessage() : e.getMessage(), e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected exception in getLaureateById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Laureates", description = "Retrieve a list of Laureate entities. Optional pagination via pageSize and pageNumber.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listLaureates(
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "pageNumber", required = false) Integer pageNumber) {
        try {
            // basic validation for pagination params
            if (pageSize != null && pageSize <= 0) {
                throw new IllegalArgumentException("pageSize must be positive");
            }
            if (pageNumber != null && pageNumber < 0) {
                throw new IllegalArgumentException("pageNumber must be non-negative");
            }

            List<DataPayload> dataPayloads = entityService.getItems(Laureate.ENTITY_NAME, Laureate.ENTITY_VERSION, pageSize, pageNumber, null).get();
            List<LaureateResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    if (data != null && !data.isNull()) {
                        LaureateResponse resp = objectMapper.treeToValue(data, LaureateResponse.class);
                        // technicalId may not be included in payload data; leave null if not available
                        responses.add(resp);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for listLaureates: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException in listLaureates: {}", cause != null ? cause.getMessage() : e.getMessage(), e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected exception in listLaureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    @Schema(name = "LaureateResponse", description = "Laureate response payload")
    public static class LaureateResponse {
        @Schema(description = "Technical identifier of the stored entity", example = "laureate-technical-123")
        private String technicalId;

        @Schema(description = "Source laureate id", example = "853")
        private Integer id;

        @Schema(description = "Given name", example = "Akira")
        private String firstname;

        @Schema(description = "Family name", example = "Suzuki")
        private String surname;

        @Schema(description = "Gender", example = "male")
        private String gender;

        @Schema(description = "Birth date (ISO)", example = "1930-09-12")
        private String born;

        @Schema(description = "Death date (ISO) or null", example = "null")
        private String died;

        @Schema(description = "Country of birth", example = "Japan")
        private String borncountry;

        @Schema(description = "Country code of birth", example = "JP")
        private String borncountrycode;

        @Schema(description = "City of birth", example = "Mukawa")
        private String borncity;

        @Schema(description = "Award year", example = "2010")
        private String year;

        @Schema(description = "Award category", example = "Chemistry")
        private String category;

        @Schema(description = "Motivation text", example = "for palladium-catalyzed cross couplings in organic synthesis")
        private String motivation;

        @Schema(description = "Affiliation name", example = "Hokkaido University")
        private String affiliationName;

        @Schema(description = "Affiliation city", example = "Sapporo")
        private String affiliationCity;

        @Schema(description = "Affiliation country", example = "Japan")
        private String affiliationCountry;

        @Schema(description = "Computed age at award or current", example = "80")
        private Integer computedAge;

        @Schema(description = "Ingest job technical id that produced this record", example = "job-2025-08-28-01")
        private String ingestJobId;
    }
}