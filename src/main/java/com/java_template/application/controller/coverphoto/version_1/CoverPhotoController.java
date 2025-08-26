package com.java_template.application.controller.coverphoto.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.coverphoto.version_1.CoverPhoto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Controller for CoverPhoto entity - acts as a proxy to EntityService.
 *
 * - No business logic implemented here.
 * - All operations delegate to EntityService.
 */
@RestController
@RequestMapping("/coverphoto/v1")
@Tag(name = "CoverPhoto", description = "Operations for CoverPhoto entity")
public class CoverPhotoController {

    private static final Logger logger = LoggerFactory.getLogger(CoverPhotoController.class);

    private final EntityService entityService;

    public CoverPhotoController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Add CoverPhoto", description = "Adds a new CoverPhoto entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Created",
                    content = @Content(schema = @Schema(implementation = AddCoverPhotoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> addCoverPhoto(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "CoverPhoto to add")
            @Valid @RequestBody AddCoverPhotoRequest request) {
        try {
            UUID id = entityService.addItem(
                    CoverPhoto.ENTITY_NAME,
                    String.valueOf(CoverPhoto.ENTITY_VERSION),
                    request.getData()
            ).get();
            AddCoverPhotoResponse resp = new AddCoverPhotoResponse();
            resp.setTechnicalId(id);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument while adding CoverPhoto", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while adding CoverPhoto", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding CoverPhoto", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while adding CoverPhoto", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Add multiple CoverPhotos", description = "Adds multiple CoverPhoto entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Created",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AddMultipleCoverPhotoResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> addCoverPhotos(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of CoverPhotos to add")
            @Valid @RequestBody AddMultipleCoverPhotoRequest request) {
        try {
            List<UUID> ids = entityService.addItems(
                    CoverPhoto.ENTITY_NAME,
                    String.valueOf(CoverPhoto.ENTITY_VERSION),
                    request.getEntities()
            ).get();
            AddMultipleCoverPhotoResponse resp = new AddMultipleCoverPhotoResponse();
            resp.setTechnicalIds(ids);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument while adding multiple CoverPhotos", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while adding multiple CoverPhotos", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding multiple CoverPhotos", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while adding multiple CoverPhotos", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get CoverPhoto by technicalId", description = "Retrieves a CoverPhoto by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getCoverPhoto(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            ObjectNode node = entityService.getItem(
                    CoverPhoto.ENTITY_NAME,
                    String.valueOf(CoverPhoto.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            ).get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument while fetching CoverPhoto", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching CoverPhoto", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching CoverPhoto", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while fetching CoverPhoto", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all CoverPhotos", description = "Retrieves all CoverPhoto entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ArrayNode.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getCoverPhotos() {
        try {
            ArrayNode array = entityService.getItems(
                    CoverPhoto.ENTITY_NAME,
                    String.valueOf(CoverPhoto.ENTITY_VERSION)
            ).get();
            return ResponseEntity.ok(array);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching CoverPhotos", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching CoverPhotos", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while fetching CoverPhotos", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Search CoverPhotos by condition", description = "Searches CoverPhoto entities by given search condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ArrayNode.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchCoverPhotos(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition")
            @Valid @RequestBody SearchConditionRequest conditionRequest) {
        try {
            ArrayNode array = entityService.getItemsByCondition(
                    CoverPhoto.ENTITY_NAME,
                    String.valueOf(CoverPhoto.ENTITY_VERSION),
                    conditionRequest,
                    true
            ).get();
            return ResponseEntity.ok(array);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument while searching CoverPhotos", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while searching CoverPhotos", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching CoverPhotos", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while searching CoverPhotos", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update CoverPhoto", description = "Updates an existing CoverPhoto entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Updated",
                    content = @Content(schema = @Schema(implementation = UpdateCoverPhotoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateCoverPhoto(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "CoverPhoto update payload")
            @Valid @RequestBody UpdateCoverPhotoRequest request) {
        try {
            UUID updatedId = entityService.updateItem(
                    CoverPhoto.ENTITY_NAME,
                    String.valueOf(CoverPhoto.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    request.getData()
            ).get();
            UpdateCoverPhotoResponse resp = new UpdateCoverPhotoResponse();
            resp.setTechnicalId(updatedId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument while updating CoverPhoto", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while updating CoverPhoto", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating CoverPhoto", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while updating CoverPhoto", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete CoverPhoto", description = "Deletes a CoverPhoto by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Deleted",
                    content = @Content(schema = @Schema(implementation = DeleteCoverPhotoResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteCoverPhoto(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID deletedId = entityService.deleteItem(
                    CoverPhoto.ENTITY_NAME,
                    String.valueOf(CoverPhoto.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            ).get();
            DeleteCoverPhotoResponse resp = new DeleteCoverPhotoResponse();
            resp.setTechnicalId(deletedId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument while deleting CoverPhoto", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while deleting CoverPhoto", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting CoverPhoto", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while deleting CoverPhoto", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Static DTO classes for requests/responses

    @Data
    public static class AddCoverPhotoRequest {
        @Schema(description = "CoverPhoto entity payload (JSON object)", required = true, implementation = ObjectNode.class)
        private ObjectNode data;
    }

    @Data
    public static class AddCoverPhotoResponse {
        @Schema(description = "Technical id of the created entity", required = true)
        private UUID technicalId;
    }

    @Data
    public static class AddMultipleCoverPhotoRequest {
        @Schema(description = "List of CoverPhoto JSON entities", required = true, implementation = ArrayNode.class)
        private List<ObjectNode> entities;
    }

    @Data
    public static class AddMultipleCoverPhotoResponse {
        @Schema(description = "List of technical ids of created entities", required = true)
        private List<UUID> technicalIds;
    }

    @Data
    public static class UpdateCoverPhotoRequest {
        @Schema(description = "CoverPhoto entity payload (JSON object)", required = true, implementation = ObjectNode.class)
        private ObjectNode data;
    }

    @Data
    public static class UpdateCoverPhotoResponse {
        @Schema(description = "Technical id of the updated entity", required = true)
        private UUID technicalId;
    }

    @Data
    public static class DeleteCoverPhotoResponse {
        @Schema(description = "Technical id of the deleted entity", required = true)
        private UUID technicalId;
    }
}