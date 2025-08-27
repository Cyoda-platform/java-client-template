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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Dull controller acting as a proxy to EntityService for Laureate entity only.
 * No business logic here — workflows implement business behavior.
 */
@RestController
@RequestMapping("/api/laureates/v1")
@Tag(name = "Laureate API", description = "Proxy endpoints for Laureate entity (version 1)")
public class LaureateController {

    private static final Logger logger = LoggerFactory.getLogger(LaureateController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LaureateController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get Laureate by technicalId", description = "Retrieve a Laureate entity by its technical UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = LaureateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getLaureateByTechnicalId(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId must be provided");
            }

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();

            if (dataPayload == null || dataPayload.getData() == null || dataPayload.getData().isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Laureate not found");
            }

            ObjectNode node = (ObjectNode) dataPayload.getData();
            LaureateResponse response = objectMapper.treeToValue(node, LaureateResponse.class);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("Bad request in getLaureateByTechnicalId: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getLaureateByTechnicalId", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching laureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Request interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in getLaureateByTechnicalId", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Laureates", description = "Retrieve all Laureate entities (no pagination). This is a proxy to EntityService.getItems")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = LaureateResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
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
                    if (data != null && !data.isNull()) {
                        LaureateResponse r = objectMapper.treeToValue(data, LaureateResponse.class);
                        responses.add(r);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request in listLaureates: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in listLaureates", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing laureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Request interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in listLaureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTOs for request/response payloads (as per functional requirements)
    @Data
    @Schema(name = "LaureateResponse", description = "Laureate response payload")
    public static class LaureateResponse {
        @Schema(description = "Source id from API", example = "853")
        private Integer id;

        @Schema(description = "First name", example = "Akira")
        private String firstname;

        @Schema(description = "Surname", example = "Suzuki")
        private String surname;

        @Schema(description = "Year of award", example = "2010")
        private String year;

        @Schema(description = "Category of award", example = "Chemistry")
        private String category;

        @Schema(description = "Validation status", example = "VALID")
        private String validationStatus;

        @Schema(description = "Age at award", example = "80")
        private Integer ageAtAward;

        // Additional fields present in the entity can be added if needed, but kept minimal per functional requirement example
    }
}