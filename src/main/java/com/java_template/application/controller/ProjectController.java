package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.project.version_1.Project;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.net.URI;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/projects")
@Tag(name = "Project API", description = "Endpoints for managing projects")
public class ProjectController {

    private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);

    private final EntityService entityService;

    public ProjectController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Project", description = "Create a new Project. Returns only technicalId and Location header.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = CreatedResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createProject(@Valid @RequestBody CreateProjectRequest request) {
        try {
            Project project = new Project();
            project.setId(request.getId());
            project.setName(request.getName());
            project.setDescription(request.getDescription());
            project.setStartDate(request.getStartDate());
            project.setEndDate(request.getEndDate());
            project.setStatus(request.getStatus());
            project.setOwnerId(request.getOwnerId());
            project.setMetadata(request.getMetadata());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Project.ENTITY_NAME,
                    String.valueOf(Project.ENTITY_VERSION),
                    project
            );

            UUID technicalId = idFuture.get();
            CreatedResponse resp = new CreatedResponse(technicalId.toString());
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create("/projects/" + technicalId.toString()));
            return new ResponseEntity<>(resp, headers, HttpStatus.CREATED);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createProject", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while creating project", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while creating project", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Project", description = "Retrieve stored Project by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getProject(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Project.ENTITY_NAME,
                    String.valueOf(Project.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in getProject", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while retrieving project", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving project", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "List Projects", description = "List all stored projects")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listProjects() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Project.ENTITY_NAME,
                    String.valueOf(Project.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("Execution error while listing projects", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while listing projects", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update Project", description = "Update an existing Project by technicalId and return the stored object")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @PutMapping(value = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateProject(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId,
            @Valid @RequestBody UpdateProjectRequest request) {
        try {
            Project project = new Project();
            project.setId(request.getId());
            project.setName(request.getName());
            project.setDescription(request.getDescription());
            project.setStartDate(request.getStartDate());
            project.setEndDate(request.getEndDate());
            project.setStatus(request.getStatus());
            project.setOwnerId(request.getOwnerId());
            project.setMetadata(request.getMetadata());

            CompletableFuture<UUID> updated = entityService.updateItem(
                    Project.ENTITY_NAME,
                    String.valueOf(Project.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    project
            );
            updated.get();
            // return the refreshed entity
            CompletableFuture<ObjectNode> refreshed = entityService.getItem(
                    Project.ENTITY_NAME,
                    String.valueOf(Project.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = refreshed.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in updateProject", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while updating project", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while updating project", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete Project", description = "Delete (soft) a Project by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeletedResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @DeleteMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteProject(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<UUID> deleted = entityService.deleteItem(
                    Project.ENTITY_NAME,
                    String.valueOf(Project.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID id = deleted.get();
            DeletedResponse resp = new DeletedResponse(id.toString());
            return ResponseEntity.ok(resp);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while deleting project", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while deleting project", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Data
    @Schema(name = "CreateProjectRequest", description = "Payload to create a project")
    public static class CreateProjectRequest {
        @Schema(description = "Optional business id")
        private String id;
        @Schema(description = "Project name", required = true)
        private String name;
        @Schema(description = "Project description")
        private String description;
        @Schema(description = "ISO 8601 start date")
        private String startDate;
        @Schema(description = "ISO 8601 end date")
        private String endDate;
        @Schema(description = "Project status (optional on create)")
        private String status;
        @Schema(description = "Owner id")
        private String ownerId;
        @Schema(description = "Freeform metadata map")
        private java.util.Map<String, Object> metadata;
    }

    @Data
    @Schema(name = "UpdateProjectRequest", description = "Payload to update a project")
    public static class UpdateProjectRequest {
        @Schema(description = "Optional business id")
        private String id;
        @Schema(description = "Project name")
        private String name;
        @Schema(description = "Project description")
        private String description;
        @Schema(description = "ISO 8601 start date")
        private String startDate;
        @Schema(description = "ISO 8601 end date")
        private String endDate;
        @Schema(description = "Project status")
        private String status;
        @Schema(description = "Owner id")
        private String ownerId;
        @Schema(description = "Freeform metadata map")
        private java.util.Map<String, Object> metadata;
    }

    @Data
    @Schema(name = "CreatedResponse", description = "Response returned after creation containing technicalId")
    public static class CreatedResponse {
        @Schema(description = "Technical id")
        private String technicalId;

        public CreatedResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "DeletedResponse", description = "Response returned after deletion containing technicalId")
    public static class DeletedResponse {
        @Schema(description = "Technical id")
        private String technicalId;

        public DeletedResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}
