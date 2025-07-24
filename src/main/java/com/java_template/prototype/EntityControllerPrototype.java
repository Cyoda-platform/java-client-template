package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.ScoreFetchJob;
import com.java_template.application.entity.Subscriber;
import com.java_template.application.entity.GameScore;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, ScoreFetchJob> scoreFetchJobCache = new ConcurrentHashMap<>();
    private final AtomicLong scoreFetchJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Subscriber> subscriberCache = new ConcurrentHashMap<>();
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, GameScore> gameScoreCache = new ConcurrentHashMap<>();
    private final AtomicLong gameScoreIdCounter = new AtomicLong(1);

    // POST /prototype/scoreFetchJob - create ScoreFetchJob entity
    @PostMapping("/scoreFetchJob")
    public ResponseEntity<Map<String, String>> createScoreFetchJob(@RequestBody ScoreFetchJob job) {
        if (job == null || job.getJobDate() == null || job.getStatus() == null || job.getStatus().isBlank()) {
            log.error("Invalid ScoreFetchJob input");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ScoreFetchJob input"));
        }
        String id = String.valueOf(scoreFetchJobIdCounter.getAndIncrement());
        scoreFetchJobCache.put(id, job);
        log.info("ScoreFetchJob created with ID: {}", id);
        processScoreFetchJob(job);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));
    }

    // GET /prototype/scoreFetchJob/{id} - retrieve ScoreFetchJob entity by id
    @GetMapping("/scoreFetchJob/{id}")
    public ResponseEntity<?> getScoreFetchJob(@PathVariable String id) {
        ScoreFetchJob job = scoreFetchJobCache.get(id);
        if (job == null) {
            log.error("ScoreFetchJob not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "ScoreFetchJob not found"));
        }
        return ResponseEntity.ok(job);
    }

    // POST /prototype/subscriber - create Subscriber entity
    @PostMapping("/subscriber")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Subscriber subscriber) {
        if (subscriber == null || subscriber.getEmail() == null || subscriber.getEmail().isBlank()
                || subscriber.getStatus() == null || subscriber.getStatus().isBlank()) {
            log.error("Invalid Subscriber input");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Subscriber input"));
        }
        String id = String.valueOf(subscriberIdCounter.getAndIncrement());
        subscriberCache.put(id, subscriber);
        log.info("Subscriber created with ID: {}", id);
        processSubscriber(subscriber);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));
    }

    // GET /prototype/subscriber/{id} - retrieve Subscriber entity by id
    @GetMapping("/subscriber/{id}")
    public ResponseEntity<?> getSubscriber(@PathVariable String id) {
        Subscriber subscriber = subscriberCache.get(id);
        if (subscriber == null) {
            log.error("Subscriber not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Subscriber not found"));
        }
        return ResponseEntity.ok(subscriber);
    }

    // POST /prototype/gameScore - create GameScore entity
    @PostMapping("/gameScore")
    public ResponseEntity<Map<String, String>> createGameScore(@RequestBody GameScore gameScore) {
        if (gameScore == null || gameScore.getGameDate() == null || gameScore.getHomeTeam() == null || gameScore.getHomeTeam().isBlank()
                || gameScore.getAwayTeam() == null || gameScore.getAwayTeam().isBlank()
                || gameScore.getHomeScore() == null || gameScore.getAwayScore() == null
                || gameScore.getStatus() == null || gameScore.getStatus().isBlank()) {
            log.error("Invalid GameScore input");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid GameScore input"));
        }
        String id = String.valueOf(gameScoreIdCounter.getAndIncrement());
        gameScoreCache.put(id, gameScore);
        log.info("GameScore created with ID: {}", id);
        processGameScore(gameScore);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));
    }

    // GET /prototype/gameScore/{id} - retrieve GameScore entity by id
    @GetMapping("/gameScore/{id}")
    public ResponseEntity<?> getGameScore(@PathVariable String id) {
        GameScore gameScore = gameScoreCache.get(id);
        if (gameScore == null) {
            log.error("GameScore not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "GameScore not found"));
        }
        return ResponseEntity.ok(gameScore);
    }

    // Example of process methods with business logic from requirements

    private void processScoreFetchJob(ScoreFetchJob job) {
        log.info("Processing ScoreFetchJob for date: {}", job.getJobDate());
        // 1. Set status to RUNNING and triggeredAt timestamp
        job.setStatus("RUNNING");
        job.setTriggeredAt(Instant.now());

        // 2. Fetch NBA scores asynchronously - simulate fetch here (in real, call external API)
        // For prototype, simulate fetch with dummy data:
        List<GameScore> fetchedScores = new ArrayList<>();
        // Simulate one dummy game score
        GameScore gs = new GameScore();
        gs.setGameDate(job.getJobDate());
        gs.setHomeTeam("Team A");
        gs.setAwayTeam("Team B");
        gs.setHomeScore(100);
        gs.setAwayScore(98);
        gs.setStatus("RECEIVED");
        fetchedScores.add(gs);

        // 3. Save each fetched GameScore as immutable entity
        for (GameScore gsEntity : fetchedScores) {
            String gsId = String.valueOf(gameScoreIdCounter.getAndIncrement());
            gameScoreCache.put(gsId, gsEntity);
            log.info("Saved GameScore with ID: {}", gsId);
        }

        // 4. Update job status to COMPLETED and completedAt timestamp
        job.setStatus("COMPLETED");
        job.setCompletedAt(Instant.now());

        // 5. Trigger notification process
        processGameScoreNotification(job.getJobDate());
    }

    private void processSubscriber(Subscriber subscriber) {
        log.info("Processing Subscriber with email: {}", subscriber.getEmail());
        // Validate email format (simple contains '@' check)
        if (!subscriber.getEmail().contains("@")) {
            log.error("Invalid email format for subscriber: {}", subscriber.getEmail());
            return;
        }
        // Add subscriber to notification list logic can be more complex in real system
        subscriber.setStatus("ACTIVE");
        subscriber.setSubscribedAt(Instant.now());
        log.info("Subscriber {} set to ACTIVE", subscriber.getEmail());
    }

    private void processGameScore(GameScore gameScore) {
        log.info("Processing GameScore for game: {} vs {} on {}", gameScore.getHomeTeam(), gameScore.getAwayTeam(), gameScore.getGameDate());
        // Placeholder for any enrichment or validation logic
    }

    private void processGameScoreNotification(LocalDate jobDate) {
        log.info("Sending notifications for GameScores on date: {}", jobDate);
        // Retrieve all GameScores with status RECEIVED for the jobDate
        List<GameScore> scoresToNotify = new ArrayList<>();
        for (GameScore gs : gameScoreCache.values()) {
            if (gs.getGameDate().equals(jobDate) && "RECEIVED".equals(gs.getStatus())) {
                scoresToNotify.add(gs);
            }
        }

        // Generate summary text
        StringBuilder summary = new StringBuilder("NBA Scores for " + jobDate + ":\n");
        for (GameScore gs : scoresToNotify) {
            summary.append(gs.getHomeTeam()).append(" ").append(gs.getHomeScore())
                .append(" - ").append(gs.getAwayTeam()).append(" ").append(gs.getAwayScore())
                .append("\n");
        }

        // Send email to all ACTIVE subscribers (simulate)
        for (Subscriber sub : subscriberCache.values()) {
            if ("ACTIVE".equals(sub.getStatus())) {
                log.info("Sending email to {}: \n{}", sub.getEmail(), summary.toString());
            }
        }

        // Update GameScore statuses to PROCESSED
        for (GameScore gs : scoresToNotify) {
            gs.setStatus("PROCESSED");
        }
    }
}