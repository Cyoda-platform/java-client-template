package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.FetchJob;
import com.java_template.application.entity.Subscriber;
import com.java_template.application.entity.Notification;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Caches and ID counters for FetchJob
    private final ConcurrentHashMap<String, FetchJob> fetchJobCache = new ConcurrentHashMap<>();
    private final AtomicLong fetchJobIdCounter = new AtomicLong(1);

    // Caches and ID counters for Subscriber
    private final ConcurrentHashMap<String, Subscriber> subscriberCache = new ConcurrentHashMap<>();
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    // Caches and ID counters for Notification
    private final ConcurrentHashMap<String, Notification> notificationCache = new ConcurrentHashMap<>();
    private final AtomicLong notificationIdCounter = new AtomicLong(1);

    // -------------------- FetchJob APIs --------------------

    @PostMapping("/fetch-jobs")
    public ResponseEntity<?> createFetchJob(@RequestBody Map<String, String> body) {
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
        String newId = String.valueOf(fetchJobIdCounter.getAndIncrement());
        FetchJob fetchJob = new FetchJob();
        fetchJob.setId(newId);
        fetchJob.setTechnicalId(UUID.randomUUID());
        fetchJob.setScheduledDate(scheduledDate);
        fetchJob.setStatus(FetchJob.StatusEnum.PENDING);
        fetchJob.setResultSummary("");
        fetchJobCache.put(newId, fetchJob);
        log.info("Created FetchJob with ID: {}", newId);

        processFetchJob(fetchJob);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", fetchJob.getId(), "status", fetchJob.getStatus().name()));
    }

    @GetMapping("/fetch-jobs/{id}")
    public ResponseEntity<?> getFetchJob(@PathVariable String id) {
        FetchJob fetchJob = fetchJobCache.get(id);
        if (fetchJob == null) {
            log.error("FetchJob not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "FetchJob not found"));
        }
        Map<String, Object> response = new HashMap<>();
        response.put("id", fetchJob.getId());
        response.put("status", fetchJob.getStatus().name());
        response.put("resultSummary", fetchJob.getResultSummary());
        return ResponseEntity.ok(response);
    }

    // -------------------- Subscriber APIs --------------------

    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            log.error("Missing or blank email");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "email is required and cannot be blank"));
        }
        String newId = String.valueOf(subscriberIdCounter.getAndIncrement());
        Subscriber subscriber = new Subscriber();
        subscriber.setId(newId);
        subscriber.setTechnicalId(UUID.randomUUID());
        subscriber.setEmail(email);
        subscriber.setStatus(Subscriber.StatusEnum.ACTIVE);
        subscriberCache.put(newId, subscriber);
        log.info("Created Subscriber with ID: {}", newId);

        processSubscriber(subscriber);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", subscriber.getId(), "status", subscriber.getStatus().name()));
    }

    @GetMapping("/subscribers/{id}")
    public ResponseEntity<?> getSubscriber(@PathVariable String id) {
        Subscriber subscriber = subscriberCache.get(id);
        if (subscriber == null) {
            log.error("Subscriber not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Subscriber not found"));
        }
        Map<String, Object> response = new HashMap<>();
        response.put("id", subscriber.getId());
        response.put("email", subscriber.getEmail());
        response.put("status", subscriber.getStatus().name());
        return ResponseEntity.ok(response);
    }

    // -------------------- Notification APIs --------------------

    @GetMapping("/notifications/{subscriberId}")
    public ResponseEntity<?> getNotificationsBySubscriber(@PathVariable String subscriberId) {
        if (!subscriberCache.containsKey(subscriberId)) {
            log.error("Subscriber not found with ID: {}", subscriberId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Subscriber not found"));
        }
        List<Map<String, Object>> notificationsList = new ArrayList<>();
        for (Notification notification : notificationCache.values()) {
            if (notification.getSubscriberId() != null && notification.getSubscriberId().equals(subscriberId)) {
                Map<String, Object> notifMap = new HashMap<>();
                notifMap.put("id", notification.getId());
                notifMap.put("jobId", notification.getJobId());
                notifMap.put("status", notification.getStatus().name());
                notifMap.put("sentAt", notification.getSentAt() != null ? notification.getSentAt().toString() : null);
                notificationsList.add(notifMap);
            }
        }
        return ResponseEntity.ok(notificationsList);
    }

    // -------------------- Process Methods --------------------

    private void processFetchJob(FetchJob fetchJob) {
        log.info("Processing FetchJob with ID: {}", fetchJob.getId());
        fetchJob.setStatus(FetchJob.StatusEnum.PROCESSING);

        // Simulate fetching NBA scores from external API for scheduledDate
        // For prototype, just simulate success and save dummy data
        try {
            Thread.sleep(100); // simulate delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // In real implementation, fetch from external API and save data immutably

        // Update status and resultSummary
        fetchJob.setStatus(FetchJob.StatusEnum.COMPLETED);
        fetchJob.setResultSummary("Simulated fetch success for date " + fetchJob.getScheduledDate());

        // Save updated job
        fetchJobCache.put(fetchJob.getId(), fetchJob);

        // Trigger notifications for all active subscribers
        for (Subscriber subscriber : subscriberCache.values()) {
            if (subscriber.getStatus() == Subscriber.StatusEnum.ACTIVE) {
                String notifId = String.valueOf(notificationIdCounter.getAndIncrement());
                Notification notification = new Notification();
                notification.setId(notifId);
                notification.setTechnicalId(UUID.randomUUID());
                notification.setSubscriberId(subscriber.getId());
                notification.setJobId(fetchJob.getId());
                notification.setStatus(Notification.StatusEnum.PENDING);
                notification.setSentAt(null);
                notificationCache.put(notifId, notification);

                processNotification(notification);
            }
        }
    }

    private void processSubscriber(Subscriber subscriber) {
        log.info("Processing Subscriber with ID: {}", subscriber.getId());
        // For prototype, no additional logic required on creation
    }

    private void processNotification(Notification notification) {
        log.info("Processing Notification with ID: {}", notification.getId());

        // Simulate sending email to subscriber
        try {
            Thread.sleep(50); // simulate delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate success sending email
        notification.setStatus(Notification.StatusEnum.SENT);
        notification.setSentAt(OffsetDateTime.now());

        // Update notification cache
        notificationCache.put(notification.getId(), notification);

        log.info("Notification sent to subscriberId: {} for jobId: {}", notification.getSubscriberId(), notification.getJobId());
    }
}