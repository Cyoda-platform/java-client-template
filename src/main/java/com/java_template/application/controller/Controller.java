package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.FetchJob;
import com.java_template.application.entity.Notification;
import com.java_template.application.entity.Subscriber;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entities")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    // -------------------- FetchJob APIs --------------------

    @PostMapping("/fetch-jobs")
    public ResponseEntity<?> createFetchJob(@RequestBody Map<String, String> body) throws ExecutionException, InterruptedException {
        String scheduledDateStr = body.get("scheduledDate");
        if (scheduledDateStr == null || scheduledDateStr.isBlank()) {
            log.error("Missing or blank scheduledDate");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "scheduledDate is required and cannot be blank"));
        }
        LocalDate scheduledDate;
        try {
            scheduledDate = LocalDate.parse(scheduledDateStr);
        } catch (DateTimeParseException e) {
            log.error("Invalid scheduledDate format: {}", scheduledDateStr);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "scheduledDate must be in ISO format YYYY-MM-DD"));
        }

        FetchJob fetchJob = new FetchJob();
        fetchJob.setScheduledDate(scheduledDate);
        fetchJob.setStatus(FetchJob.StatusEnum.PENDING);
        fetchJob.setResultSummary("");

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "FetchJob",
                ENTITY_VERSION,
                fetchJob
        );
        UUID technicalId = idFuture.get();

        // Retrieve the saved entity to get the assigned technicalId
        CompletableFuture<ObjectNode> savedJobFuture = entityService.getItem("FetchJob", ENTITY_VERSION, technicalId);
        ObjectNode savedJobNode = savedJobFuture.get();

        // Set fields from ObjectNode to fetchJob to keep local copy
        fetchJob.setTechnicalId(UUID.fromString(savedJobNode.get("technicalId").asText()));
        // Assign an id from technicalId for local use (string), since original id is missing
        fetchJob.setId(technicalId.toString());

        log.info("Created FetchJob with technicalId: {}", technicalId);

        processFetchJob(fetchJob);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", fetchJob.getId(), "status", fetchJob.getStatus().name()));
    }

    @GetMapping("/fetch-jobs/{id}")
    public ResponseEntity<?> getFetchJob(@PathVariable String id) throws ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid FetchJob id format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid FetchJob id format"));
        }

        CompletableFuture<ObjectNode> fetchJobFuture = entityService.getItem("FetchJob", ENTITY_VERSION, technicalId);
        ObjectNode fetchJobNode = fetchJobFuture.get();
        if (fetchJobNode == null || fetchJobNode.isEmpty()) {
            log.error("FetchJob not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "FetchJob not found"));
        }
        Map<String, Object> response = new HashMap<>();
        response.put("id", fetchJobNode.get("technicalId").asText());
        response.put("status", fetchJobNode.get("status").asText());
        response.put("resultSummary", fetchJobNode.get("resultSummary").asText());

        return ResponseEntity.ok(response);
    }

    // -------------------- Subscriber APIs --------------------

    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@RequestBody Map<String, String> body) throws ExecutionException, InterruptedException {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            log.error("Missing or blank email");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "email is required and cannot be blank"));
        }

        Subscriber subscriber = new Subscriber();
        subscriber.setEmail(email);
        subscriber.setStatus(Subscriber.StatusEnum.ACTIVE);

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "Subscriber",
                ENTITY_VERSION,
                subscriber
        );
        UUID technicalId = idFuture.get();

        CompletableFuture<ObjectNode> savedSubscriberFuture = entityService.getItem("Subscriber", ENTITY_VERSION, technicalId);
        ObjectNode savedSubscriberNode = savedSubscriberFuture.get();

        subscriber.setTechnicalId(UUID.fromString(savedSubscriberNode.get("technicalId").asText()));
        subscriber.setId(technicalId.toString());

        log.info("Created Subscriber with technicalId: {}", technicalId);

        processSubscriber(subscriber);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", subscriber.getId(), "status", subscriber.getStatus().name()));
    }

    @GetMapping("/subscribers/{id}")
    public ResponseEntity<?> getSubscriber(@PathVariable String id) throws ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid Subscriber id format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid Subscriber id format"));
        }

        CompletableFuture<ObjectNode> subscriberFuture = entityService.getItem("Subscriber", ENTITY_VERSION, technicalId);
        ObjectNode subscriberNode = subscriberFuture.get();
        if (subscriberNode == null || subscriberNode.isEmpty()) {
            log.error("Subscriber not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Subscriber not found"));
        }
        Map<String, Object> response = new HashMap<>();
        response.put("id", subscriberNode.get("technicalId").asText());
        response.put("email", subscriberNode.get("email").asText());
        response.put("status", subscriberNode.get("status").asText());

        return ResponseEntity.ok(response);
    }

    // -------------------- Notification APIs --------------------

    @GetMapping("/notifications/{subscriberId}")
    public ResponseEntity<?> getNotificationsBySubscriber(@PathVariable String subscriberId) throws ExecutionException, InterruptedException {
        UUID subscriberTechnicalId;
        try {
            subscriberTechnicalId = UUID.fromString(subscriberId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid Subscriber id format: {}", subscriberId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid Subscriber id format"));
        }

        // Verify subscriber exists
        CompletableFuture<ObjectNode> subscriberFuture = entityService.getItem("Subscriber", ENTITY_VERSION, subscriberTechnicalId);
        ObjectNode subscriberNode = subscriberFuture.get();
        if (subscriberNode == null || subscriberNode.isEmpty()) {
            log.error("Subscriber not found with id: {}", subscriberId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Subscriber not found"));
        }

        Condition cond = Condition.of("$.subscriberId", "EQUALS", subscriberId);
        SearchConditionRequest condition = SearchConditionRequest.group("AND", cond);

        CompletableFuture<ArrayNode> notificationsFuture = entityService.getItemsByCondition("Notification", ENTITY_VERSION, condition);
        ArrayNode notificationsArray = notificationsFuture.get();

        List<Map<String, Object>> notificationsList = new ArrayList<>();
        for (int i = 0; i < notificationsArray.size(); i++) {
            ObjectNode notificationNode = (ObjectNode) notificationsArray.get(i);
            Map<String, Object> notifMap = new HashMap<>();
            notifMap.put("id", notificationNode.get("technicalId").asText());
            notifMap.put("jobId", notificationNode.hasNonNull("jobId") ? notificationNode.get("jobId").asText() : null);
            notifMap.put("status", notificationNode.get("status").asText());
            notifMap.put("sentAt", notificationNode.hasNonNull("sentAt") ? notificationNode.get("sentAt").asText() : null);
            notificationsList.add(notifMap);
        }

        return ResponseEntity.ok(notificationsList);
    }

    // -------------------- Process Methods --------------------

    private void processFetchJob(FetchJob fetchJob) throws ExecutionException, InterruptedException {
        log.info("Processing FetchJob with technicalId: {}", fetchJob.getTechnicalId());
        fetchJob.setStatus(FetchJob.StatusEnum.PROCESSING);

        // Update status to PROCESSING as new entity version
        entityService.addItem("FetchJob", ENTITY_VERSION, fetchJob).get();

        // Simulate fetching NBA scores from external API for scheduledDate
        try {
            Thread.sleep(100); // simulate delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Update status and resultSummary - create new version
        FetchJob updatedFetchJob = new FetchJob();
        updatedFetchJob.setScheduledDate(fetchJob.getScheduledDate());
        updatedFetchJob.setStatus(FetchJob.StatusEnum.COMPLETED);
        updatedFetchJob.setResultSummary("Simulated fetch success for date " + fetchJob.getScheduledDate());

        entityService.addItem("FetchJob", ENTITY_VERSION, updatedFetchJob).get();

        // Trigger notifications for all active subscribers
        CompletableFuture<ArrayNode> subscribersFuture = entityService.getItemsByCondition("Subscriber", ENTITY_VERSION,
                SearchConditionRequest.group("AND",
                        Condition.of("$.status", "EQUALS", "ACTIVE")));

        ArrayNode subscribersArray = subscribersFuture.get();

        for (int i = 0; i < subscribersArray.size(); i++) {
            ObjectNode subscriberNode = (ObjectNode) subscribersArray.get(i);
            Subscriber subscriber = new Subscriber();
            subscriber.setTechnicalId(UUID.fromString(subscriberNode.get("technicalId").asText()));
            subscriber.setId(subscriberNode.get("technicalId").asText());
            subscriber.setEmail(subscriberNode.hasNonNull("email") ? subscriberNode.get("email").asText() : null);
            subscriber.setStatus(Subscriber.StatusEnum.valueOf(subscriberNode.get("status").asText()));

            Notification notification = new Notification();
            notification.setSubscriberId(subscriber.getId());
            notification.setJobId(fetchJob.getTechnicalId().toString());
            notification.setStatus(Notification.StatusEnum.PENDING);
            notification.setSentAt(null);

            UUID notifTechnicalId = entityService.addItem("Notification", ENTITY_VERSION, notification).get();
            notification.setTechnicalId(notifTechnicalId);
            notification.setId(notifTechnicalId.toString());

            processNotification(notification);
        }
    }

    private void processSubscriber(Subscriber subscriber) {
        log.info("Processing Subscriber with id: {}", subscriber.getId());
        // No additional logic on creation
    }

    private void processNotification(Notification notification) {
        log.info("Processing Notification with subscriberId: {}, for jobId: {}", notification.getSubscriberId(), notification.getJobId());

        try {
            Thread.sleep(50); // simulate delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Notification updatedNotification = new Notification();
        updatedNotification.setSubscriberId(notification.getSubscriberId());
        updatedNotification.setJobId(notification.getJobId());
        updatedNotification.setStatus(Notification.StatusEnum.SENT);
        updatedNotification.setSentAt(OffsetDateTime.now());

        try {
            entityService.addItem("Notification", ENTITY_VERSION, updatedNotification).get();
        } catch (Exception e) {
            log.error("Failed to update Notification status to SENT: {}", e.getMessage());
        }

        log.info("Notification sent to subscriberId: {} for jobId: {}", notification.getSubscriberId(), notification.getJobId());
    }
}