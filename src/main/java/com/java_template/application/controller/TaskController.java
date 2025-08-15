package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.task.version_1.Task;
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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping
@Tag(name = "Task API", description = "Endpoints for managing tasks")
public class TaskController {

    private static final Logger logger = LoggerFactory.getLogger(TaskController.class);

    private final EntityService entityService;

    public TaskController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Task", description = "Create a new Task under a project. Returns only technicalId and Location header.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = CreatedResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(value = "/projects/{projectTechnicalId}/tasks", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createTask(
            @Parameter(name = "projectTechnicalId", description = "Technical ID of the project") @PathVariable("projectTechnicalId") String projectTechnicalId,
            @Valid @RequestBody CreateTaskRequest request) {
        try {
            Task task = new Task();
            task.setId(request.getId());
            task.setProjectId(request.getProjectId() != null ? request.getProjectId() : null);
            task.setTitle(request.getTitle());
            task.setDescription(request.getDescription());
            task.setStatus(request.getStatus());
            task.setAssigneeId(request.getAssigneeId());
            task.setDueDate(request.getDueDate());
            task.setPriority(request.getPriority());
            task.setDependencies(request.getDependencies());
            task.setMetadata(request.getMetadata());

            // Ensure projectTechnicalId is stored in metadata for linkage if not present in request
            if (task.getMetadata() == null) task.setMetadata(new java.util.HashMap<>());
            task.getMetadata().putIfAbsent("projectTechnicalId", projectTechnicalId);

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Task.ENTITY_NAME,
                    String.valueOf(Task.ENTITY_VERSION),
                    task
            );

            UUID technicalId = idFuture.get();
            CreatedResponse resp = new CreatedResponse(technicalId.toString());
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create("/tasks/" + technicalId.toString()));
            return new ResponseEntity<>(resp, headers, HttpStatus.CREATED);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createTask", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while creating task", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while creating task", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get Task", description = "Retrieve stored Task by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/tasks/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getTask(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Task.ENTITY_NAME,
                    String.valueOf(Task.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in getTask", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while retrieving task", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving task", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "List Tasks for Project", description = "List tasks for a given projectTechnicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/projects/{projectTechnicalId}/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listTasksForProject(
            @Parameter(name = "projectTechnicalId", description = "Technical ID of the project") @PathVariable("projectTechnicalId") String projectTechnicalId) {
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.metadata.projectTechnicalId", "EQUALS", projectTechnicalId)
            );
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Task.ENTITY_NAME,
                    String.valueOf(Task.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("Execution error while listing tasks for project", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while listing tasks for project", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "List Tasks", description = "List all stored tasks")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listTasks() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Task.ENTITY_NAME,
                    String.valueOf(Task.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("Execution error while listing tasks", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while listing tasks", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update Task", description = "Update an existing Task by technicalId and return the stored object")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @PutMapping(value = "/tasks/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateTask(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId,
            @Valid @RequestBody UpdateTaskRequest request) {
        try {
            Task task = new Task();
            task.setId(request.getId());
            task.setProjectId(request.getProjectId());
            task.setTitle(request.getTitle());
            task.setDescription(request.getDescription());
            task.setStatus(request.getStatus());
            task.setAssigneeId(request.getAssigneeId());
            task.setDueDate(request.getDueDate());
            task.setPriority(request.getPriority());
            task.setDependencies(request.getDependencies());
            task.setMetadata(request.getMetadata());

            CompletableFuture<UUID> updated = entityService.updateItem(
                    Task.ENTITY_NAME,
                    String.valueOf(Task.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    task
            );
            updated.get();
            // return the refreshed entity
            CompletableFuture<ObjectNode> refreshed = entityService.getItem(
                    Task.ENTITY_NAME,
                    String.valueOf(Task.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = refreshed.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in updateTask", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution error while updating task", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while updating task", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete Task", description = "Delete (soft) a Task by technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeletedResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @DeleteMapping(value = "/tasks/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteTask(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<UUID> deleted = entityService.deleteItem(
                    Task.ENTITY_NAME,
                    String.valueOf(Task.ENTITY_VERSION),
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
                logger.error("Execution error while deleting task", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while deleting task", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Data
    @Schema(name = "CreateTaskRequest", description = "Payload to create a task")
    public static class CreateTaskRequest {
        @Schema(description = "Optional business id")
        private String id;
        @Schema(description = "Optional project business id")
        private String projectId;
        @Schema(description = "Task title", required = true)
        private String title;
        @Schema(description = "Task description")
        private String description;
        @Schema(description = "Task status (optional on create)")
        private String status;
        @Schema(description = "Assignee id")
        private String assigneeId;
        @Schema(description = "ISO 8601 due date")
        private String dueDate;
        @Schema(description = "Priority label")
        private String priority;
        @Schema(description = "Dependencies list of task technicalIds")
        private List<String> dependencies;
        @Schema(description = "Freeform metadata map")
        private java.util.Map<String, Object> metadata;
    }

    @Data
    @Schema(name = "UpdateTaskRequest", description = "Payload to update a task")
    public static class UpdateTaskRequest {
        @Schema(description = "Optional business id")
        private String id;
        @Schema(description = "Optional project business id")
        private String projectId;
        @Schema(description = "Task title")
        private String title;
        @Schema(description = "Task description")
        private String description;
        @Schema(description = "Task status")
        private String status;
        @Schema(description = "Assignee id")
        private String assigneeId;
        @Schema(description = "ISO 8601 due date")
        private String dueDate;
        @Schema(description = "Priority label")
        private String priority;
        @Schema(description = "Dependencies list of task technicalIds")
        private List<String> dependencies;
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
