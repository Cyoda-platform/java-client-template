package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.java_template.common.service.EntityService;
import com.java_template.application.entity.activity.version_1.Activity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

import org.slf4j.Logger;

@RestController
@RequestMapping("/activities")
@Tag(name = "Activity")
public class ActivityController {
    private final EntityService entityService;
    private static final Logger logger = LoggerFactory.getLogger(ActivityController.class);

    public ActivityController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get Activity", description = "Retrieve Activity by technicalId. Activities are produced by ingestion and cannot be created via this API.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getActivity(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Activity.ENTITY_NAME,
                    String.valueOf(Activity.ENTITY_VERSION),
                    id
            );

            ObjectNode item = itemFuture.get();
            if (item == null) {
                throw new NoSuchElementException("Activity not found: " + technicalId);
            }
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution exception getting activity", ee);
                return ResponseEntity.status(500).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error getting activity", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
}
