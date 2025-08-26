package com.java_template.application.controller.coverphoto.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.coverphoto.version_1.CoverPhoto;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;
import java.util.List;

@RestController
@RequestMapping("/api/v1/coverphotos")
@Tag(name = "CoverPhoto", description = "CoverPhoto read-only APIs")
public class CoverPhotoController {

    private static final Logger logger = LoggerFactory.getLogger(CoverPhotoController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CoverPhotoController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Get all cover photos", description = "Retrieve all CoverPhoto entities (gallery view). Returns list of summary objects.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CoverPhotoSummaryResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping
    public ResponseEntity<?> getAllCoverPhotos() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    CoverPhoto.ENTITY_NAME,
                    String.valueOf(CoverPhoto.ENTITY_VERSION)
            );
            ArrayNode arrayNode = itemsFuture.get();
            if (arrayNode == null) {
                return ResponseEntity.ok(List.of());
            }
            List<CoverPhotoSummaryResponse> list = objectMapper.convertValue(arrayNode, new TypeReference<List<CoverPhotoSummaryResponse>>() {});
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for getAllCoverPhotos", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception while getting all cover photos", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while getting all cover photos", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get cover photo by technicalId", description = "Retrieve details of a single CoverPhoto entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CoverPhotoDetailResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getCoverPhotoById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    CoverPhoto.ENTITY_NAME,
                    String.valueOf(CoverPhoto.ENTITY_VERSION),
                    id
            );
            ObjectNode objectNode = itemFuture.get();
            if (objectNode == null || objectNode.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("CoverPhoto not found");
            }
            CoverPhotoDetailResponse dto = objectMapper.treeToValue(objectNode, CoverPhotoDetailResponse.class);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getCoverPhotoById for id: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception while getting cover photo by id: {}", technicalId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while getting cover photo by id: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // DTOs for request/response payloads (static classes)

    @Data
    @Schema(name = "CoverPhotoSummaryResponse", description = "Summary view of CoverPhoto used in gallery lists")
    public static class CoverPhotoSummaryResponse {
        @Schema(description = "Source id from API", example = "10")
        private String id;

        @Schema(description = "Display title", example = "Cover A")
        private String title;

        @Schema(description = "Thumbnail URL", example = "https://...")
        private String thumbnailUrl;

        @Schema(description = "Published date (ISO)", example = "2025-08-01T00:00:00Z")
        private String publishedDate;

        @Schema(description = "Total view count", example = "123")
        private Integer viewCount;

        @Schema(description = "Embedded comments (summary may be empty)")
        private List<CommentResponse> comments;
    }

    @Data
    @Schema(name = "CoverPhotoDetailResponse", description = "Detailed view of a CoverPhoto")
    public static class CoverPhotoDetailResponse {
        @Schema(description = "Source id from API", example = "10")
        private String id;

        @Schema(description = "Display title", example = "Cover A")
        private String title;

        @Schema(description = "Photo description", example = "A beautiful landscape")
        private String description;

        @Schema(description = "Original image URL", example = "https://example.com/image.jpg")
        private String sourceUrl;

        @Schema(description = "Gallery thumbnail URL", example = "https://example.com/thumb.jpg")
        private String thumbnailUrl;

        @Schema(description = "Tags", example = "[\"landscape\",\"sunset\"]")
        private List<String> tags;

        @Schema(description = "Published date (ISO)", example = "2025-08-01T00:00:00Z")
        private String publishedDate;

        @Schema(description = "Ingestion status", example = "PUBLISHED")
        private String ingestionStatus;

        @Schema(description = "Total view count", example = "123")
        private Integer viewCount;

        @Schema(description = "Embedded comments")
        private List<CommentResponse> comments;

        @Schema(description = "Created at timestamp", example = "2025-07-31T23:59:59Z")
        private String createdAt;

        @Schema(description = "Updated at timestamp", example = "2025-08-01T00:05:00Z")
        private String updatedAt;
    }

    @Data
    @Schema(name = "CommentResponse", description = "Embedded comment object within a CoverPhoto")
    public static class CommentResponse {
        @Schema(description = "User business id", example = "u-1001")
        private String userId;

        @Schema(description = "Comment text", example = "Nice!")
        private String text;

        @Schema(description = "Created at timestamp", example = "2025-08-01T01:00:00Z")
        private String createdAt;

        @Schema(description = "Comment status", example = "visible")
        private String status;
    }
}