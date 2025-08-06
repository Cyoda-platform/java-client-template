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
import com.java_template.application.entity.CatFactInteraction;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, WeeklyCatFactJob> weeklyCatFactJobCache = new ConcurrentHashMap<>();
    private final AtomicLong weeklyCatFactJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Subscriber> subscriberCache = new ConcurrentHashMap<>();
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, CatFactInteraction> catFactInteractionCache = new ConcurrentHashMap<>();
    private final AtomicLong catFactInteractionIdCounter = new AtomicLong(1);

    private final RestTemplate restTemplate = new RestTemplate();

    // POST /prototype/weekly-cat-fact-jobs
    @PostMapping("/weekly-cat-fact-jobs")
    public ResponseEntity<Map<String, String>> createWeeklyCatFactJob(@RequestBody Map<String, String> payload) {
        String subscriberEmail = payload.get("subscriberEmail");
        if (subscriberEmail == null || subscriberEmail.isBlank()) {
            log.error("Subscriber email is missing or blank");
            return ResponseEntity.badRequest().build();
        }
        String technicalId = String.valueOf(weeklyCatFactJobIdCounter.getAndIncrement());
        WeeklyCatFactJob job = new WeeklyCatFactJob();
        job.setSubscriberEmail(subscriberEmail);
        job.setStatus("PENDING");
        job.setScheduledAt(new Date().toInstant().toString());

        weeklyCatFactJobCache.put(technicalId, job);
        try {
            processWeeklyCatFactJob(technicalId, job);
            log.info("WeeklyCatFactJob created and processed for technicalId: {}", technicalId);
            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error processing WeeklyCatFactJob for id {}: {}", technicalId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /prototype/weekly-cat-fact-jobs/{id}
    @GetMapping("/weekly-cat-fact-jobs/{id}")
    public ResponseEntity<WeeklyCatFactJob> getWeeklyCatFactJob(@PathVariable String id) {
        WeeklyCatFactJob job = weeklyCatFactJobCache.get(id);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(job);
    }

    // POST /prototype/subscribers
    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        if (email == null || email.isBlank()) {
            log.error("Subscriber email is missing or blank");
            return ResponseEntity.badRequest().build();
        }
        // Check if already subscribed
        boolean exists = subscriberCache.values().stream()
                .anyMatch(s -> s.getEmail().equalsIgnoreCase(email));
        if (exists) {
            log.info("Subscriber with email {} already exists", email);
            Map<String, String> response = new HashMap<>();
            response.put("technicalId", "existing");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        String technicalId = String.valueOf(subscriberIdCounter.getAndIncrement());
        Subscriber subscriber = new Subscriber();
        subscriber.setEmail(email);
        subscriber.setSubscribedAt(new Date().toInstant().toString());

        subscriberCache.put(technicalId, subscriber);
        try {
            processSubscriber(technicalId, subscriber);
            log.info("Subscriber created and processed for technicalId: {}", technicalId);
            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error processing Subscriber for id {}: {}", technicalId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /prototype/subscribers/{id}
    @GetMapping("/subscribers/{id}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable String id) {
        Subscriber subscriber = subscriberCache.get(id);
        if (subscriber == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(subscriber);
    }

    // POST /prototype/cat-fact-interactions
    @PostMapping("/cat-fact-interactions")
    public ResponseEntity<Map<String, String>> createCatFactInteraction(@RequestBody Map<String, String> payload) {
        String subscriberEmail = payload.get("subscriberEmail");
        String catFactId = payload.get("catFactId");
        String interactionType = payload.get("interactionType");

        if (subscriberEmail == null || subscriberEmail.isBlank() ||
                catFactId == null || catFactId.isBlank() ||
                interactionType == null || interactionType.isBlank()) {
            log.error("Missing required fields for CatFactInteraction");
            return ResponseEntity.badRequest().build();
        }
        String technicalId = String.valueOf(catFactInteractionIdCounter.getAndIncrement());
        CatFactInteraction interaction = new CatFactInteraction();
        interaction.setSubscriberEmail(subscriberEmail);
        interaction.setCatFactId(catFactId);
        interaction.setInteractionType(interactionType);
        interaction.setInteractionTimestamp(new Date().toInstant().toString());

        catFactInteractionCache.put(technicalId, interaction);
        try {
            processCatFactInteraction(technicalId, interaction);
            log.info("CatFactInteraction created and processed for technicalId: {}", technicalId);
            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error processing CatFactInteraction for id {}: {}", technicalId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /prototype/cat-fact-interactions/{id}
    @GetMapping("/cat-fact-interactions/{id}")
    public ResponseEntity<CatFactInteraction> getCatFactInteraction(@PathVariable String id) {
        CatFactInteraction interaction = catFactInteractionCache.get(id);
        if (interaction == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(interaction);
    }

    // Business logic methods

    private void processWeeklyCatFactJob(String technicalId, WeeklyCatFactJob job) {
        // Validate subscriber email
        if (job.getSubscriberEmail() == null || job.getSubscriberEmail().isBlank()) {
            job.setStatus("FAILED");
            log.error("WeeklyCatFactJob {} validation failed: subscriberEmail blank", technicalId);
            return;
        }

        // Check if subscriber exists
        boolean subscriberExists = subscriberCache.values().stream()
                .anyMatch(s -> s.getEmail().equalsIgnoreCase(job.getSubscriberEmail()));

        // If new subscriber, add them
        if (!subscriberExists) {
            String subscriberId = String.valueOf(subscriberIdCounter.getAndIncrement());
            Subscriber subscriber = new Subscriber();
            subscriber.setEmail(job.getSubscriberEmail());
            subscriber.setSubscribedAt(new Date().toInstant().toString());
            subscriberCache.put(subscriberId, subscriber);
            processSubscriber(subscriberId, subscriber);
            log.info("New subscriber created during WeeklyCatFactJob processing with id {}", subscriberId);
        }

        // Call Cat Fact API
        try {
            String apiUrl = "https://catfact.ninja/fact";
            Map response = restTemplate.getForObject(apiUrl, Map.class);
            if (response != null && response.containsKey("fact")) {
                String fact = response.get("fact").toString();
                job.setCatFact(fact);
                job.setStatus("PROCESSING");
                // Send email to all subscribers (simulate)
                for (Subscriber subscriber : subscriberCache.values()) {
                    log.info("Sending cat fact email to {}", subscriber.getEmail());
                    // Here would be email sending logic
                }
                job.setStatus("COMPLETED");
                log.info("WeeklyCatFactJob {} completed successfully", technicalId);
            } else {
                job.setStatus("FAILED");
                log.error("Cat Fact API did not return a fact");
            }
        } catch (Exception e) {
            job.setStatus("FAILED");
            log.error("Error calling Cat Fact API: {}", e.getMessage());
        }
    }

    private void processSubscriber(String technicalId, Subscriber subscriber) {
        // Validate email format simple check
        if (subscriber.getEmail() == null || subscriber.getEmail().isBlank() || !subscriber.getEmail().contains("@")) {
            log.error("Subscriber {} has invalid email format", technicalId);
            return;
        }
        // Could add confirmation email logic here
        log.info("Subscriber {} processed successfully", technicalId);
    }

    private void processCatFactInteraction(String technicalId, CatFactInteraction interaction) {
        // Record interaction for reporting, no complex logic here
        log.info("CatFactInteraction {} recorded: {} interaction by {}", technicalId,
                interaction.getInteractionType(), interaction.getSubscriberEmail());
    }
}