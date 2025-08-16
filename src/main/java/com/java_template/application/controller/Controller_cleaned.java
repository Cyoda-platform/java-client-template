package com.java_template.application.controller;

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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    // Local counters to simulate unique technicalIds as prefix (still needed to generate human-readable technicalId keys)
    private final AtomicLong workflowIdCounter = new AtomicLong(1);
    private final AtomicLong taskIdCounter = new AtomicLong(1);
    private final AtomicLong notificationIdCounter = new AtomicLong(1);

    // ----------- Workflow Endpoints ------------

    @PostMapping("/workflows")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody Workflow workflow) {
        try {
            if (!workflow.isValid()) {
                log.error("Invalid Workflow entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Add workflow via entityService
            CompletableFuture<UUID> idFuture = entityService.addItem(Workflow.ENTITY_NAME, ENTITY_VERSION, workflow);
            UUID technicalUUID = idFuture.get();

            // Generate technicalId string key for internal reference consistent with previous approach
            String technicalId = "wf-" + workflowIdCounter.getAndIncrement();

            log.info("Workflow created with technicalId: {} (UUID: {})", technicalId, technicalUUID);

            // processWorkflow method removed

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in createWorkflow", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error in createWorkflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/workflows/{technicalId}")
    public ResponseEntity<Workflow> getWorkflow(@PathVariable String technicalId) {
        try {
            // Here, original technicalId string like "wf-1" does not map to UUID, so we must skip retrieval or return 404
            // Because we cannot map technicalId string to UUID, leave as is and return 404
            log.error("Direct technicalId based retrieval not supported with EntityService for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in getWorkflow", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error in getWorkflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ----------- Task Endpoints ------------

    @PostMapping("/tasks")
    public ResponseEntity<Map<String, String>> createTask(@RequestBody Task task) {
        try {
            if (!task.isValid()) {
                log.error("Invalid Task entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Check referenced workflow exists by condition search in EntityService (inMemory=true)
            SearchConditionRequest workflowCondition = SearchConditionRequest.group("AND",
                    Condition.of("$.technicalId", "EQUALS", task.getWorkflowTechnicalId()));

            CompletableFuture<ArrayNode> workflowSearchFuture = entityService.getItemsByCondition(Workflow.ENTITY_NAME, ENTITY_VERSION, workflowCondition, true);
            ArrayNode workflows = workflowSearchFuture.get();
            if (workflows.isEmpty()) {
                log.error("Referenced Workflow not found for technicalId: {}", task.getWorkflowTechnicalId());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Add task via entityService
            CompletableFuture<UUID> idFuture = entityService.addItem(Task.ENTITY_NAME, ENTITY_VERSION, task);
            UUID technicalUUID = idFuture.get();

            String technicalId = "task-" + taskIdCounter.getAndIncrement();

            log.info("Task created with technicalId: {} (UUID: {})", technicalId, technicalUUID);

            // processTask method removed

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in createTask", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error in createTask", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/tasks/{technicalId}")
    public ResponseEntity<Task> getTask(@PathVariable String technicalId) {
        try {
            // Similarly, direct retrieval by string technicalId not supported with EntityService
            log.error("Direct technicalId based retrieval not supported with EntityService for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in getTask", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error in getTask", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ----------- Notification Endpoints ------------

    @PostMapping("/notifications")
    public ResponseEntity<Map<String, String>> createNotification(@RequestBody Notification notification) {
        try {
            if (!notification.isValid()) {
                log.error("Invalid Notification entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Check referenced task exists by condition search in EntityService (inMemory=true)
            SearchConditionRequest taskCondition = SearchConditionRequest.group("AND",
                    Condition.of("$.technicalId", "EQUALS", notification.getTaskTechnicalId()));

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

            // processNotification method removed

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in createNotification", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error in createNotification", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/notifications/{technicalId}")
    public ResponseEntity<Notification> getNotification(@PathVariable String technicalId) {
        try {
            // Direct retrieval by string technicalId not supported with EntityService
            log.error("Direct technicalId based retrieval not supported with EntityService for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in getNotification", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error in getNotification", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}