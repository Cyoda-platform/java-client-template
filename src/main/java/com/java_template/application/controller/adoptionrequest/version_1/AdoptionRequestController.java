package com.java_template.application.controller.adoptionrequest.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/adoption-request")
@Tag(name = "AdoptionRequest", description = "Controller for AdoptionRequest entity operations")
public class AdoptionRequestController {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionRequestController.class);

    private final EntityService entityService;

    public AdoptionRequestController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create AdoptionRequest", description = "Create a single AdoptionRequest entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Created", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "AdoptionRequest payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateRequest.class)))
            @RequestBody CreateRequest request) {
        try {
            if (request == null || request.getAdoptionRequest() == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    request.getAdoptionRequest()
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new CreateResponse(id));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid create request", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException during create", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during create", e);
            return ResponseEntity.status(500).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during create", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple AdoptionRequests", description = "Create multiple AdoptionRequest entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Created", content = @Content(schema = @Schema(implementation = CreateManyResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createMany(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of AdoptionRequest payloads", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateManyRequest.class))))
            @RequestBody CreateManyRequest request) {
        try {
            if (request == null || request.getAdoptionRequests() == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    request.getAdoptionRequests()
            );
            List<UUID> ids = idsFuture.get();
            return ResponseEntity.ok(new CreateManyResponse(ids));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid bulk create request", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException during bulk create", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during bulk create", e);
            return ResponseEntity.status(500).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during bulk create", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get AdoptionRequest by ID", description = "Retrieve a single AdoptionRequest by technical ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(new GetResponse(node));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid getById request", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException during getById", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getById", e);
            return ResponseEntity.status(500).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during getById", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all AdoptionRequests", description = "Retrieve all AdoptionRequest entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = GetManyResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAll() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION)
            );
            ArrayNode nodes = itemsFuture.get();
            return ResponseEntity.ok(new GetManyResponse(nodes));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException during getAll", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getAll", e);
            return ResponseEntity.status(500).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during getAll", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Search AdoptionRequests", description = "Retrieve AdoptionRequest entities by search condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = GetManyResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> search(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition request", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest conditionRequest) {
        try {
            if (conditionRequest == null) {
                throw new IllegalArgumentException("Search condition is required");
            }
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    conditionRequest,
                    true
            );
            ArrayNode nodes = filteredItemsFuture.get();
            return ResponseEntity.ok(new GetManyResponse(nodes));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid search request", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException during search", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during search", e);
            return ResponseEntity.status(500).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during search", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Update AdoptionRequest", description = "Update an existing AdoptionRequest by technical ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Updated", content = @Content(schema = @Schema(implementation = UpdateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> update(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "AdoptionRequest payload to update", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateRequest.class)))
            @RequestBody UpdateRequest request) {
        try {
            if (request == null || request.getAdoptionRequest() == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    id,
                    request.getAdoptionRequest()
            );
            UUID updated = updatedIdFuture.get();
            return ResponseEntity.ok(new UpdateResponse(updated));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid update request", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException during update", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during update", e);
            return ResponseEntity.status(500).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during update", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete AdoptionRequest", description = "Delete an AdoptionRequest by technical ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Deleted", content = @Content(schema = @Schema(implementation = DeleteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> delete(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    AdoptionRequest.ENTITY_NAME,
                    String.valueOf(AdoptionRequest.ENTITY_VERSION),
                    id
            );
            UUID deleted = deletedIdFuture.get();
            return ResponseEntity.ok(new DeleteResponse(deleted));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid delete request", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException during delete", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during delete", e);
            return ResponseEntity.status(500).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during delete", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // DTOs

    @Data
    @Schema(name = "CreateRequest", description = "Request to create an AdoptionRequest")
    public static class CreateRequest {
        @Schema(description = "AdoptionRequest entity as JSON object", required = true, implementation = ObjectNode.class)
        private ObjectNode adoptionRequest;
    }

    @Data
    @Schema(name = "CreateResponse", description = "Response after creating an AdoptionRequest")
    public static class CreateResponse {
        @Schema(description = "Technical ID of created entity", required = true)
        private UUID technicalId;

        public CreateResponse(UUID technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "CreateManyRequest", description = "Request to create multiple AdoptionRequests")
    public static class CreateManyRequest {
        @Schema(description = "List of AdoptionRequest entities", required = true, implementation = ObjectNode.class)
        private List<ObjectNode> adoptionRequests;
    }

    @Data
    @Schema(name = "CreateManyResponse", description = "Response after creating multiple AdoptionRequests")
    public static class CreateManyResponse {
        @Schema(description = "Technical IDs of created entities", required = true)
        private List<UUID> technicalIds;

        public CreateManyResponse(List<UUID> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }

    @Data
    @Schema(name = "GetResponse", description = "Get single AdoptionRequest response")
    public static class GetResponse {
        @Schema(description = "AdoptionRequest entity as JSON", required = true, implementation = ObjectNode.class)
        private ObjectNode adoptionRequest;

        public GetResponse(ObjectNode adoptionRequest) {
            this.adoptionRequest = adoptionRequest;
        }
    }

    @Data
    @Schema(name = "GetManyResponse", description = "Get multiple AdoptionRequests response")
    public static class GetManyResponse {
        @Schema(description = "List of AdoptionRequest entities as JSON array", required = true, implementation = ArrayNode.class)
        private ArrayNode adoptionRequests;

        public GetManyResponse(ArrayNode adoptionRequests) {
            this.adoptionRequests = adoptionRequests;
        }
    }

    @Data
    @Schema(name = "UpdateRequest", description = "Request to update an AdoptionRequest")
    public static class UpdateRequest {
        @Schema(description = "AdoptionRequest entity as JSON object", required = true, implementation = ObjectNode.class)
        private ObjectNode adoptionRequest;
    }

    @Data
    @Schema(name = "UpdateResponse", description = "Response after updating an AdoptionRequest")
    public static class UpdateResponse {
        @Schema(description = "Technical ID of updated entity", required = true)
        private UUID technicalId;

        public UpdateResponse(UUID technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "DeleteResponse", description = "Response after deleting an AdoptionRequest")
    public static class DeleteResponse {
        @Schema(description = "Technical ID of deleted entity", required = true)
        private UUID technicalId;

        public DeleteResponse(UUID technicalId) {
            this.technicalId = technicalId;
        }
    }
}