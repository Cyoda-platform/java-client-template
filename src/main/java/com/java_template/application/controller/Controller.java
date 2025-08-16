package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Workflow;
import com.java_template.application.entity.Task;
import com.java_template.application.entity.Notification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // Local counters to simulate unique technicalIds as prefix (still needed to generate human-readable technicalId keys)
    private final AtomicLong workflowIdCounter = new AtomicLong(1);
    private final AtomicLong taskIdCounter = new AtomicLong(1);
    private final AtomicLong notificationIdCounter = new AtomicLong(1);

    // ----------- Workflow Endpoints ------------

    @PostMapping("/workflows")
    public ResponseEntity<Map<String, String>> createWorkflow(@Valid @RequestBody Workflow workflow) throws JsonProcessingException, ExecutionException, InterruptedException {
        try {
            if (!workflow.isValid()) {
                log.error("Invalid Workflow entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(Workflow.ENTITY_NAME, ENTITY_VERSION, workflow);
            UUID technicalUUID = idFuture.get();

            String technicalId = "wf-" + workflowIdCounter.getAndIncrement();

            log.info("Workflow created with technicalId: {} (UUID: {})", technicalId, technicalUUID);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in createWorkflow", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error in createWorkflow", e);
            throw e;
        } catch (Exception e) {
            log.error("Error in createWorkflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/workflows/{id}")
    public ResponseEntity<Workflow> getWorkflow(@PathVariable("id") String id) throws JsonProcessingException, ExecutionException, InterruptedException {
        try {
            UUID technicalUUID = UUID.fromString(id);
            CompletableFuture<ObjectNode> workflowFuture = entityService.getItem(Workflow.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode workflowNode = workflowFuture.get();
            if (workflowNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Workflow workflow = objectMapper.treeToValue(workflowNode, Workflow.class);
            return ResponseEntity.ok(workflow);

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in getWorkflow", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error in getWorkflow", e);
            throw e;
        } catch (Exception e) {
            log.error("Error in getWorkflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ----------- Task Endpoints ------------

    @PostMapping("/tasks")
    public ResponseEntity<Map<String, String>> createTask(@Valid @RequestBody Task task) throws JsonProcessingException, ExecutionException, InterruptedException {
        try {
            if (!task.isValid()) {
                log.error("Invalid Task entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // convert workflowTechnicalId string to UUID for condition search
            UUID workflowUUID = UUID.fromString(task.getWorkflowTechnicalId());
            SearchConditionRequest workflowCondition = SearchConditionRequest.group("AND",
                    Condition.of("$.technicalId", "EQUALS", workflowUUID.toString()));

            CompletableFuture<ArrayNode> workflowSearchFuture = entityService.getItemsByCondition(Workflow.ENTITY_NAME, ENTITY_VERSION, workflowCondition, true);
            ArrayNode workflows = workflowSearchFuture.get();
            if (workflows.isEmpty()) {
                log.error("Referenced Workflow not found for technicalId: {}", task.getWorkflowTechnicalId());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(Task.ENTITY_NAME, ENTITY_VERSION, task);
            UUID technicalUUID = idFuture.get();

            String technicalId = "task-" + taskIdCounter.getAndIncrement();

            log.info("Task created with technicalId: {} (UUID: {})", technicalId, technicalUUID);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in createTask", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error in createTask", e);
            throw e;
        } catch (Exception e) {
            log.error("Error in createTask", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<Task> getTask(@PathVariable("id") String id) throws JsonProcessingException, ExecutionException, InterruptedException {
        try {
            UUID technicalUUID = UUID.fromString(id);
            CompletableFuture<ObjectNode> taskFuture = entityService.getItem(Task.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode taskNode = taskFuture.get();
            if (taskNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Task task = objectMapper.treeToValue(taskNode, Task.class);
            return ResponseEntity.ok(task);

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in getTask", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error in getTask", e);
            throw e;
        } catch (Exception e) {
            log.error("Error in getTask", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ----------- Notification Endpoints ------------

    @PostMapping("/notifications")
    public ResponseEntity<Map<String, String>> createNotification(@Valid @RequestBody Notification notification) throws JsonProcessingException, ExecutionException, InterruptedException {
        try {
            if (!notification.isValid()) {
                log.error("Invalid Notification entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            UUID taskUUID = UUID.fromString(notification.getTaskTechnicalId());
            SearchConditionRequest taskCondition = SearchConditionRequest.group("AND",
                    Condition.of("$.technicalId", "EQUALS", taskUUID.toString()));

            CompletableFuture<ArrayNode> taskSearchFuture = entityService.getItemsByCondition(Task.ENTITY_NAME, ENTITY_VERSION, taskCondition, true);
            ArrayNode tasks = taskSearchFuture.get();
            if (tasks.isEmpty()) {
                log.error("Referenced Task not found for technicalId: {}", notification.getTaskTechnicalId());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(Notification.ENTITY_NAME, ENTITY_VERSION, notification);
            UUID technicalUUID = idFuture.get();

            String technicalId = "notif-" + notificationIdCounter.getAndIncrement();

            log.info("Notification created with technicalId: {} (UUID: {})", technicalId, technicalUUID);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in createNotification", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error in createNotification", e);
            throw e;
        } catch (Exception e) {
            log.error("Error in createNotification", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/notifications/{id}")
    public ResponseEntity<Notification> getNotification(@PathVariable("id") String id) throws JsonProcessingException, ExecutionException, InterruptedException {
        try {
            UUID technicalUUID = UUID.fromString(id);
            CompletableFuture<ObjectNode> notificationFuture = entityService.getItem(Notification.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode notificationNode = notificationFuture.get();
            if (notificationNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Notification notification = objectMapper.treeToValue(notificationNode, Notification.class);
            return ResponseEntity.ok(notification);

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in getNotification", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error in getNotification", e);
            throw e;
        } catch (Exception e) {
            log.error("Error in getNotification", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}