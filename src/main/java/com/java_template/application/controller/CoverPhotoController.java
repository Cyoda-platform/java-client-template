package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.coverphoto.version_1.CoverPhoto;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/coverphotos")
@Tag(name = "CoverPhoto API", description = "API for CoverPhoto entity (proxy to entity service)")
public class CoverPhotoController {

    private static final Logger logger = LoggerFactory.getLogger(CoverPhotoController.class);
    private final EntityService entityService;

    public CoverPhotoController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create CoverPhoto", description = "Create a new cover photo (usually created by ingestion jobs). Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createCoverPhoto(@RequestBody(description = "CoverPhoto request payload") @org.springframework.web.bind.annotation.RequestBody CoverPhotoRequest request) {
        try {
            if (request == null || request.coverId == null || request.coverId.isBlank()) {
                throw new IllegalArgumentException("coverId is required");
            }
            if (request.title == null || request.title.isBlank()) {
                throw new IllegalArgumentException("title is required");
            }
            if (request.imageUrl == null || request.imageUrl.isBlank()) {
                throw new IllegalArgumentException("imageUrl is required");
            }

            CoverPhoto cp = new CoverPhoto();
            cp.setCoverId(request.coverId);
            cp.setTitle(request.title);
            cp.setBookId(request.bookId);
            cp.setImageUrl(request.imageUrl);
            cp.setFetchedAt(request.fetchedAt);
            cp.setTags(request.tags);
            cp.setStatus(request.status != null ? request.status : "RECEIVED");
            cp.setMetadata(request.metadata);
            cp.setOriginPayload(request.originPayload);
            cp.setDuplicateOf(request.duplicateOf);
            cp.setIngestionJobId(request.ingestionJobId);
            cp.setErrorFlag(request.errorFlag);
            cp.setProcessingAttempts(request.processingAttempts);
            cp.setLastProcessedAt(request.lastProcessedAt);

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    CoverPhoto.ENTITY_NAME,
                    String.valueOf(CoverPhoto.ENTITY_VERSION),
                    cp
            );

            UUID id = idFuture.get();
            URI location = URI.create(String.format("/coverphotos/%s", id.toString()));
            return ResponseEntity.created(location).body(new IdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error creating cover photo", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating cover photo", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error creating cover photo", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get CoverPhoto by technicalId", description = "Retrieve a cover photo by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getCoverPhoto(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    CoverPhoto.ENTITY_NAME,
                    String.valueOf(CoverPhoto.ENTITY_VERSION),
                    id
            );

            ObjectNode obj = itemFuture.get();
            return ResponseEntity.ok(obj);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error retrieving cover photo", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving cover photo", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error retrieving cover photo", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List CoverPhotos", description = "List all cover photos")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class)))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listCoverPhotos() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    CoverPhoto.ENTITY_NAME,
                    String.valueOf(CoverPhoto.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error listing cover photos", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing cover photos", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error listing cover photos", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search CoverPhotos", description = "Search cover photos by a simple field condition (field, operator, value)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class)))
    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchCoverPhotos(
            @RequestParam(required = true) String field,
            @RequestParam(required = true) String operator,
            @RequestParam(required = true) String value
    ) {
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$." + field, operator, value)
            );
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    CoverPhoto.ENTITY_NAME,
                    String.valueOf(CoverPhoto.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error searching cover photos", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching cover photos", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error searching cover photos", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class CoverPhotoRequest {
        @Schema(description = "Origin cover id (source)")
        public String coverId;
        @Schema(description = "Title")
        public String title;
        @Schema(description = "Related book id")
        public String bookId;
        @Schema(description = "Image URL", required = true)
        public String imageUrl;
        @Schema(description = "Fetched at ISO-8601")
        public String fetchedAt;
        @Schema(description = "Tags list")
        public java.util.List<String> tags;
        @Schema(description = "Status")
        public String status;
        @Schema(description = "Metadata map")
        public java.util.Map<String, Object> metadata;
        @Schema(description = "Origin payload")
        public java.util.Map<String, Object> originPayload;
        @Schema(description = "Duplicate of technicalId")
        public String duplicateOf;
        @Schema(description = "Ingestion job technicalId")
        public String ingestionJobId;
        @Schema(description = "Error flag")
        public Boolean errorFlag;
        @Schema(description = "Processing attempts")
        public Integer processingAttempts;
        @Schema(description = "Last processed at")
        public String lastProcessedAt;
    }

    @Data
    static class IdResponse {
        @Schema(description = "Technical id assigned to the entity")
        public String technicalId;

        public IdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}
