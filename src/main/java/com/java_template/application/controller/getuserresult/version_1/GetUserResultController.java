package com.java_template.application.controller.getuserresult.version_1;

import static com.java_template.common.config.Config.*;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.getuserresult.version_1.GetUserResult;
import com.java_template.application.entity.user.version_1.User;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/get-user-result/v1")
@Tag(name = "GetUserResult", description = "Controller for GetUserResult entity (proxy to EntityService)")
public class GetUserResultController {

    private static final Logger logger = LoggerFactory.getLogger(GetUserResultController.class);

    private final EntityService entityService;

    public GetUserResultController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create GetUserResult", description = "Persist a GetUserResult entity. Controller is a proxy to EntityService and does not contain business logic.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createGetUserResult(@RequestBody CreateGetUserResultRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            GetUserResult data = mapToEntity(request);
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    GetUserResult.ENTITY_NAME,
                    String.valueOf(GetUserResult.ENTITY_VERSION),
                    data
            );
            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating GetUserResult", ex);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating GetUserResult", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while creating GetUserResult", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Create multiple GetUserResult items", description = "Persist multiple GetUserResult entities (bulk).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TechnicalIdResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createGetUserResultsBulk(@RequestBody List<CreateGetUserResultRequest> requests) {
        try {
            if (requests == null || requests.isEmpty())
                throw new IllegalArgumentException("Request list must not be empty");
            List<GetUserResult> entities = new ArrayList<>();
            for (CreateGetUserResultRequest r : requests) {
                entities.add(mapToEntity(r));
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    GetUserResult.ENTITY_NAME,
                    String.valueOf(GetUserResult.ENTITY_VERSION),
                    entities
            );
            List<UUID> ids = idsFuture.get();
            List<TechnicalIdResponse> resp = new ArrayList<>();
            for (UUID id : ids) {
                TechnicalIdResponse t = new TechnicalIdResponse();
                t.setTechnicalId(id.toString());
                resp.add(t);
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid bulk request: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating GetUserResult bulk", ex);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating GetUserResult bulk", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while creating GetUserResult bulk", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all GetUserResult items", description = "Retrieve all stored GetUserResult entities.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllGetUserResults() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    GetUserResult.ENTITY_NAME,
                    String.valueOf(GetUserResult.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving all GetUserResult items", ex);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving GetUserResult items", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving GetUserResult items", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Filter GetUserResult items", description = "Retrieve GetUserResult entities matching the provided SearchConditionRequest. Use SearchConditionRequest.group(...) and Condition.of(...) for simple queries.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/filter")
    public ResponseEntity<?> filterGetUserResults(@RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) throw new IllegalArgumentException("Search condition is required");
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    GetUserResult.ENTITY_NAME,
                    String.valueOf(GetUserResult.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid filter request: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while filtering GetUserResult items", ex);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while filtering GetUserResult items", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while filtering GetUserResult items", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get GetUserResult by technicalId", description = "Retrieve a single GetUserResult by its technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getGetUserResultById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    GetUserResult.ENTITY_NAME,
                    String.valueOf(GetUserResult.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid get by id request: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving GetUserResult by id", ex);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving GetUserResult by id", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving GetUserResult by id", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update GetUserResult by technicalId", description = "Update an existing GetUserResult entity.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateGetUserResult(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId,
            @RequestBody CreateGetUserResultRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");
            GetUserResult data = mapToEntity(request);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    GetUserResult.ENTITY_NAME,
                    String.valueOf(GetUserResult.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    data
            );
            UUID updatedId = updatedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid update request: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while updating GetUserResult", ex);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating GetUserResult", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while updating GetUserResult", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete GetUserResult by technicalId", description = "Delete a GetUserResult entity by technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteGetUserResult(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    GetUserResult.ENTITY_NAME,
                    String.valueOf(GetUserResult.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID deletedId = deletedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid delete request: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException while deleting GetUserResult", ex);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting GetUserResult", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while deleting GetUserResult", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    // Mapper from request DTO to entity
    private GetUserResult mapToEntity(CreateGetUserResultRequest req) {
        GetUserResult e = new GetUserResult();
        e.setJobReference(req.getJobReference());
        e.setStatus(req.getStatus());
        e.setErrorMessage(req.getErrorMessage());
        e.setRetrievedAt(req.getRetrievedAt());
        if (req.getUser() != null) {
            User u = new User();
            u.setId(req.getUser().getId());
            u.setEmail(req.getUser().getEmail());
            u.setFirstName(req.getUser().getFirstName());
            u.setLastName(req.getUser().getLastName());
            u.setAvatar(req.getUser().getAvatar());
            u.setRetrievedAt(req.getUser().getRetrievedAt());
            u.setSource(req.getUser().getSource());
            e.setUser(u);
        } else {
            e.setUser(null);
        }
        return e;
    }

    @Data
    @Schema(name = "CreateGetUserResultRequest", description = "Request payload to create or update a GetUserResult")
    public static class CreateGetUserResultRequest {
        @Schema(description = "Reference to GetUserJob", example = "job-123e4567")
        private String jobReference;

        @Schema(description = "Status of the result (e.g. SUCCESS, NOT_FOUND, INVALID_INPUT, ERROR)", example = "SUCCESS")
        private String status;

        @Schema(description = "Error message when status is not SUCCESS", example = "User not found")
        private String errorMessage;

        @Schema(description = "ISO timestamp when result was produced", example = "2025-08-27T10:00:02Z")
        private String retrievedAt;

        @Schema(description = "User payload (present if status == SUCCESS)")
        private UserDto user;
    }

    @Data
    @Schema(name = "UserDto", description = "User data inside GetUserResult")
    public static class UserDto {
        @Schema(description = "Business identifier", example = "2")
        private Integer id;

        @Schema(description = "User email", example = "janet.weaver@reqres.in")
        private String email;

        @Schema(description = "First name", example = "Janet")
        private String firstName;

        @Schema(description = "Last name", example = "Weaver")
        private String lastName;

        @Schema(description = "Avatar URL", example = "https://reqres.in/img/faces/2-image.jpg")
        private String avatar;

        @Schema(description = "ISO timestamp when record was fetched", example = "2025-08-27T10:00:02Z")
        private String retrievedAt;

        @Schema(description = "Source system", example = "ReqRes")
        private String source;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technicalId of the persisted entity")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical identifier (UUID)", example = "123e4567-e89b-12d3-a456-426614174000")
        private String technicalId;
    }
}