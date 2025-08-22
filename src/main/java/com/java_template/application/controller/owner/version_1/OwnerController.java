package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.owner.version_1.Owner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dull proxy controller for Owner entity. All business logic is implemented in workflows.
 */
@RestController
@RequestMapping("/owners")
@Tag(name = "Owner Controller", description = "Proxy endpoints for Owner entity")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Owner", description = "Creates an Owner resource (starts owner signup workflow). Returns technicalId and Location header.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createOwner(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Owner creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateRequest.class)))
            @RequestBody CreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        try {
            ObjectNode node = (ObjectNode) objectMapper.valueToTree(request);
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    node
            );
            UUID technicalId = idFuture.get();
            CreateResponse resp = new CreateResponse(technicalId.toString());
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(String.format("/owners/%s", technicalId.toString())));
            return new ResponseEntity<>(resp, headers, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request when creating Owner", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating Owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when creating Owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Owners", description = "Bulk create Owners. Returns list of technicalIds.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BulkCreateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createOwnersBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of Owner creation payloads", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateRequest.class))))
            @RequestBody List<CreateRequest> requests
    ) {
        try {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (CreateRequest r : requests) {
                arrayNode.add(objectMapper.valueToTree(r));
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    arrayNode
            );
            List<UUID> ids = idsFuture.get();
            BulkCreateResponse resp = new BulkCreateResponse(ids.stream().map(UUID::toString).toList());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid batch request when creating Owners", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating Owners batch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when creating Owners batch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Owner", description = "Retrieve Owner by technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Owner.class))),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getOwner(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId
    ) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for getOwner: {}", technicalId, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving Owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when retrieving Owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Owners", description = "Retrieve all Owners.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Owner.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listOwners() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing Owners", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when listing Owners", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Owners", description = "Search Owners by a single field condition. Provide query parameters: field, operator, value")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Owner.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/search")
    public ResponseEntity<?> searchOwners(
            @Parameter(description = "Field to filter on (JSONPath, e.g. $.name)", required = true) @RequestParam String field,
            @Parameter(description = "Operator (EQUALS, NOT_EQUAL, etc.)", required = true) @RequestParam String operator,
            @Parameter(description = "Value to compare", required = true) @RequestParam String value
    ) {
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of(field, operator, value)
            );
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode array = filteredItemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search parameters", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching Owners", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when searching Owners", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Owner", description = "Update an existing Owner's data.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateOwner(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true) @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Owner update payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateRequest.class)))
            @RequestBody CreateRequest request
    ) {
        try {
            ObjectNode node = (ObjectNode) objectMapper.valueToTree(request);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    node
            );
            UUID updatedId = updatedIdFuture.get();
            UpdateResponse resp = new UpdateResponse(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request when updating Owner: {}", technicalId, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating Owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when updating Owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Owner", description = "Delete an Owner by technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteOwner(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true) @PathVariable String technicalId
    ) {
        try {
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID deletedId = deletedIdFuture.get();
            DeleteResponse resp = new DeleteResponse(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for deleteOwner: {}", technicalId, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting Owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when deleting Owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private ResponseEntity<String> handleExecutionException(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Resource not found", cause);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Invalid argument in execution", cause);
            return ResponseEntity.badRequest().body(cause.getMessage());
        } else {
            logger.error("Execution exception", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
        }
    }

    // --- DTOs ---

    @Data
    @Schema(name = "CreateRequest", description = "Owner creation request")
    public static class CreateRequest {
        @Schema(description = "Full name of the owner", example = "Alex Doe")
        private String name;

        @Schema(description = "Contact information")
        private Contact contact;

        @Schema(description = "Address", example = "City, Country")
        private String address;

        // optional fields that client may pass
        @Schema(description = "Favorites list of pet ids")
        private List<String> favorites;

        @Schema(description = "Adopted pets list of pet ids")
        private List<String> adoptedPets;

        @Schema(description = "Account status (optional)", example = "pending_verification")
        private String accountStatus;

        @Data
        @Schema(name = "Contact", description = "Owner contact object")
        public static class Contact {
            @Schema(description = "Email address", example = "alex@example.com")
            private String email;

            @Schema(description = "Phone number", example = "555-0100")
            private String phone;
        }
    }

    @Data
    @Schema(name = "CreateResponse", description = "Response for create operations")
    public static class CreateResponse {
        @Schema(description = "Technical id of the created entity", example = "owner_xyz789")
        private String technicalId;

        public CreateResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "BulkCreateResponse", description = "Response for bulk create operations")
    public static class BulkCreateResponse {
        @Schema(description = "List of technical ids")
        private List<String> technicalIds;

        public BulkCreateResponse(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }

    @Data
    @Schema(name = "UpdateResponse", description = "Response for update operations")
    public static class UpdateResponse {
        @Schema(description = "Technical id of the updated entity")
        private String technicalId;

        public UpdateResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "DeleteResponse", description = "Response for delete operations")
    public static class DeleteResponse {
        @Schema(description = "Technical id of the deleted entity")
        private String technicalId;

        public DeleteResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}