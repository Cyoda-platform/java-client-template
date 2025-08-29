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

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

/**
 * Controller for Media entity. Now accepts batch create requests (array of media payloads).
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

    @Operation(summary = "Create Media (batch)", description = "Persist multiple Media entities and start processing workflows. Returns technicalIds.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = BatchCreateResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<BatchCreateResponse> createMedia(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = CreateMediaRequest.class))))
            @RequestBody List<CreateMediaRequest> requests
    ) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("request body must be a non-empty array");
            }

            List<Media> entities = new ArrayList<>();
            for (CreateMediaRequest request : requests) {
                if (request == null) continue;
                if (request.getOwnerId() == null || request.getOwnerId().isBlank()) {
                    throw new IllegalArgumentException("owner_id is required for each media item");
                }
                if (request.getFilename() == null || request.getFilename().isBlank()) {
                    throw new IllegalArgumentException("filename is required for each media item");
                }
                if (request.getMime() == null || request.getMime().isBlank()) {
                    throw new IllegalArgumentException("mime is required for each media item");
                }

                Media media = new Media();
                media.setMedia_id(UUID.randomUUID().toString());
                media.setOwner_id(request.getOwnerId());
                media.setFilename(request.getFilename());
                media.setMime(request.getMime());
                media.setCreated_at(Instant.now().toString());
                media.setStatus("uploaded");
                entities.add(media);
            }

            List<UUID> ids = entityService.addItems(
                    Media.ENTITY_NAME,
                    Media.ENTITY_VERSION,
                    entities
            ).get();

            List<String> technicalIds = new ArrayList<>();
            if (ids != null) {
                for (UUID u : ids) technicalIds.add(u != null ? u.toString() : null);
            }
            BatchCreateResponse resp = new BatchCreateResponse();
            resp.setTechnicalIds(technicalIds);
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
    @Schema(description = "Batch create response with technicalIds")
    public static class BatchCreateResponse {
        @Schema(name = "technicalIds", description = "Technical IDs of created entities")
        private List<String> technicalIds;
    }
}