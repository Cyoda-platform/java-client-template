package com.java_template.application.controller.owner.version_1;

import static com.java_template.common.config.Config.*;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
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

import lombok.Data;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/owners")
@Tag(name = "Owner Controller", description = "Proxy controller for Owner entity - delegates to EntityService")
@RequiredArgsConstructor
public class OwnerController {

    private static final Logger logger = LoggerFactory.getLogger(OwnerController.class);

    private final EntityService entityService;

    private final ObjectMapper mapper = new ObjectMapper();

    // Create single owner
    @Operation(summary = "Create Owner", description = "Create a new Owner (signup). Returns technicalId and Location header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreateResponse> createOwner(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Owner create payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateOwnerRequest.class)))
            @RequestBody CreateOwnerRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("Owner.name is required");
            }

            ObjectNode data = mapper.valueToTree(request);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    data
            );

            UUID technicalId = idFuture.get();

            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{technicalId}")
                    .buildAndExpand(technicalId.toString())
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(location);

            CreateResponse response = new CreateResponse();
            response.setTechnicalId(technicalId.toString());

            return new ResponseEntity<>(response, headers, HttpStatus.CREATED);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to create owner: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while creating owner", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception ex) {
            logger.error("Exception while creating owner", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Create multiple owners (batch)
    @Operation(summary = "Create Owners Batch", description = "Create multiple Owners in batch. Returns technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BatchCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(value = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BatchCreateResponse> createOwnersBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of Owners to create", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateOwnerRequest.class))))
            @RequestBody List<CreateOwnerRequest> requests
    ) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request list must not be empty");
            }

            ArrayNode arrayNode = mapper.createArrayNode();
            for (CreateOwnerRequest r : requests) {
                arrayNode.add(mapper.valueToTree(r));
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    arrayNode
            );

            List<UUID> uuids = idsFuture.get();

            BatchCreateResponse response = new BatchCreateResponse();
            for (UUID id : uuids) {
                response.getTechnicalIds().add(id.toString());
            }

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid batch create request: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during batch create", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception ex) {
            logger.error("Exception during batch create", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Get all owners
    @Operation(summary = "List Owners", description = "Retrieve all Owner entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = OwnerResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<OwnerResponse>> listOwners() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION)
            );

            ArrayNode array = itemsFuture.get();

            List<OwnerResponse> list = mapper.convertValue(array, mapper.getTypeFactory().constructCollectionType(List.class, OwnerResponse.class));

            return ResponseEntity.ok(list);

        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while listing owners", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception ex) {
            logger.error("Exception while listing owners", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Search owners by condition (in-memory)
    @Operation(summary = "Search Owners", description = "Search Owners by condition (in-memory filtering). Supply a SearchConditionRequest body.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = OwnerResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<OwnerResponse>> searchOwners(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition
    ) {
        try {
            if (condition == null) {
                throw new IllegalArgumentException("Search condition is required");
            }

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode array = filteredItemsFuture.get();

            List<OwnerResponse> list = mapper.convertValue(array, mapper.getTypeFactory().constructCollectionType(List.class, OwnerResponse.class));

            return ResponseEntity.ok(list);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid search request: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during search", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception ex) {
            logger.error("Exception during search", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Get single owner by technicalId
    @Operation(summary = "Get Owner", description = "Retrieve Owner by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OwnerResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OwnerResponse> getOwner(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    id
            );

            ObjectNode node = itemFuture.get();

            OwnerResponse resp = mapper.treeToValue(node, OwnerResponse.class);

            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid technicalId or request: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while getting owner", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception ex) {
            logger.error("Exception while getting owner", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Update owner
    @Operation(summary = "Update Owner", description = "Update an existing Owner by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(value = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UpdateResponse> updateOwner(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Owner update payload", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateOwnerRequest.class)))
            @RequestBody UpdateOwnerRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            UUID id = UUID.fromString(technicalId);

            ObjectNode data = mapper.valueToTree(request);

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    id,
                    data
            );

            UUID updatedId = updatedIdFuture.get();

            UpdateResponse response = new UpdateResponse();
            response.setTechnicalId(updatedId.toString());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid update request: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while updating owner", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception ex) {
            logger.error("Exception while updating owner", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Delete owner
    @Operation(summary = "Delete Owner", description = "Delete an Owner by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DeleteResponse> deleteOwner(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    id
            );

            UUID deletedId = deletedIdFuture.get();

            DeleteResponse response = new DeleteResponse();
            response.setTechnicalId(deletedId.toString());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid delete request: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException while deleting owner", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception ex) {
            logger.error("Exception while deleting owner", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // --- DTOs ---

    @Data
    @Schema(name = "CreateOwnerRequest", description = "Request to create an Owner")
    public static class CreateOwnerRequest {
        @Schema(description = "Business id or generated id (optional)", example = "owner_xyz789")
        private String id;
        @Schema(description = "Full name", required = true, example = "Alex Doe")
        private String name;
        @Schema(description = "Contact object", required = true)
        private Contact contact;
        @Schema(description = "Address", example = "City")
        private String address;
        @Schema(description = "Favorites (pet ids)")
        private List<String> favorites;
        @Schema(description = "Adopted pets (pet ids)")
        private List<String> adoptedPets;
        @Schema(description = "Account status", example = "pending_verification")
        private String accountStatus;
        @Schema(description = "Created at timestamp")
        private String createdAt;
        @Schema(description = "Updated at timestamp")
        private String updatedAt;
    }

    @Data
    @Schema(name = "UpdateOwnerRequest", description = "Request to update an Owner")
    public static class UpdateOwnerRequest {
        @Schema(description = "Full name", example = "Alex Doe")
        private String name;
        @Schema(description = "Contact object")
        private Contact contact;
        @Schema(description = "Address", example = "City")
        private String address;
        @Schema(description = "Favorites (pet ids)")
        private List<String> favorites;
        @Schema(description = "Adopted pets (pet ids)")
        private List<String> adoptedPets;
        @Schema(description = "Account status", example = "active")
        private String accountStatus;
    }

    @Data
    @Schema(name = "Contact", description = "Owner contact details")
    public static class Contact {
        @Schema(description = "Email", example = "alex@example.com")
        private String email;
        @Schema(description = "Phone", example = "555-0100")
        private String phone;
    }

    @Data
    @Schema(name = "CreateResponse", description = "Response containing created technicalId")
    public static class CreateResponse {
        @Schema(description = "Technical id of created entity", example = "owner_xyz789")
        private String technicalId;
    }

    @Data
    @Schema(name = "BatchCreateResponse", description = "Response containing list of created technicalIds")
    public static class BatchCreateResponse {
        @Schema(description = "Technical ids of created entities")
        private List<String> technicalIds = new java.util.ArrayList<>();
    }

    @Data
    @Schema(name = "UpdateResponse", description = "Response containing updated technicalId")
    public static class UpdateResponse {
        @Schema(description = "Technical id of updated entity", example = "owner_xyz789")
        private String technicalId;
    }

    @Data
    @Schema(name = "DeleteResponse", description = "Response containing deleted technicalId")
    public static class DeleteResponse {
        @Schema(description = "Technical id of deleted entity", example = "owner_xyz789")
        private String technicalId;
    }

    @Data
    @Schema(name = "OwnerResponse", description = "Owner representation returned by the API")
    public static class OwnerResponse {
        @Schema(description = "Business id or generated id", example = "owner_xyz789")
        private String id;
        @Schema(description = "Full name", example = "Alex Doe")
        private String name;
        @Schema(description = "Contact object")
        private Contact contact;
        @Schema(description = "Address")
        private String address;
        @Schema(description = "Favorites (pet ids)")
        private List<String> favorites;
        @Schema(description = "Adopted pets (pet ids)")
        private List<String> adoptedPets;
        @Schema(description = "Account status", example = "active")
        private String accountStatus;
        @Schema(description = "Created at timestamp")
        private String createdAt;
        @Schema(description = "Updated at timestamp")
        private String updatedAt;
    }
}