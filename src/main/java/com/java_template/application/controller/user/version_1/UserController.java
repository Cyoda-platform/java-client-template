package com.java_template.application.controller.user.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/entity/User/v1")
@Tag(name = "User", description = "User entity proxy controller (v1)")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public UserController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create User", description = "Create a new User entity (event). Returns technicalId only.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createUser(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = UserCreateRequest.class)))
            @RequestBody UserCreateRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            // Map request -> entity (no business logic)
            User user = new User();
            user.setId(request.getId());
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setPhone(request.getPhone());
            if (request.getPrimaryAddress() != null) {
                User.Address addr = new User.Address();
                addr.setLine1(request.getPrimaryAddress().getLine1());
                addr.setLine2(request.getPrimaryAddress().getLine2());
                addr.setCity(request.getPrimaryAddress().getCity());
                addr.setPostal(request.getPrimaryAddress().getPostal());
                addr.setCountry(request.getPrimaryAddress().getCountry());
                user.setPrimaryAddress(addr);
            }
            user.setProfileUpdatedAt(request.getProfileUpdatedAt());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    User.ENTITY_NAME,
                    User.ENTITY_VERSION,
                    user
            );
            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request for createUser: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createUser", ex);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unhandled exception in createUser", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get User by technicalId", description = "Retrieve stored User entity by technicalId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getUserById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(404).body("Entity not found");
            }
            JsonNode dataNode = dataPayload.getData();
            UserResponse response = objectMapper.treeToValue(dataNode, UserResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request for getUserById: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getUserById", ex);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unhandled exception in getUserById", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "List Users", description = "Retrieve all User entities (non-paginated)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listUsers() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    User.ENTITY_NAME,
                    User.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<UserResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    JsonNode data = payload.getData();
                    if (data != null && !data.isNull()) {
                        UserResponse r = objectMapper.treeToValue(data, UserResponse.class);
                        responses.add(r);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in listUsers", ex);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unhandled exception in listUsers", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update User", description = "Update an existing User entity by technicalId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User update payload", required = true,
                    content = @Content(schema = @Schema(implementation = UserUpdateRequest.class)))
            @RequestBody UserUpdateRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            // Map request -> entity (no business logic)
            User user = new User();
            user.setId(request.getId());
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setPhone(request.getPhone());
            if (request.getPrimaryAddress() != null) {
                User.Address addr = new User.Address();
                addr.setLine1(request.getPrimaryAddress().getLine1());
                addr.setLine2(request.getPrimaryAddress().getLine2());
                addr.setCity(request.getPrimaryAddress().getCity());
                addr.setPostal(request.getPrimaryAddress().getPostal());
                addr.setCountry(request.getPrimaryAddress().getCountry());
                user.setPrimaryAddress(addr);
            }
            user.setProfileUpdatedAt(request.getProfileUpdatedAt());

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    UUID.fromString(technicalId),
                    user
            );
            UUID updatedId = updatedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request for updateUser: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateUser", ex);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unhandled exception in updateUser", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete User", description = "Delete an existing User entity by technicalId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(UUID.fromString(technicalId));
            UUID deletedId = deletedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request for deleteUser: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteUser", ex);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unhandled exception in deleteUser", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    // Static DTO classes for request/response payloads

    @Data
    @Schema(name = "UserCreateRequest", description = "Payload for creating a User")
    public static class UserCreateRequest {
        @Schema(description = "Entity id (natural id)", required = true, example = "user-123")
        private String id;

        @Schema(description = "Full name", required = true, example = "Jane Doe")
        private String name;

        @Schema(description = "Email address", required = true, example = "jane@example.com")
        private String email;

        @Schema(description = "Phone number", example = "+1234567890")
        private String phone;

        @Schema(description = "Primary address")
        private AddressDto primaryAddress;

        @Schema(description = "Profile updated timestamp (ISO-8601)", example = "2025-08-28T12:00:00Z")
        private String profileUpdatedAt;
    }

    @Data
    @Schema(name = "UserUpdateRequest", description = "Payload for updating a User")
    public static class UserUpdateRequest {
        @Schema(description = "Entity id (natural id)", required = true, example = "user-123")
        private String id;

        @Schema(description = "Full name", required = true, example = "Jane Doe")
        private String name;

        @Schema(description = "Email address", required = true, example = "jane@example.com")
        private String email;

        @Schema(description = "Phone number", example = "+1234567890")
        private String phone;

        @Schema(description = "Primary address")
        private AddressDto primaryAddress;

        @Schema(description = "Profile updated timestamp (ISO-8601)", example = "2025-08-28T12:00:00Z")
        private String profileUpdatedAt;
    }

    @Data
    @Schema(name = "UserResponse", description = "User entity response representation")
    public static class UserResponse {
        @Schema(description = "Entity id (natural id)", example = "user-123")
        private String id;

        @Schema(description = "Full name", example = "Jane Doe")
        private String name;

        @Schema(description = "Email address", example = "jane@example.com")
        private String email;

        @Schema(description = "Phone number", example = "+1234567890")
        private String phone;

        @Schema(description = "Primary address")
        private AddressDto primaryAddress;

        @Schema(description = "Profile updated timestamp (ISO-8601)", example = "2025-08-28T12:00:00Z")
        private String profileUpdatedAt;
    }

    @Data
    @Schema(name = "AddressDto", description = "Address representation")
    public static class AddressDto {
        @Schema(description = "Address line 1", example = "123 Main St")
        private String line1;

        @Schema(description = "Address line 2", example = "Apt 4B")
        private String line2;

        @Schema(description = "City", example = "Metropolis")
        private String city;

        @Schema(description = "Postal code", example = "12345")
        private String postal;

        @Schema(description = "Country", example = "US")
        private String country;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing only the technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }
}