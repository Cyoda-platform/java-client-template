package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.Workflow;
import com.java_template.application.entity.Task;
import com.java_template.application.entity.Notification;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, Workflow> workflowCache = new ConcurrentHashMap<>();
    private final AtomicLong workflowIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Task> taskCache = new ConcurrentHashMap<>();
    private final AtomicLong taskIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Notification> notificationCache = new ConcurrentHashMap<>();
    private final AtomicLong notificationIdCounter = new AtomicLong(1);

    // ----------- Workflow Endpoints ------------

    @PostMapping("/workflows")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody Workflow workflow) {
        if (!workflow.isValid()) {
            log.error("Invalid Workflow entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Generate technicalId
        String technicalId = "wf-" + workflowIdCounter.getAndIncrement();
        workflowCache.put(technicalId, workflow);

        log.info("Workflow created with technicalId: {}", technicalId);

        processWorkflow(technicalId, workflow);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/workflows/{technicalId}")
    public ResponseEntity<Workflow> getWorkflow(@PathVariable String technicalId) {
        Workflow workflow = workflowCache.get(technicalId);
        if (workflow == null) {
            log.error("Workflow not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(workflow);
    }

    // ----------- Task Endpoints ------------

    @PostMapping("/tasks")
    public ResponseEntity<Map<String, String>> createTask(@RequestBody Task task) {
        if (!task.isValid()) {
            log.error("Invalid Task entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Check referenced workflow exists
        if (!workflowCache.containsKey(task.getWorkflowTechnicalId())) {
            log.error("Referenced Workflow not found for technicalId: {}", task.getWorkflowTechnicalId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Generate technicalId
        String technicalId = "task-" + taskIdCounter.getAndIncrement();
        taskCache.put(technicalId, task);

        log.info("Task created with technicalId: {}", technicalId);

        processTask(technicalId, task);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/tasks/{technicalId}")
    public ResponseEntity<Task> getTask(@PathVariable String technicalId) {
        Task task = taskCache.get(technicalId);
        if (task == null) {
            log.error("Task not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(task);
    }

    // ----------- Notification Endpoints ------------

    @PostMapping("/notifications")
    public ResponseEntity<Map<String, String>> createNotification(@RequestBody Notification notification) {
        if (!notification.isValid()) {
            log.error("Invalid Notification entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Check referenced task exists
        if (!taskCache.containsKey(notification.getTaskTechnicalId())) {
            log.error("Referenced Task not found for technicalId: {}", notification.getTaskTechnicalId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String technicalId = "notif-" + notificationIdCounter.getAndIncrement();
        notificationCache.put(technicalId, notification);

        log.info("Notification created with technicalId: {}", technicalId);

        processNotification(technicalId, notification);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/notifications/{technicalId}")
    public ResponseEntity<Notification> getNotification(@PathVariable String technicalId) {
        Notification notification = notificationCache.get(technicalId);
        if (notification == null) {
            log.error("Notification not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(notification);
    }

    // ----------- Process Methods ------------

    private void processWorkflow(String technicalId, Workflow workflow) {
        // Validation: Check name is not blank
        if (workflow.getName() == null || workflow.getName().isBlank()) {
            log.error("Workflow validation failed: name is blank");
            return;
        }
        // Additional initialization or environment setup can be done here
        log.info("Processed Workflow {} successfully", technicalId);
        // Mark workflow as READY for task creation could be implemented by setting some internal state if needed
    }

    private void processTask(String technicalId, Task task) {
        // Validation: referenced Workflow must exist - already checked in POST handler
        // Processing: assign default status if not set
        if (task.getStatus() == null || task.getStatus().isBlank()) {
            task.setStatus("PENDING");
        }
        // Enrich task details or perform other business logic here
        log.info("Processed Task {} successfully", technicalId);

        // Trigger notification creation event (simulate by creating notification)
        Notification notification = new Notification();
        notification.setTaskTechnicalId(technicalId);
        notification.setMessage("Task '" + task.getTitle() + "' is now " + task.getStatus());
        notification.setSentAt(java.time.Instant.now().toString());

        String notifId = "notif-" + notificationIdCounter.getAndIncrement();
        notificationCache.put(notifId, notification);
        processNotification(notifId, notification);
    }

    private void processNotification(String technicalId, Notification notification) {
        // Validation: referenced Task must exist - already checked in POST handler
        // Processing: simulate sending notification, e.g. logging
        log.info("Notification {} sent for Task {}: {}", technicalId, notification.getTaskTechnicalId(), notification.getMessage());
        // Mark notification as sent by setting sentAt if not already set
        if (notification.getSentAt() == null || notification.getSentAt().isBlank()) {
            notification.setSentAt(java.time.Instant.now().toString());
        }
    }
}