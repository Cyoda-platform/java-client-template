package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.CatFactJob;
import com.java_template.application.entity.Subscriber;
import com.java_template.application.entity.Interaction;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, CatFactJob> catFactJobCache = new ConcurrentHashMap<>();
    private final AtomicLong catFactJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Subscriber> subscriberCache = new ConcurrentHashMap<>();
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Interaction> interactionCache = new ConcurrentHashMap<>();
    private final AtomicLong interactionIdCounter = new AtomicLong(1);

    // --- CatFactJob Endpoints ---

    @PostMapping("/catFactJobs")
    public ResponseEntity<?> createCatFactJob(@RequestBody CatFactJob catFactJob) {
        if (catFactJob == null || catFactJob.getScheduledAt() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("scheduledAt is required");
        }
        String id = "catFactJob-" + catFactJobIdCounter.getAndIncrement();
        catFactJob.setId(id);
        catFactJob.setStatus("PENDING");
        catFactJobCache.put(id, catFactJob);

        log.info("Created CatFactJob with ID: {}", id);

        try {
            processCatFactJob(catFactJob);
        } catch (Exception e) {
            log.error("Error processing CatFactJob ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing failed");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(catFactJob);
    }

    @GetMapping("/catFactJobs/{id}")
    public ResponseEntity<?> getCatFactJob(@PathVariable String id) {
        CatFactJob job = catFactJobCache.get(id);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("CatFactJob not found");
        }
        return ResponseEntity.ok(job);
    }

    // --- Subscriber Endpoints ---

    @PostMapping("/subscribers")
    public ResponseEntity<?> createSubscriber(@RequestBody Subscriber subscriber) {
        if (subscriber == null || subscriber.getEmail() == null || subscriber.getEmail().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Valid email is required");
        }

        String id = "subscriber-" + subscriberIdCounter.getAndIncrement();
        subscriber.setId(id);
        subscriber.setSubscribedAt(java.time.LocalDateTime.now());
        subscriber.setStatus("ACTIVE");

        subscriberCache.put(id, subscriber);

        log.info("Created Subscriber with ID: {}", id);

        try {
            processSubscriber(subscriber);
        } catch (Exception e) {
            log.error("Error processing Subscriber ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing failed");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(subscriber);
    }

    @GetMapping("/subscribers/{id}")
    public ResponseEntity<?> getSubscriber(@PathVariable String id) {
        Subscriber subscriber = subscriberCache.get(id);
        if (subscriber == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
        }
        return ResponseEntity.ok(subscriber);
    }

    @GetMapping("/subscribers/count")
    public ResponseEntity<?> getActiveSubscriberCount() {
        long count = subscriberCache.values().stream().filter(s -> "ACTIVE".equalsIgnoreCase(s.getStatus())).count();
        Map<String, Long> response = new HashMap<>();
        response.put("activeSubscribers", count);
        return ResponseEntity.ok(response);
    }

    // --- Interaction Endpoints ---

    @PostMapping("/interactions")
    public ResponseEntity<?> createInteraction(@RequestBody Interaction interaction) {
        if (interaction == null ||
            interaction.getSubscriberId() == null || interaction.getSubscriberId().isBlank() ||
            interaction.getCatFactJobId() == null || interaction.getCatFactJobId().isBlank() ||
            interaction.getInteractionType() == null || interaction.getInteractionType().isBlank() ||
            interaction.getInteractedAt() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("All interaction fields are required");
        }

        // Validate referenced entities exist
        if (!subscriberCache.containsKey(interaction.getSubscriberId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Referenced Subscriber does not exist");
        }
        if (!catFactJobCache.containsKey(interaction.getCatFactJobId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Referenced CatFactJob does not exist");
        }

        String id = "interaction-" + interactionIdCounter.getAndIncrement();
        interaction.setId(id);
        interaction.setStatus("RECORDED");

        interactionCache.put(id, interaction);

        log.info("Created Interaction with ID: {}", id);

        try {
            processInteraction(interaction);
        } catch (Exception e) {
            log.error("Error processing Interaction ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing failed");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(interaction);
    }

    @GetMapping("/interactions/{id}")
    public ResponseEntity<?> getInteraction(@PathVariable String id) {
        Interaction interaction = interactionCache.get(id);
        if (interaction == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Interaction not found");
        }
        return ResponseEntity.ok(interaction);
    }

    @GetMapping("/interactions/count")
    public ResponseEntity<?> getInteractionCounts() {
        long emailOpens = interactionCache.values().stream()
                .filter(i -> "EMAIL_OPEN".equalsIgnoreCase(i.getInteractionType()))
                .count();
        long linkClicks = interactionCache.values().stream()
                .filter(i -> "LINK_CLICK".equalsIgnoreCase(i.getInteractionType()))
                .count();
        Map<String, Long> response = new HashMap<>();
        response.put("emailOpens", emailOpens);
        response.put("linkClicks", linkClicks);
        return ResponseEntity.ok(response);
    }

    // --- Process Methods ---

    private void processCatFactJob(CatFactJob catFactJob) {
        log.info("Processing CatFactJob with ID: {}", catFactJob.getId());

        // Fetch a new cat fact from the Cat Fact API
        try {
            // Simulate API call - replace with actual HTTP call in real implementation
            String fetchedFact = "Cats sleep 70% of their lives."; // placeholder fact
            catFactJob.setCatFactText(fetchedFact);
            catFactJob.setStatus("PROCESSING");
            log.info("Fetched cat fact: {}", fetchedFact);

            // Send emails to all active subscribers
            subscriberCache.values().stream()
                .filter(s -> "ACTIVE".equalsIgnoreCase(s.getStatus()))
                .forEach(s -> log.info("Sending cat fact email to subscriber: {}", s.getEmail()));

            catFactJob.setStatus("COMPLETED");
            log.info("CatFactJob {} completed successfully", catFactJob.getId());
        } catch (Exception e) {
            catFactJob.setStatus("FAILED");
            log.error("Failed to process CatFactJob {}: {}", catFactJob.getId(), e.getMessage());
        }
    }

    private void processSubscriber(Subscriber subscriber) {
        log.info("Processing Subscriber with ID: {}", subscriber.getId());

        // Validate email format (simple check)
        if (!subscriber.getEmail().contains("@")) {
            log.error("Invalid email format for subscriber {}", subscriber.getId());
            throw new IllegalArgumentException("Invalid email format");
        }

        // Here you could send a confirmation email
        log.info("Subscriber {} validated and saved", subscriber.getEmail());
    }

    private void processInteraction(Interaction interaction) {
        log.info("Processing Interaction with ID: {}", interaction.getId());

        // Log the interaction details, could be extended to update metrics or trigger workflows
        log.info("Subscriber {} had interaction {} on CatFactJob {} at {}",
                interaction.getSubscriberId(),
                interaction.getInteractionType(),
                interaction.getCatFactJobId(),
                interaction.getInteractedAt());
    }
}