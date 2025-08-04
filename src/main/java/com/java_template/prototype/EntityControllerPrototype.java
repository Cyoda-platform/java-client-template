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
import com.java_template.application.entity.CatFact;
import java.time.LocalDateTime;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, WeeklyCatFactJob> weeklyCatFactJobCache = new ConcurrentHashMap<>();
    private final AtomicLong weeklyCatFactJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Subscriber> subscriberCache = new ConcurrentHashMap<>();
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, CatFact> catFactCache = new ConcurrentHashMap<>();
    private final AtomicLong catFactIdCounter = new AtomicLong(1);

    // POST /prototype/weeklyCatFactJob - create WeeklyCatFactJob and trigger processing
    @PostMapping("/weeklyCatFactJob")
    public ResponseEntity<Map<String, String>> createWeeklyCatFactJob() {
        String technicalId = String.valueOf(weeklyCatFactJobIdCounter.getAndIncrement());
        WeeklyCatFactJob job = new WeeklyCatFactJob();
        job.setStatus("PENDING");
        job.setCatFact("");
        job.setSubscriberCount(0);
        job.setEmailSentDate(null);

        weeklyCatFactJobCache.put(technicalId, job);
        log.info("Created WeeklyCatFactJob with id {}", technicalId);

        try {
            processWeeklyCatFactJob(technicalId, job);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
        } catch (Exception e) {
            log.error("Error processing WeeklyCatFactJob id {}: {}", technicalId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /prototype/weeklyCatFactJob/{id} - retrieve WeeklyCatFactJob by id
    @GetMapping("/weeklyCatFactJob/{id}")
    public ResponseEntity<WeeklyCatFactJob> getWeeklyCatFactJob(@PathVariable String id) {
        WeeklyCatFactJob job = weeklyCatFactJobCache.get(id);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(job);
    }

    // POST /prototype/subscriber - create Subscriber and trigger processing
    @PostMapping("/subscriber")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Check if email already subscribed
        boolean exists = subscriberCache.values().stream()
                .anyMatch(s -> s.getEmail().equalsIgnoreCase(email) && "ACTIVE".equals(s.getStatus()));
        if (exists) {
            log.info("Subscriber with email {} already exists", email);
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        String technicalId = String.valueOf(subscriberIdCounter.getAndIncrement());
        Subscriber subscriber = new Subscriber();
        subscriber.setEmail(email);
        subscriber.setSubscribedDate(LocalDateTime.now());
        subscriber.setStatus("ACTIVE");
        subscriber.setInteractionCount(0);

        subscriberCache.put(technicalId, subscriber);
        log.info("Created Subscriber with id {} and email {}", technicalId, email);

        try {
            processSubscriber(technicalId, subscriber);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
        } catch (Exception e) {
            log.error("Error processing Subscriber id {}: {}", technicalId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /prototype/subscriber/{id} - retrieve Subscriber by id
    @GetMapping("/subscriber/{id}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable String id) {
        Subscriber subscriber = subscriberCache.get(id);
        if (subscriber == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(subscriber);
    }

    // GET /prototype/catFact/{id} - retrieve CatFact by id
    @GetMapping("/catFact/{id}")
    public ResponseEntity<CatFact> getCatFact(@PathVariable String id) {
        CatFact catFact = catFactCache.get(id);
        if (catFact == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(catFact);
    }

    private void processWeeklyCatFactJob(String technicalId, WeeklyCatFactJob job) {
        log.info("Processing WeeklyCatFactJob id {}", technicalId);

        try {
            // Step 2: Data Ingestion - call external Cat Fact API
            RestTemplate restTemplate = new RestTemplate();
            Map<String, Object> response = restTemplate.getForObject("https://catfact.ninja/fact", Map.class);
            if (response == null || !response.containsKey("fact")) {
                throw new RuntimeException("Failed to retrieve cat fact from API");
            }
            String fact = (String) response.get("fact");

            // Create and save CatFact entity
            String catFactId = String.valueOf(catFactIdCounter.getAndIncrement());
            CatFact catFact = new CatFact();
            catFact.setFact(fact);
            catFact.setRetrievedDate(LocalDateTime.now());
            catFactCache.put(catFactId, catFact);
            log.info("Retrieved and saved CatFact id {}: {}", catFactId, fact);

            // Count active subscribers
            long activeSubscribers = subscriberCache.values().stream()
                    .filter(s -> "ACTIVE".equals(s.getStatus()))
                    .count();

            // Compose email content and send emails (simulate)
            log.info("Sending cat fact email to {} active subscribers", activeSubscribers);
            // Here would be email sending logic; simulated by logging

            // Update job status and details
            job.setCatFact(fact);
            job.setSubscriberCount((int) activeSubscribers);
            job.setEmailSentDate(LocalDateTime.now());
            job.setStatus("COMPLETED");

            // Update cache
            weeklyCatFactJobCache.put(technicalId, job);

            log.info("WeeklyCatFactJob id {} completed successfully", technicalId);
        } catch (Exception e) {
            job.setStatus("FAILED");
            weeklyCatFactJobCache.put(technicalId, job);
            log.error("WeeklyCatFactJob id {} failed: {}", technicalId, e.getMessage());
        }
    }

    private void processSubscriber(String technicalId, Subscriber subscriber) {
        log.info("Processing Subscriber id {} email {}", technicalId, subscriber.getEmail());
        // Validation already done in isValid and controller checks
        // Here could be additional processing or integration if needed
        log.info("Subscriber id {} processed successfully", technicalId);
    }
}