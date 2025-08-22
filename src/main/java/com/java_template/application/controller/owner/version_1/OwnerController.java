package com.java_template.application.controller.owner.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.owner.version_1.Owner;
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
import jakarta.validation.Valid;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/owners")
@Tag(name = "Owner", description = "Owner entity operations (version 1)")
public class OwnerController {

    private static final Logger logger = LoggerFactory.getLogger(OwnerController.class);

    private final EntityService entityService;

    public OwnerController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Owner", description = "Create a new Owner entity. Returns the technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createOwner(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Owner creation payload", required = true,
            content = @Content(schema = @Schema(implementation = OwnerCreateRequest.class)))
                                             @Valid @RequestBody OwnerCreateRequest request) {
        try {
            // Build ObjectNode payload from request using Jackson ObjectNode via EntityService contract expects plain object
            ObjectNode payload = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            if (request.getId() != null) payload.put("id", request.getId());
            if (request.getName() != null) payload.put("name", request.getName());
            if (request.getEmail() != null) payload.put("email", request.getEmail());
            if (request.getPhone() != null) payload.put("phone", request.getPhone());
            if (request.getAddress() != null) payload.put("address", request.getAddress());
            if (request.getRole() != null) payload.put("role", request.getRole());
            if (request.getVerified() != null) payload.put("verified", request.getVerified());
            if (request.getFavorites() != null) {
                ArrayNode favs = payload.putArray("favorites");
                for (String f : request.getFavorites()) favs.add(f);
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    payload
            );

            UUID technicalId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.error("Invalid request to create owner", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating owner", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating owner", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while creating owner", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Owner by technicalId", description = "Retrieve an Owner by its technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OwnerResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getOwnerById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID tid = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    tid
            );

            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Owner not found");
            }

            OwnerResponse resp = mapObjectNodeToOwnerResponse(technicalId, node);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.error("Invalid technicalId for getOwnerById", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching owner", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching owner", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while fetching owner", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "List all Owners", description = "Retrieve all Owner entities")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = OwnerResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE, params = {})
    public ResponseEntity<?> listOwners() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION)
            );
            ArrayNode arrayNode = itemsFuture.get();
            List<OwnerResponse> out = new ArrayList<>();
            if (arrayNode != null) {
                for (JsonNode n : arrayNode) {
                    if (n instanceof ObjectNode) {
                        String technicalId = n.has("technicalId") ? n.get("technicalId").asText() : null;
                        out.add(mapObjectNodeToOwnerResponse(technicalId, (ObjectNode) n));
                    }
                }
            }
            return ResponseEntity.ok(out);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while listing owners", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing owners", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while listing owners", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Search Owners by condition", description = "Search Owners by simple field conditions")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = OwnerResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchOwners(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition payload", required = true,
            content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
                                           @Valid @RequestBody SearchConditionRequest condition) {
        try {
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode arrayNode = filteredItemsFuture.get();
            List<OwnerResponse> out = new ArrayList<>();
            if (arrayNode != null) {
                for (JsonNode n : arrayNode) {
                    if (n instanceof ObjectNode) {
                        String technicalId = n.has("technicalId") ? n.get("technicalId").asText() : null;
                        out.add(mapObjectNodeToOwnerResponse(technicalId, (ObjectNode) n));
                    }
                }
            }
            return ResponseEntity.ok(out);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid search condition for owners", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while searching owners", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching owners", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while searching owners", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update Owner", description = "Update an existing Owner entity")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(value = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateOwner(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Owner update payload", required = true,
                    content = @Content(schema = @Schema(implementation = OwnerUpdateRequest.class)))
            @Valid @RequestBody OwnerUpdateRequest request) {
        try {
            UUID tid = UUID.fromString(technicalId);

            ObjectNode payload = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            if (request.getName() != null) payload.put("name", request.getName());
            if (request.getEmail() != null) payload.put("email", request.getEmail());
            if (request.getPhone() != null) payload.put("phone", request.getPhone());
            if (request.getAddress() != null) payload.put("address", request.getAddress());
            if (request.getRole() != null) payload.put("role", request.getRole());
            if (request.getVerified() != null) payload.put("verified", request.getVerified());
            if (request.getFavorites() != null) {
                ArrayNode favs = payload.putArray("favorites");
                for (String f : request.getFavorites()) favs.add(f);
            }

            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    tid,
                    payload
            );
            UUID uid = updatedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(uid.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.error("Invalid request to update owner", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while updating owner", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating owner", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while updating owner", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete Owner", description = "Delete an Owner by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteOwner(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID tid = UUID.fromString(technicalId);

            CompletableFuture<UUID> deletedId = entityService.deleteItem(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    tid
            );
            UUID did = deletedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(did.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.error("Invalid technicalId for deleteOwner", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while deleting owner", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting owner", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while deleting owner", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    private OwnerResponse mapObjectNodeToOwnerResponse(String technicalId, ObjectNode node) {
        OwnerResponse resp = new OwnerResponse();
        resp.setTechnicalId(technicalId);
        if (node.has("id") && !node.get("id").isNull()) resp.setId(node.get("id").asText());
        if (node.has("name") && !node.get("name").isNull()) resp.setName(node.get("name").asText());
        if (node.has("email") && !node.get("email").isNull()) resp.setEmail(node.get("email").asText());
        if (node.has("phone") && !node.get("phone").isNull()) resp.setPhone(node.get("phone").asText());
        if (node.has("address") && !node.get("address").isNull()) resp.setAddress(node.get("address").asText());
        if (node.has("role") && !node.get("role").isNull()) resp.setRole(node.get("role").asText());
        if (node.has("verified") && !node.get("verified").isNull()) resp.setVerified(node.get("verified").asBoolean());
        if (node.has("favorites") && node.get("favorites").isArray()) {
            List<String> favs = new ArrayList<>();
            for (JsonNode fn : node.withArray("favorites")) {
                if (!fn.isNull()) favs.add(fn.asText());
            }
            resp.setFavorites(favs);
        }
        return resp;
    }

    @Data
    @Schema(name = "OwnerCreateRequest", description = "Payload to create an Owner")
    public static class OwnerCreateRequest {
        @Schema(description = "External id if any", example = "owner_98765")
        private String id;

        @Schema(description = "Owner full name", example = "Alex Smith", required = true)
        private String name;

        @Schema(description = "Contact email", example = "alex@example.com")
        private String email;

        @Schema(description = "Contact phone", example = "+1234567890")
        private String phone;

        @Schema(description = "Postal address", example = "123 Cat Lane")
        private String address;

        @Schema(description = "Favorite pet ids")
        private List<String> favorites;

        @Schema(description = "Role of the user", example = "user")
        private String role;

        @Schema(description = "Contact verified flag")
        private Boolean verified;
    }

    @Data
    @Schema(name = "OwnerUpdateRequest", description = "Payload to update an Owner")
    public static class OwnerUpdateRequest {
        @Schema(description = "Owner full name", example = "Alex Smith")
        private String name;

        @Schema(description = "Contact email", example = "alex@example.com")
        private String email;

        @Schema(description = "Contact phone", example = "+1234567890")
        private String phone;

        @Schema(description = "Postal address", example = "123 Cat Lane")
        private String address;

        @Schema(description = "Favorite pet ids")
        private List<String> favorites;

        @Schema(description = "Role of the user", example = "user")
        private String role;

        @Schema(description = "Contact verified flag")
        private Boolean verified;
    }

    @Data
    @Schema(name = "OwnerResponse", description = "Owner retrieval response")
    public static class OwnerResponse {
        @Schema(description = "Technical ID", example = "owner_98765")
        private String technicalId;

        @Schema(description = "Domain id if any", example = "owner_98765")
        private String id;

        @Schema(description = "Owner full name", example = "Alex Smith")
        private String name;

        @Schema(description = "Contact email", example = "alex@example.com")
        private String email;

        @Schema(description = "Contact phone", example = "+1234567890")
        private String phone;

        @Schema(description = "Postal address", example = "123 Cat Lane")
        private String address;

        @Schema(description = "Favorite pet ids")
        private List<String> favorites;

        @Schema(description = "Role of the user", example = "user")
        private String role;

        @Schema(description = "Contact verified flag")
        private Boolean verified;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response that contains only the technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID", example = "7b9f7c3e-1e7a-4c6f-9d3f-2b0a9b7d6e4f")
        private String technicalId;
    }
}