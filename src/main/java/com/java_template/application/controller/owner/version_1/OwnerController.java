package com.java_template.application.controller.owner.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/owners")
@Tag(name = "Owner", description = "Owner entity proxy endpoints (version 1)")
public class OwnerController {

    private static final Logger logger = LoggerFactory.getLogger(OwnerController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public OwnerController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create Owner", description = "Create a single Owner entity. Returns technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OwnerCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<?> createOwner(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Owner creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = OwnerRequest.class)))
            @RequestBody OwnerRequest request
    ) {
        try {
            Owner owner = mapRequestToEntity(request);
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    owner
            );
            UUID technicalId = idFuture.get();
            OwnerCreateResponse resp = new OwnerCreateResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createOwner: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createOwner", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in createOwner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Owners", description = "Create multiple Owner entities in bulk. Returns list of technicalIds.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = OwnersBulkCreateResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createOwnersBulk(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of owners to create", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = OwnerRequest.class))))
            @RequestBody List<OwnerRequest> requests
    ) {
        try {
            List<Owner> entities = new ArrayList<>();
            for (OwnerRequest r : requests) {
                entities.add(mapRequestToEntity(r));
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    entities
            );
            List<UUID> ids = idsFuture.get();
            OwnersBulkCreateResponse resp = new OwnersBulkCreateResponse();
            List<String> techIds = new ArrayList<>();
            for (UUID id : ids) techIds.add(id.toString());
            resp.setTechnicalIds(techIds);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createOwnersBulk: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createOwnersBulk", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating owners bulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in createOwnersBulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Owner by technicalId", description = "Retrieve an Owner by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OwnerResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getOwnerById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID tid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    tid
            );
            ObjectNode node = itemFuture.get();
            OwnerResponse resp = objectMapper.treeToValue(node, OwnerResponse.class);
            resp.setTechnicalId(technicalId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId or request for getOwnerById: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getOwnerById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getOwnerById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Owners", description = "Retrieve all Owner entities")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = OwnerResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping
    public ResponseEntity<?> listOwners() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            List<OwnerResponse> list = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                ObjectNode node = (ObjectNode) array.get(i);
                OwnerResponse resp = objectMapper.treeToValue(node, OwnerResponse.class);
                // technicalId may not be present in node; skip setting if absent
                list.add(resp);
            }
            return ResponseEntity.ok(list);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException in listOwners", e);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing owners", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in listOwners", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Find Owners by condition", description = "Retrieve Owner entities matching a simple search condition")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = OwnerResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchOwners(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition request", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition
    ) {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode array = itemsFuture.get();
            List<OwnerResponse> list = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                ObjectNode node = (ObjectNode) array.get(i);
                OwnerResponse resp = objectMapper.treeToValue(node, OwnerResponse.class);
                list.add(resp);
            }
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search condition: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException in searchOwners", e);
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching owners", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in searchOwners", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Owner", description = "Update an existing Owner by technicalId. Returns updated technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OwnerCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateOwner(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Owner update payload", required = true,
                    content = @Content(schema = @Schema(implementation = OwnerRequest.class)))
            @RequestBody OwnerRequest request
    ) {
        try {
            UUID tid = UUID.fromString(technicalId);
            Owner owner = mapRequestToEntity(request);
            CompletableFuture<UUID> updated = entityService.updateItem(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    tid,
                    owner
            );
            UUID returned = updated.get();
            OwnerCreateResponse resp = new OwnerCreateResponse();
            resp.setTechnicalId(returned.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request in updateOwner: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateOwner", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in updateOwner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Owner", description = "Delete an Owner by technicalId. Returns deleted technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OwnerCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteOwner(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID tid = UUID.fromString(technicalId);
            CompletableFuture<UUID> deleted = entityService.deleteItem(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    tid
            );
            UUID returned = deleted.get();
            OwnerCreateResponse resp = new OwnerCreateResponse();
            resp.setTechnicalId(returned.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId in deleteOwner: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteOwner", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting owner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in deleteOwner", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Helper mapper: simple field copy, no business logic
    private Owner mapRequestToEntity(OwnerRequest req) {
        if (req == null) throw new IllegalArgumentException("Request body is null");
        Owner owner = new Owner();
        owner.setId(req.getId());
        owner.setName(req.getName());
        owner.setAddress(req.getAddress());
        owner.setAdoptedPetIds(req.getAdoptedPetIds());
        owner.setFavorites(req.getFavorites());
        owner.setProfileBadges(req.getProfileBadges());
        owner.setVerificationStatus(req.getVerificationStatus());
        if (req.getContactInfo() != null) {
            Owner.ContactInfo ci = new Owner.ContactInfo();
            ci.setEmail(req.getContactInfo().getEmail());
            ci.setPhone(req.getContactInfo().getPhone());
            owner.setContactInfo(ci);
        }
        return owner;
    }

    @Data
    @Schema(description = "Owner request payload")
    public static class OwnerRequest {
        @Schema(description = "Business id", example = "o-45")
        private String id;
        @Schema(description = "Owner full name", example = "Alex Doe")
        private String name;
        @Schema(description = "Contact information")
        private ContactInfoDto contactInfo;
        @Schema(description = "Postal address", example = "123 Cat Lane")
        private String address;
        @Schema(description = "Adopted pet ids")
        private List<String> adoptedPetIds;
        @Schema(description = "Favorited pet ids")
        private List<String> favorites;
        @Schema(description = "Verification status", example = "VERIFIED")
        private String verificationStatus;
        @Schema(description = "Profile badges")
        private List<String> profileBadges;

        @Data
        @Schema(description = "Contact info")
        public static class ContactInfoDto {
            @Schema(description = "Email", example = "alex@example.com")
            private String email;
            @Schema(description = "Phone", example = "+123456789")
            private String phone;
        }
    }

    @Data
    @Schema(description = "Owner response payload")
    public static class OwnerResponse {
        @Schema(description = "Technical id (storage id)", example = "owner_9001")
        private String technicalId;
        @Schema(description = "Business id", example = "o-45")
        private String id;
        @Schema(description = "Owner full name", example = "Alex Doe")
        private String name;
        @Schema(description = "Contact information")
        private OwnerRequest.ContactInfoDto contactInfo;
        @Schema(description = "Postal address", example = "123 Cat Lane")
        private String address;
        @Schema(description = "Adopted pet ids")
        private List<String> adoptedPetIds;
        @Schema(description = "Favorited pet ids")
        private List<String> favorites;
        @Schema(description = "Verification status", example = "VERIFIED")
        private String verificationStatus;
        @Schema(description = "Profile badges")
        private List<String> profileBadges;
    }

    @Data
    @Schema(description = "Create response containing technicalId")
    public static class OwnerCreateResponse {
        @Schema(description = "Technical id", example = "owner_9001")
        private String technicalId;
    }

    @Data
    @Schema(description = "Bulk create response containing technicalIds")
    public static class OwnersBulkCreateResponse {
        @Schema(description = "List of technical ids")
        private List<String> technicalIds;
    }
}