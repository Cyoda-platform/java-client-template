package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.GameScoreFetchJob;
import com.java_template.application.entity.NbaGameScore;
import com.java_template.application.entity.Subscriber;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, GameScoreFetchJob> gameScoreFetchJobCache = new ConcurrentHashMap<>();
    private final AtomicLong gameScoreFetchJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, NbaGameScore> nbaGameScoreCache = new ConcurrentHashMap<>();
    private final AtomicLong nbaGameScoreIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Subscriber> subscriberCache = new ConcurrentHashMap<>();
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    // --- GameScoreFetchJob Endpoints ---

    @PostMapping("/gameScoreFetchJob")
    public ResponseEntity<?> createGameScoreFetchJob(@RequestBody GameScoreFetchJob job) {
        if (job == null || job.getScheduledDate() == null || job.getStatus() == null || job.getStatus().isBlank()) {
            log.error("Invalid GameScoreFetchJob creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid GameScoreFetchJob data");
        }
        String id = String.valueOf(gameScoreFetchJobIdCounter.getAndIncrement());
        job.setId(id);
        job.setTechnicalId(UUID.randomUUID());
        job.setCreatedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        job.setUpdatedAt(new java.sql.Timestamp(System.currentTimeMillis()));

        gameScoreFetchJobCache.put(id, job);
        log.info("Created GameScoreFetchJob with ID: {}", id);

        processGameScoreFetchJob(job);

        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @GetMapping("/gameScoreFetchJob/{id}")
    public ResponseEntity<?> getGameScoreFetchJob(@PathVariable String id) {
        GameScoreFetchJob job = gameScoreFetchJobCache.get(id);
        if (job == null) {
            log.error("GameScoreFetchJob not found for ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("GameScoreFetchJob not found");
        }
        return ResponseEntity.ok(job);
    }

    @PutMapping("/gameScoreFetchJob/{id}")
    public ResponseEntity<?> updateGameScoreFetchJob(@PathVariable String id, @RequestBody GameScoreFetchJob job) {
        if (!gameScoreFetchJobCache.containsKey(id)) {
            log.error("GameScoreFetchJob not found for update ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("GameScoreFetchJob not found");
        }
        if (job == null || job.getScheduledDate() == null || job.getStatus() == null || job.getStatus().isBlank()) {
            log.error("Invalid GameScoreFetchJob update request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid GameScoreFetchJob data");
        }
        job.setId(id);
        job.setTechnicalId(gameScoreFetchJobCache.get(id).getTechnicalId());
        job.setUpdatedAt(new java.sql.Timestamp(System.currentTimeMillis()));

        gameScoreFetchJobCache.put(id, job);
        log.info("Updated GameScoreFetchJob with ID: {}", id);

        processGameScoreFetchJob(job);

        return ResponseEntity.ok(job);
    }

    @DeleteMapping("/gameScoreFetchJob/{id}")
    public ResponseEntity<?> deleteGameScoreFetchJob(@PathVariable String id) {
        GameScoreFetchJob removed = gameScoreFetchJobCache.remove(id);
        if (removed == null) {
            log.error("GameScoreFetchJob not found for delete ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("GameScoreFetchJob not found");
        }
        log.info("Deleted GameScoreFetchJob with ID: {}", id);
        return ResponseEntity.ok("Deleted GameScoreFetchJob with ID: " + id);
    }

    private void processGameScoreFetchJob(GameScoreFetchJob job) {
        log.info("Processing GameScoreFetchJob with ID: {}", job.getId());
        // TODO: Implement actual business logic per requirements:
        // - Validate scheduledDate
        // - Fetch NBA game scores from external API
        // - Save/update NbaGameScore entities in cache or database
        // - Update job status
        // - Trigger notifications if necessary
    }

    // --- NbaGameScore Endpoints ---

    @PostMapping("/nbaGameScore")
    public ResponseEntity<?> createNbaGameScore(@RequestBody NbaGameScore gameScore) {
        if (gameScore == null || gameScore.getGameId() == null || gameScore.getGameId().isBlank()
                || gameScore.getDate() == null || gameScore.getHomeTeam() == null || gameScore.getHomeTeam().isBlank()
                || gameScore.getAwayTeam() == null || gameScore.getAwayTeam().isBlank()
                || gameScore.getStatus() == null || gameScore.getStatus().isBlank()) {
            log.error("Invalid NbaGameScore creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid NbaGameScore data");
        }
        String id = String.valueOf(nbaGameScoreIdCounter.getAndIncrement());
        gameScore.setId(id);
        gameScore.setTechnicalId(UUID.randomUUID());

        nbaGameScoreCache.put(id, gameScore);
        log.info("Created NbaGameScore with ID: {}", id);

        processNbaGameScore(gameScore);

        return ResponseEntity.status(HttpStatus.CREATED).body(gameScore);
    }

    @GetMapping("/nbaGameScore/{id}")
    public ResponseEntity<?> getNbaGameScore(@PathVariable String id) {
        NbaGameScore gameScore = nbaGameScoreCache.get(id);
        if (gameScore == null) {
            log.error("NbaGameScore not found for ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("NbaGameScore not found");
        }
        return ResponseEntity.ok(gameScore);
    }

    @PutMapping("/nbaGameScore/{id}")
    public ResponseEntity<?> updateNbaGameScore(@PathVariable String id, @RequestBody NbaGameScore gameScore) {
        if (!nbaGameScoreCache.containsKey(id)) {
            log.error("NbaGameScore not found for update ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("NbaGameScore not found");
        }
        if (gameScore == null || gameScore.getGameId() == null || gameScore.getGameId().isBlank()
                || gameScore.getDate() == null || gameScore.getHomeTeam() == null || gameScore.getHomeTeam().isBlank()
                || gameScore.getAwayTeam() == null || gameScore.getAwayTeam().isBlank()
                || gameScore.getStatus() == null || gameScore.getStatus().isBlank()) {
            log.error("Invalid NbaGameScore update request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid NbaGameScore data");
        }
        gameScore.setId(id);
        gameScore.setTechnicalId(nbaGameScoreCache.get(id).getTechnicalId());

        nbaGameScoreCache.put(id, gameScore);
        log.info("Updated NbaGameScore with ID: {}", id);

        processNbaGameScore(gameScore);

        return ResponseEntity.ok(gameScore);
    }

    @DeleteMapping("/nbaGameScore/{id}")
    public ResponseEntity<?> deleteNbaGameScore(@PathVariable String id) {
        NbaGameScore removed = nbaGameScoreCache.remove(id);
        if (removed == null) {
            log.error("NbaGameScore not found for delete ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("NbaGameScore not found");
        }
        log.info("Deleted NbaGameScore with ID: {}", id);
        return ResponseEntity.ok("Deleted NbaGameScore with ID: " + id);
    }

    private void processNbaGameScore(NbaGameScore gameScore) {
        log.info("Processing NbaGameScore with ID: {}", gameScore.getId());
        // TODO: Implement actual business logic per requirements:
        // - Validate completeness of score data
        // - Update status to PROCESSED
        // - Trigger notifications for subscribers interested in teams
    }

    // --- Subscriber Endpoints ---

    @PostMapping("/subscriber")
    public ResponseEntity<?> createSubscriber(@RequestBody Subscriber subscriber) {
        if (subscriber == null || subscriber.getContactInfo() == null || subscriber.getContactInfo().isBlank()
                || subscriber.getStatus() == null || subscriber.getStatus().isBlank()) {
            log.error("Invalid Subscriber creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Subscriber data");
        }
        String id = String.valueOf(subscriberIdCounter.getAndIncrement());
        subscriber.setId(id);
        subscriber.setTechnicalId(UUID.randomUUID());

        subscriberCache.put(id, subscriber);
        log.info("Created Subscriber with ID: {}", id);

        processSubscriber(subscriber);

        return ResponseEntity.status(HttpStatus.CREATED).body(subscriber);
    }

    @GetMapping("/subscriber/{id}")
    public ResponseEntity<?> getSubscriber(@PathVariable String id) {
        Subscriber subscriber = subscriberCache.get(id);
        if (subscriber == null) {
            log.error("Subscriber not found for ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
        }
        return ResponseEntity.ok(subscriber);
    }

    @PutMapping("/subscriber/{id}")
    public ResponseEntity<?> updateSubscriber(@PathVariable String id, @RequestBody Subscriber subscriber) {
        if (!subscriberCache.containsKey(id)) {
            log.error("Subscriber not found for update ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
        }
        if (subscriber == null || subscriber.getContactInfo() == null || subscriber.getContactInfo().isBlank()
                || subscriber.getStatus() == null || subscriber.getStatus().isBlank()) {
            log.error("Invalid Subscriber update request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Subscriber data");
        }
        subscriber.setId(id);
        subscriber.setTechnicalId(subscriberCache.get(id).getTechnicalId());

        subscriberCache.put(id, subscriber);
        log.info("Updated Subscriber with ID: {}", id);

        processSubscriber(subscriber);

        return ResponseEntity.ok(subscriber);
    }

    @DeleteMapping("/subscriber/{id}")
    public ResponseEntity<?> deleteSubscriber(@PathVariable String id) {
        Subscriber removed = subscriberCache.remove(id);
        if (removed == null) {
            log.error("Subscriber not found for delete ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
        }
        log.info("Deleted Subscriber with ID: {}", id);
        return ResponseEntity.ok("Deleted Subscriber with ID: " + id);
    }

    private void processSubscriber(Subscriber subscriber) {
        log.info("Processing Subscriber with ID: {}", subscriber.getId());
        // TODO: Implement actual business logic per requirements:
        // - Validate contactInfo and preferences
        // - No further automatic processing unless triggered by GameScore events
    }
}