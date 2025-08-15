package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/hn-items")
@Tag(name = "HNItem Controller", description = "Proxy controller for HNItem entity")
public class HNItemController {

    private static final Logger logger = LoggerFactory.getLogger(HNItemController.class);

    private final EntityService entityService;

    public HNItemController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create HNItem", description = "Create an HNItem with rawJson and enqueue processing")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = HNItemCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<HNItemCreateResponse> createHNItem(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "HNItem create request") @RequestBody HNItemCreateRequest request) {
        try {
            if (request == null || request.getRawJson() == null || request.getRawJson().isBlank()) {
                throw new IllegalArgumentException("rawJson is required");
            }

            HNItem hnItem = new HNItem();
            hnItem.setRawJson(request.getRawJson());
            // set initial state for workflow
            hnItem.setStatus("RECEIVED");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    HNItem.ENTITY_NAME,
                    String.valueOf(HNItem.ENTITY_VERSION),
                    hnItem
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new HNItemCreateResponse(technicalId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create HNItem", iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            }
            logger.error("ExecutionException while creating HNItem", ee);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while creating HNItem", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get HNItem by technicalId", description = "Retrieve an HNItem by its technical id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<ObjectNode> getHNItem(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    HNItem.ENTITY_NAME,
                    String.valueOf(HNItem.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get HNItem", iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            }
            logger.error("ExecutionException while retrieving HNItem", ee);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving HNItem", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Query HNItems", description = "List HNItems or query by Hacker News id (itemId)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<ArrayNode> listHNItems(@Parameter(name = "itemId", description = "Hacker News id to filter by") @RequestParam(required = false) Long itemId) {
        try {
            CompletableFuture<ArrayNode> itemsFuture;
            if (itemId != null) {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.id", "EQUALS", String.valueOf(itemId))
                );
                itemsFuture = entityService.getItemsByCondition(
                        HNItem.ENTITY_NAME,
                        String.valueOf(HNItem.ENTITY_VERSION),
                        condition,
                        true
                );
            } else {
                itemsFuture = entityService.getItems(
                        HNItem.ENTITY_NAME,
                        String.valueOf(HNItem.ENTITY_VERSION)
                );
            }
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to list HNItems", iae);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            }
            logger.error("ExecutionException while listing HNItems", ee);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error while listing HNItems", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Data
    public static class HNItemCreateRequest {
        @Schema(description = "Original JSON payload as received", required = true)
        private String rawJson;
    }

    @Data
    public static class HNItemCreateResponse {
        @Schema(description = "Technical id of the created HNItem")
        private String technicalId;

        public HNItemCreateResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}
