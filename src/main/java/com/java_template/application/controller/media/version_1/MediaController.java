package com.java_template.application.controller.media.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.media.version_1.Media;
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
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.time.Instant;

/**
 * Dull controller for Media entity. Proxies requests to EntityService.
 */
@RestController
@RequestMapping("/media/v1")
@Tag(name = "Media", description = "Media entity API (v1)")
public class MediaController {

    private static final Logger logger = LoggerFactory.getLogger(MediaController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public MediaController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Media", description = "Persist a Media entity and start processing workflows. Returns the technicalId only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<CreateResponse> createMedia(
            @SwaggerRequestBody(required = true, content = @Content(schema = @Schema(implementation = CreateMediaRequest.class)))
            @RequestBody CreateMediaRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getOwnerId() == null || request.getOwnerId().isBlank()) {
                throw new IllegalArgumentException("owner_id is required");
            }
            if (request.getFilename() == null || request.getFilename().isBlank()) {
                throw new IllegalArgumentException("filename is required");
            }
            if (request.getMime() == null || request.getMime().isBlank()) {
                throw new IllegalArgumentException("mime is required");
            }

            // Minimal mapping to entity (no business logic)
            Media media = new Media();
            // generate technical identifiers required by entity validation
            media.setMedia_id(UUID.randomUUID().toString());
            media.setOwner_id(request.getOwnerId());
            media.setFilename(request.getFilename());
            media.setMime(request.getMime());
            media.setCreated_at(Instant.now().toString());
            // set a sensible default status; workflows will adjust it
            media.setStatus("uploaded");

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Media.ENTITY_NAME,
                    Media.ENTITY_VERSION,
                    media
            );
            java.util.UUID entityId = idFuture.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(entityId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Validation failed for createMedia: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in createMedia", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating media", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in createMedia", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get Media by technicalId", description = "Retrieve a persisted Media entity by its technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Media.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = "application/json")
    public ResponseEntity<Media> getMediaById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Media media = objectMapper.treeToValue(dataPayload.getData(), Media.class);
            return ResponseEntity.ok(media);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in getMediaById: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in getMediaById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving media", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in getMediaById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Data
    @Schema(description = "Create Media Request")
    public static class CreateMediaRequest {
        @Schema(name = "owner_id", description = "Owner (user) id", required = true, example = "user-123")
        private String ownerId;

        @Schema(name = "filename", description = "Original filename", required = true, example = "image.jpg")
        private String filename;

        @Schema(name = "mime", description = "MIME type", required = true, example = "image/jpeg")
        private String mime;
    }

    @Data
    @Schema(description = "Create Response with technicalId")
    public static class CreateResponse {
        @Schema(name = "technicalId", description = "Technical ID of created entity", example = "tech-media-001")
        private String technicalId;
    }
}