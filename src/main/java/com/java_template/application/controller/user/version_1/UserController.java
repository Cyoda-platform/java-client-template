package com.java_template.application.controller.user.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.java_template.application.entity.user.version_1.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/users")
@Tag(name = "User Controller", description = "CRUD proxy endpoints for User entity (version 1)")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public UserController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Operation(summary = "Create User", description = "Persist a User entity and start the User workflow. Returns technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CreateUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<CreateUserResponse> createUser(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Create user request payload")
            @RequestBody CreateUserRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is missing");
            }

            // Map request DTO to entity. Do not execute business logic here.
            User userEntity = objectMapper.convertValue(request, User.class);

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    User.ENTITY_NAME,
                    User.ENTITY_VERSION,
                    userEntity
            );
            UUID entityId = idFuture.get();
            CreateUserResponse response = new CreateUserResponse(entityId.toString());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createUser: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException while creating user", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating user", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception ex) {
            logger.error("Unexpected error while creating user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get User by technicalId", description = "Retrieve a persisted User entity by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = GetUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<GetUserResponse> getUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();

            if (dataPayload == null || dataPayload.getData() == null || dataPayload.getData().isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            JsonNode dataNode = (JsonNode) dataPayload.getData();

            // Convert JSON data to response DTO
            GetUserResponse response = objectMapper.treeToValue(dataNode, GetUserResponse.class);

            // Try to extract technicalId from meta if available, otherwise use path param
            try {
                if (dataPayload.getMeta() != null && dataPayload.getMeta().has("entityId")) {
                    response.setTechnicalId(dataPayload.getMeta().get("entityId").asText());
                } else {
                    response.setTechnicalId(technicalId);
                }
            } catch (Exception ignored) {
                response.setTechnicalId(technicalId);
            }

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getUser: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException while fetching user", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching user", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception ex) {
            logger.error("Unexpected error while fetching user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Static DTOs for requests/responses (no Lombok to ensure compilation even if annotation processing isn't configured)
    public static class CreateUserRequest {
        @Schema(description = "Business user id", example = "u_001")
        private String userId;

        @Schema(description = "Full name of the user", example = "Ava Smith")
        private String fullName;

        @Schema(description = "Email address", example = "ava@example.com")
        private String email;

        @Schema(description = "Phone number", example = "555-0100")
        private String phone;

        @Schema(description = "Primary address", example = "123 Cat Ln")
        private String address;

        @Schema(description = "ISO-8601 registration timestamp (optional)", example = "2025-08-28T12:00:00Z")
        private String registeredAt;

        @Schema(description = "User status (optional)", example = "Registered")
        private String status;

        @Schema(description = "User preferences (optional)")
        private Map<String, Object> preferences;

        @Schema(description = "Adopted pet ids (optional)")
        private List<String> adoptedPetIds;

        public CreateUserRequest() {}

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getRegisteredAt() {
            return registeredAt;
        }

        public void setRegisteredAt(String registeredAt) {
            this.registeredAt = registeredAt;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Map<String, Object> getPreferences() {
            return preferences;
        }

        public void setPreferences(Map<String, Object> preferences) {
            this.preferences = preferences;
        }

        public List<String> getAdoptedPetIds() {
            return adoptedPetIds;
        }

        public void setAdoptedPetIds(List<String> adoptedPetIds) {
            this.adoptedPetIds = adoptedPetIds;
        }
    }

    public static class CreateUserResponse {
        @Schema(description = "Technical ID of the persisted entity", example = "user_xyz789")
        private String technicalId;

        public CreateUserResponse() {}

        public CreateUserResponse(String technicalId) {
            this.technicalId = technicalId;
        }

        public String getTechnicalId() {
            return technicalId;
        }

        public void setTechnicalId(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    public static class GetUserResponse {
        @Schema(description = "Technical ID of the persisted entity", example = "user_xyz789")
        private String technicalId;

        @Schema(description = "Business user id", example = "u_001")
        private String userId;

        @Schema(description = "Full name of the user", example = "Ava Smith")
        private String fullName;

        @Schema(description = "Email address", example = "ava@example.com")
        private String email;

        @Schema(description = "Phone number", example = "555-0100")
        private String phone;

        @Schema(description = "Primary address", example = "123 Cat Ln")
        private String address;

        @Schema(description = "ISO-8601 registration timestamp", example = "2025-08-28T12:00:00Z")
        private String registeredAt;

        @Schema(description = "User preferences")
        private Map<String, Object> preferences;

        @Schema(description = "Adopted pet ids")
        private List<String> adoptedPetIds;

        @Schema(description = "User status", example = "Active")
        private String status;

        public GetUserResponse() {}

        public String getTechnicalId() {
            return technicalId;
        }

        public void setTechnicalId(String technicalId) {
            this.technicalId = technicalId;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getRegisteredAt() {
            return registeredAt;
        }

        public void setRegisteredAt(String registeredAt) {
            this.registeredAt = registeredAt;
        }

        public Map<String, Object> getPreferences() {
            return preferences;
        }

        public void setPreferences(Map<String, Object> preferences) {
            this.preferences = preferences;
        }

        public List<String> getAdoptedPetIds() {
            return adoptedPetIds;
        }

        public void setAdoptedPetIds(List<String> adoptedPetIds) {
            this.adoptedPetIds = adoptedPetIds;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}