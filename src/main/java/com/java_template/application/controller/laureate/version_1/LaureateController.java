package com.java_template.application.controller.laureate.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.laureate.version_1.Laureate;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/laureate/v1")
@Tag(name = "Laureate", description = "Operations for Laureate entity")
public class LaureateController {

    private static final Logger logger = LoggerFactory.getLogger(LaureateController.class);

    private final EntityService entityService;

    public LaureateController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Add Laureate", description = "Add a single Laureate entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> addLaureate(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Laureate to add")
            @RequestBody @Valid Laureate laureate) {
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    laureate
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new IdResponse(id));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for addLaureate", iae);
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException in addLaureate", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(cause != null ? cause.getMessage() : ee.getMessage()));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ie.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error in addLaureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Add multiple Laureates", description = "Add multiple Laureate entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = IdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> addLaureates(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of Laureates to add")
            @RequestBody @Valid List<Laureate> laureates) {
        try {
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    laureates
            );
            List<UUID> ids = idsFuture.get();
            return ResponseEntity.ok(new IdsResponse(ids));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for addLaureates", iae);
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException in addLaureates", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(cause != null ? cause.getMessage() : ee.getMessage()));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding laureates", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ie.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error in addLaureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Get Laureate by technicalId", description = "Retrieve a single Laureate by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for getLaureate", iae);
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException in getLaureate", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(cause != null ? cause.getMessage() : ee.getMessage()));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ie.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error in getLaureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Get all Laureates", description = "Retrieve all Laureate entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllLaureates() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION)
            );
            ArrayNode nodes = itemsFuture.get();
            return ResponseEntity.ok(nodes);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException in getAllLaureates", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(cause != null ? cause.getMessage() : ee.getMessage()));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting all laureates", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ie.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error in getAllLaureates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Filter Laureates", description = "Retrieve Laureate entities by search condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/filter")
    public ResponseEntity<?> getLaureatesByCondition(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition")
            @RequestBody SearchConditionRequest condition) {
        try {
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode nodes = filteredItemsFuture.get();
            return ResponseEntity.ok(nodes);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search condition for getLaureatesByCondition", iae);
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException in getLaureatesByCondition", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(cause != null ? cause.getMessage() : ee.getMessage()));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while filtering laureates", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ie.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error in getLaureatesByCondition", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Update Laureate", description = "Update a Laureate entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Laureate to update")
            @RequestBody @Valid Laureate laureate) {
        try {
            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    laureate
            );
            UUID id = updatedId.get();
            return ResponseEntity.ok(new IdResponse(id));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updateLaureate", iae);
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException in updateLaureate", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(cause != null ? cause.getMessage() : ee.getMessage()));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ie.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error in updateLaureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Delete Laureate", description = "Delete a Laureate entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteLaureate(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<UUID> deletedId = entityService.deleteItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID id = deletedId.get();
            return ResponseEntity.ok(new IdResponse(id));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for deleteLaureate", iae);
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException in deleteLaureate", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(cause != null ? cause.getMessage() : ee.getMessage()));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting laureate", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ie.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error in deleteLaureate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(e.getMessage()));
        }
    }

    // DTOs

    @Data
    @Schema(name = "IdResponse", description = "Response containing a single UUID")
    public static class IdResponse {
        @Schema(description = "Technical id", required = true)
        private final UUID id;
    }

    @Data
    @Schema(name = "IdsResponse", description = "Response containing list of UUIDs")
    public static class IdsResponse {
        @Schema(description = "Technical ids", required = true)
        private final List<UUID> ids;
    }

    @Data
    @Schema(name = "ErrorResponse", description = "Error response")
    public static class ErrorResponse {
        @Schema(description = "Error message", required = true)
        private final String message;
    }
}