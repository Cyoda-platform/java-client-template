package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
import com.java_template.common.service.EntityService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/hackernewsitem")
@Tag(name = "HackerNewsItem Controller", description = "Controller for HackerNewsItem entity")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create HackerNewsItem", description = "Creates a new HackerNewsItem entity with initial state = START, triggering processing workflow")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createHackerNewsItem(@RequestBody HackerNewsItemRequest request) {
        try {
            HackerNewsItem entity = new HackerNewsItem();
            entity.setId(request.getId());
            entity.setType(request.getType());
            entity.setJson(request.getJson());
            entity.setState("START");
            entity.setInvalidReason(null);
            entity.setCreationTimestamp(Instant.now());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    HackerNewsItem.ENTITY_NAME,
                    String.valueOf(HackerNewsItem.ENTITY_VERSION),
                    entity
            );
            UUID technicalId = idFuture.get();
            TechnicalIdResponse response = new TechnicalIdResponse(technicalId.toString());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createHackerNewsItem", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createHackerNewsItem", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in createHackerNewsItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get HackerNewsItem by technicalId", description = "Retrieves the stored HackerNewsItem by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = HackerNewsItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getHackerNewsItem(
            @Parameter(name = "technicalId", description = "Technical ID of the HackerNewsItem")
            @PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    HackerNewsItem.ENTITY_NAME,
                    String.valueOf(HackerNewsItem.ENTITY_VERSION),
                    uuid
            );
            ObjectNode itemNode = itemFuture.get();
            if (itemNode == null || itemNode.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            HackerNewsItemResponse response = new HackerNewsItemResponse();
            if (itemNode.hasNonNull("json")) {
                response.setJson(itemNode.get("json").asText());
            }
            if (itemNode.hasNonNull("creationTimestamp")) {
                response.setCreationTimestamp(Instant.parse(itemNode.get("creationTimestamp").asText()));
            }
            if (itemNode.hasNonNull("state")) {
                response.setState(itemNode.get("state").asText());
            }
            if (itemNode.has("invalidReason") && !itemNode.get("invalidReason").isNull()) {
                response.setInvalidReason(itemNode.get("invalidReason").asText());
            } else {
                response.setInvalidReason(null);
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getHackerNewsItem", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getHackerNewsItem", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception in getHackerNewsItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    @Schema(name = "HackerNewsItemRequest", description = "Request DTO for creating HackerNewsItem")
    public static class HackerNewsItemRequest {
        @Schema(description = "Hacker News item identifier", required = true, example = "12345")
        private Long id;

        @Schema(description = "Hacker News item type", required = true, example = "story")
        private String type;

        @Schema(description = "Full JSON payload of the Hacker News item", required = false, example = "{\"by\":\"author\",\"score\":10}")
        private String json;
    }

    @Data
    @Schema(name = "HackerNewsItemResponse", description = "Response DTO for retrieving HackerNewsItem")
    public static class HackerNewsItemResponse {
        @Schema(description = "Exact JSON string stored", example = "{\"by\":\"author\",\"score\":10}")
        private String json;

        @Schema(description = "Stored creation timestamp", example = "2024-06-01T12:34:56Z")
        private Instant creationTimestamp;

        @Schema(description = "State of the HackerNewsItem", allowableValues = {"START", "INVALID", "VALID"})
        private String state;

        @Schema(description = "Reason for INVALID state, or null if not invalid", nullable = true)
        private String invalidReason;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response DTO containing the technical ID")
    public static class TechnicalIdResponse {
        @Schema(description = "Unique system-generated technical ID", example = "abc123xyz")
        private final String technicalId;
    }
}