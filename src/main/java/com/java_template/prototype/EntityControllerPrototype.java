package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.WeeklyCatFactJob;
import com.java_template.application.entity.Subscriber;
import com.java_template.application.entity.EmailInteractionReport;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, WeeklyCatFactJob> weeklyCatFactJobCache = new ConcurrentHashMap<>();
    private final AtomicLong weeklyCatFactJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Subscriber> subscriberCache = new ConcurrentHashMap<>();
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, EmailInteractionReport> emailInteractionReportCache = new ConcurrentHashMap<>();
    private final AtomicLong emailInteractionReportIdCounter = new AtomicLong(1);

    // ---------------- WeeklyCatFactJob Endpoints ----------------

    @PostMapping("/weeklyCatFactJobs")
    public ResponseEntity<?> createWeeklyCatFactJob(@RequestBody WeeklyCatFactJob job) {
        if (job == null || job.getScheduledAt() == null) {
            log.error("Invalid WeeklyCatFactJob creation request: scheduledAt required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("scheduledAt is required");
        }
        String id = String.valueOf(weeklyCatFactJobIdCounter.getAndIncrement());
        job.setId(id);
        job.setStatus("PENDING");
        weeklyCatFactJobCache.put(id, job);
        log.info("Created WeeklyCatFactJob with ID: {}", id);
        processWeeklyCatFactJob(job);
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @GetMapping("/weeklyCatFactJobs/{id}")
    public ResponseEntity<?> getWeeklyCatFactJob(@PathVariable String id) {
        WeeklyCatFactJob job = weeklyCatFactJobCache.get(id);
        if (job == null) {
            log.error("WeeklyCatFactJob not found for ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("WeeklyCatFactJob not found");
        }
        return ResponseEntity.ok(job);
    }

    private void processWeeklyCatFactJob(WeeklyCatFactJob job) {
        log.info("Processing WeeklyCatFactJob with ID: {}", job.getId());
        try {
            job.setStatus("PROCESSING");
            // 1. Fetch cat fact from Cat Fact API (simulate here)
            String catFact = fetchCatFact();
            log.info("Fetched cat fact: {}", catFact);

            // 2. Retrieve all ACTIVE subscribers
            List<Subscriber> activeSubscribers = new ArrayList<>();
            for (Subscriber sub : subscriberCache.values()) {
                if ("ACTIVE".equals(sub.getStatus())) {
                    activeSubscribers.add(sub);
                }
            }
            log.info("Found {} active subscribers", activeSubscribers.size());

            // 3. Send cat fact email to each subscriber and record DELIVERY event
            for (Subscriber sub : activeSubscribers) {
                sendEmail(sub.getEmail(), catFact);
                EmailInteractionReport report = new EmailInteractionReport();
                String reportId = String.valueOf(emailInteractionReportIdCounter.getAndIncrement());
                report.setId(reportId);
                report.setSubscriberId(sub.getId());
                report.setEventType("DELIVERY");
                report.setEventTimestamp(new Date());
                report.setStatus("RECORDED");
                emailInteractionReportCache.put(reportId, report);
                log.info("Recorded email DELIVERY event for subscriber ID: {}", sub.getId());
            }
            job.setStatus("COMPLETED");
            weeklyCatFactJobCache.put(job.getId(), job);
            log.info("Completed WeeklyCatFactJob with ID: {}", job.getId());
        } catch (Exception e) {
            job.setStatus("FAILED");
            weeklyCatFactJobCache.put(job.getId(), job);
            log.error("Failed processing WeeklyCatFactJob with ID: {}", job.getId(), e);
        }
    }

    // ---------------- Subscriber Endpoints ----------------

    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@RequestBody Subscriber subscriber) {
        if (subscriber == null || subscriber.getEmail() == null || subscriber.getEmail().isBlank()) {
            log.error("Invalid Subscriber creation request: email required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("email is required");
        }
        // Check uniqueness of email
        for (Subscriber existing : subscriberCache.values()) {
            if (existing.getEmail().equalsIgnoreCase(subscriber.getEmail()) && "ACTIVE".equals(existing.getStatus())) {
                log.error("Subscriber with email {} already exists", subscriber.getEmail());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Subscriber email already exists");
            }
        }
        String id = String.valueOf(subscriberIdCounter.getAndIncrement());
        subscriber.setId(id);
        subscriber.setStatus("ACTIVE");
        subscriberCache.put(id, subscriber);
        log.info("Created Subscriber with ID: {}", id);
        processSubscriber(subscriber);
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriber);
    }

    @GetMapping("/subscribers/{id}")
    public ResponseEntity<?> getSubscriber(@PathVariable String id) {
        Subscriber subscriber = subscriberCache.get(id);
        if (subscriber == null) {
            log.error("Subscriber not found for ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
        }
        return ResponseEntity.ok(subscriber);
    }

    private void processSubscriber(Subscriber subscriber) {
        log.info("Processing Subscriber with ID: {}", subscriber.getId());
        // Validate email format (simple regex)
        if (!subscriber.getEmail().matches("^[\\w-.]+@[\\w-]+\\.[a-z]{2,}$")) {
            log.error("Invalid email format for subscriber ID: {}", subscriber.getId());
        }
        // No unsubscribe or deletion processing as per requirements
    }

    // ---------------- EmailInteractionReport Endpoints ----------------

    @GetMapping("/emailInteractionReports")
    public ResponseEntity<?> getAllEmailInteractionReports() {
        return ResponseEntity.ok(new ArrayList<>(emailInteractionReportCache.values()));
    }

    @GetMapping("/emailInteractionReports/{id}")
    public ResponseEntity<?> getEmailInteractionReport(@PathVariable String id) {
        EmailInteractionReport report = emailInteractionReportCache.get(id);
        if (report == null) {
            log.error("EmailInteractionReport not found for ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("EmailInteractionReport not found");
        }
        return ResponseEntity.ok(report);
    }

    private void processEmailInteractionReport(EmailInteractionReport report) {
        log.info("Processing EmailInteractionReport with ID: {}", report.getId());
        // No additional processing required for reports per requirements
    }

    // ---------------- Helper methods ----------------

    private String fetchCatFact() {
        // Simulate fetching cat fact from external API
        return "Cats have five toes on their front paws, but only four toes on their back paws.";
    }

    private void sendEmail(String email, String content) {
        // Simulate sending email
        log.info("Sending email to {} with content: {}", email, content);
    }
}