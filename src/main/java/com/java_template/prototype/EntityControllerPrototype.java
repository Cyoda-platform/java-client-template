package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.NbaScoreJob;
import com.java_template.application.entity.Subscriber;
import com.java_template.application.entity.GameScore;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, NbaScoreJob> nbaScoreJobCache = new ConcurrentHashMap<>();
    private final AtomicLong nbaScoreJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Subscriber> subscriberCache = new ConcurrentHashMap<>();
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, GameScore> gameScoreCache = new ConcurrentHashMap<>();
    private final AtomicLong gameScoreIdCounter = new AtomicLong(1);

    // --- NbaScoreJob Endpoints ---

    @PostMapping("/nbaScoreJob")
    public ResponseEntity<?> createNbaScoreJob(@RequestBody NbaScoreJob job) {
        if (job == null || job.getDate() == null) {
            log.error("Invalid NbaScoreJob creation request: missing date");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing or invalid required fields");
        }
        String id = String.valueOf(nbaScoreJobIdCounter.getAndIncrement());
        job.setId(id);
        job.setStatus("PENDING");
        job.setCreatedAt(new Date());
        job.setTechnicalId(UUID.randomUUID());
        nbaScoreJobCache.put(id, job);
        processNbaScoreJob(job);
        log.info("Created NbaScoreJob with ID: {}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @GetMapping("/nbaScoreJob/{id}")
    public ResponseEntity<?> getNbaScoreJob(@PathVariable String id) {
        NbaScoreJob job = nbaScoreJobCache.get(id);
        if (job == null) {
            log.error("NbaScoreJob not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("NbaScoreJob not found");
        }
        return ResponseEntity.ok(job);
    }

    // --- Subscriber Endpoints ---

    @PostMapping("/subscriber")
    public ResponseEntity<?> createSubscriber(@RequestBody Subscriber subscriber) {
        if (subscriber == null || subscriber.getEmail() == null || subscriber.getEmail().isBlank()) {
            log.error("Invalid Subscriber creation request: missing or blank email");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing or invalid email");
        }
        // Check for duplicate email
        boolean exists = subscriberCache.values().stream()
                .anyMatch(s -> s.getEmail().equalsIgnoreCase(subscriber.getEmail()) && "ACTIVE".equals(s.getStatus()));
        if (exists) {
            log.error("Subscriber creation failed: email already subscribed {}", subscriber.getEmail());
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email already subscribed");
        }
        String id = String.valueOf(subscriberIdCounter.getAndIncrement());
        subscriber.setId(id);
        subscriber.setStatus("ACTIVE");
        subscriber.setSubscribedAt(new Date());
        subscriber.setTechnicalId(UUID.randomUUID());
        subscriberCache.put(id, subscriber);
        processSubscriber(subscriber);
        log.info("Created Subscriber with ID: {}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriber);
    }

    @GetMapping("/subscriber/{id}")
    public ResponseEntity<?> getSubscriber(@PathVariable String id) {
        Subscriber subscriber = subscriberCache.get(id);
        if (subscriber == null) {
            log.error("Subscriber not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
        }
        return ResponseEntity.ok(subscriber);
    }

    @GetMapping("/subscribers")
    public ResponseEntity<?> getAllSubscribers() {
        List<Subscriber> list = new ArrayList<>(subscriberCache.values());
        return ResponseEntity.ok(list);
    }

    // --- GameScore Endpoints ---

    @PostMapping("/gameScore")
    public ResponseEntity<?> createGameScore(@RequestBody GameScore score) {
        if (score == null || score.getGameDate() == null || 
            score.getHomeTeam() == null || score.getHomeTeam().isBlank() ||
            score.getAwayTeam() == null || score.getAwayTeam().isBlank()) {
            log.error("Invalid GameScore creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing or invalid required fields");
        }
        String id = String.valueOf(gameScoreIdCounter.getAndIncrement());
        score.setId(id);
        score.setStatus("NEW");
        score.setTechnicalId(UUID.randomUUID());
        gameScoreCache.put(id, score);
        processGameScore(score);
        log.info("Created GameScore with ID: {}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(score);
    }

    @GetMapping("/gameScore/{id}")
    public ResponseEntity<?> getGameScore(@PathVariable String id) {
        GameScore score = gameScoreCache.get(id);
        if (score == null) {
            log.error("GameScore not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("GameScore not found");
        }
        return ResponseEntity.ok(score);
    }

    @GetMapping("/gameScores")
    public ResponseEntity<?> getAllGameScores(@RequestParam(required = false) Integer limit,
                                              @RequestParam(required = false) Integer offset) {
        List<GameScore> list = new ArrayList<>(gameScoreCache.values());
        // Simple pagination
        int start = offset != null && offset >= 0 ? offset : 0;
        int end = limit != null && limit > 0 ? Math.min(start + limit, list.size()) : list.size();
        if (start > list.size()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<GameScore> sublist = list.subList(start, end);
        return ResponseEntity.ok(sublist);
    }

    @GetMapping("/gameScores/date/{date}")
    public ResponseEntity<?> getGameScoresByDate(@PathVariable String date) {
        List<GameScore> filtered = new ArrayList<>();
        for (GameScore score : gameScoreCache.values()) {
            if (score.getGameDate() != null && date.equals(score.getGameDate().toString())) {
                filtered.add(score);
            }
        }
        return ResponseEntity.ok(filtered);
    }

    // --- Process Methods ---

    private void processNbaScoreJob(NbaScoreJob job) {
        log.info("Processing NbaScoreJob with ID: {}", job.getId());
        try {
            job.setStatus("IN_PROGRESS");
            // Simulate asynchronous data fetch from external API for job.date
            // In real scenario, call external API and parse results
            // Here, just simulate creation of GameScore entries
            Date gameDate = java.sql.Date.valueOf(job.getDate());
            GameScore sampleScore = new GameScore();
            sampleScore.setId(String.valueOf(gameScoreIdCounter.getAndIncrement()));
            sampleScore.setTechnicalId(UUID.randomUUID());
            sampleScore.setGameDate(job.getDate());
            sampleScore.setHomeTeam("Lakers");
            sampleScore.setAwayTeam("Warriors");
            sampleScore.setHomeScore(110);
            sampleScore.setAwayScore(108);
            sampleScore.setStatus("NEW");
            gameScoreCache.put(sampleScore.getId(), sampleScore);
            processGameScore(sampleScore);

            // Notify all active subscribers
            List<Subscriber> activeSubscribers = new ArrayList<>();
            for (Subscriber s : subscriberCache.values()) {
                if ("ACTIVE".equalsIgnoreCase(s.getStatus())) {
                    activeSubscribers.add(s);
                }
            }
            for (Subscriber sub : activeSubscribers) {
                // Simulate sending email notification
                log.info("Sending NBA scores email notification to subscriber: {}", sub.getEmail());
            }
            job.setStatus("COMPLETED");
            job.setCompletedAt(new Date());
        } catch (Exception e) {
            log.error("Failed to process NbaScoreJob ID {}: {}", job.getId(), e.getMessage());
            job.setStatus("FAILED");
        }
        nbaScoreJobCache.put(job.getId(), job);
    }

    private void processSubscriber(Subscriber subscriber) {
        log.info("Processing Subscriber with ID: {}", subscriber.getId());
        // Validate email format (simple regex)
        if (subscriber.getEmail() == null || !subscriber.getEmail().matches("^[\\w\\.-]+@[\\w\\.-]+\\.\\w+$")) {
            log.error("Invalid email format for subscriber ID: {}", subscriber.getId());
            // In real scenario, handle invalid email properly
        }
        // Optionally send welcome email notification
        log.info("Welcome email sent to: {}", subscriber.getEmail());
    }

    private void processGameScore(GameScore score) {
        log.info("Processing GameScore with ID: {}", score.getId());
        // Mark as processed after sending notification (simulate here)
        if ("NEW".equalsIgnoreCase(score.getStatus())) {
            score.setStatus("PROCESSED");
            gameScoreCache.put(score.getId(), score);
            log.info("GameScore ID {} marked as PROCESSED", score.getId());
        }
    }

}