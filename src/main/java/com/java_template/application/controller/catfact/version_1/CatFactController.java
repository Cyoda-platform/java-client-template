package com.java_template.application.controller.catfact.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "CatFact", description = "Operations for CatFact entity")
public class CatFactController {

    private static final Logger logger = LoggerFactory.getLogger(CatFactController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CatFactController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Get CatFact by technicalId", description = "Retrieve a CatFact entity by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CatFactResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/catfacts/{technicalId}")
    public ResponseEntity<CatFactResponse> getCatFactById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId must be provided");
            }

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();

            if (dataPayload == null) {
                return ResponseEntity.notFound().build();
            }

            JsonNode dataNode = dataPayload.getData();
            CatFactResponse response = objectMapper.treeToValue(dataNode, CatFactResponse.class);

            // extract technical id from meta if present
            if (dataPayload.getMeta() != null && dataPayload.getMeta().has("entityId")) {
                try {
                    response.setTechnicalId(dataPayload.getMeta().get("entityId").asText());
                } catch (Exception e) {
                    // ignore and keep response technicalId as-is (if any)
                }
            } else {
                // fallback: use path param
                response.setTechnicalId(technicalId);
            }

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getCatFactById: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while fetching CatFact {}", technicalId, ee);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching CatFact {}", technicalId, ie);
            return ResponseEntity.status(500).build();
        } catch (Exception ex) {
            logger.error("Unexpected error while fetching CatFact {}", technicalId, ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Data
    @Schema(name = "CatFactResponse", description = "CatFact response payload")
    public static class CatFactResponse {
        @Schema(description = "Technical id of the CatFact", example = "catfact-uuid-999")
        private String technicalId;

        @Schema(description = "The fact text", example = "Cats sleep 70% of their lives.")
        private String text;

        @Schema(description = "Source of the fact", example = "catfact.ninja")
        private String source;

        @Schema(description = "ISO-8601 timestamp when the fact was fetched", example = "2025-09-07T09:00:01Z")
        private String fetchedAt;

        @Schema(description = "Validation status", example = "VALID")
        private String validationStatus;

        @Schema(description = "How many times this fact has been sent", example = "1")
        private Integer sendCount;

        @Schema(description = "Engagement score", example = "12.5")
        private Double engagementScore;
    }
}