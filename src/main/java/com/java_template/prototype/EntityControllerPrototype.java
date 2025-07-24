package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.DailyScoresJob;
import com.java_template.application.entity.Subscriber;
import com.java_template.application.entity.GameScore;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Cache and ID counters for DailyScoresJob
    private final ConcurrentHashMap<String, DailyScoresJob> dailyScoresJobCache = new ConcurrentHashMap<>();
    private final AtomicLong dailyScoresJobIdCounter = new AtomicLong(1);

    // Cache and ID counters for Subscriber
    private final ConcurrentHashMap<String, Subscriber> subscriberCache = new ConcurrentHashMap<>();
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    // Cache and ID counters for GameScore
    private final ConcurrentHashMap<String, GameScore> gameScoreCache = new ConcurrentHashMap<>();
    private final AtomicLong gameScoreIdCounter = new AtomicLong(1);

    // --- DailyScoresJob Endpoints ---

    @PostMapping("/dailyScoresJob")
    public ResponseEntity<Map<String, String>> createDailyScoresJob(@RequestBody DailyScoresJob jobRequest) {
        if (jobRequest == null || jobRequest.getDate() == null || jobRequest.getDate().isBlank()) {
            log.error("Invalid DailyScoresJob creation request: missing or blank date");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Date is required and cannot be blank"));
        }

        String id = "job-" + dailyScoresJobIdCounter.getAndIncrement();
        jobRequest.setStatus("PENDING");
        jobRequest.setCreatedAt(java.time.Instant.now().toString());
        jobRequest.setCompletedAt(null);
        dailyScoresJobCache.put(id, jobRequest);

        log.info("Created DailyScoresJob with ID: {}", id);

        processDailyScoresJob(jobRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));
    }

    @GetMapping("/dailyScoresJob/{id}")
    public ResponseEntity<?> getDailyScoresJobById(@PathVariable String id) {
        var job = dailyScoresJobCache.get(id);
        if (job == null) {
            log.error("DailyScoresJob with ID {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DailyScoresJob not found"));
        }
        return ResponseEntity.ok(job);
    }

    // --- Subscriber Endpoints ---

    @PostMapping("/subscriber")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Subscriber subscriberRequest) {
        if (subscriberRequest == null || subscriberRequest.getEmail() == null || subscriberRequest.getEmail().isBlank()) {
            log.error("Invalid Subscriber creation request: missing or blank email");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Email is required and cannot be blank"));
        }

        String id = "sub-" + subscriberIdCounter.getAndIncrement();
        subscriberRequest.setSubscribedAt(java.time.Instant.now().toString());
        subscriberCache.put(id, subscriberRequest);

        log.info("Created Subscriber with ID: {}", id);

        processSubscriber(subscriberRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));
    }

    @GetMapping("/subscriber/{id}")
    public ResponseEntity<?> getSubscriberById(@PathVariable String id) {
        var subscriber = subscriberCache.get(id);
        if (subscriber == null) {
            log.error("Subscriber with ID {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Subscriber not found"));
        }
        return ResponseEntity.ok(subscriber);
    }

    // --- GameScore Endpoints ---

    @GetMapping("/gameScore/{id}")
    public ResponseEntity<?> getGameScoreById(@PathVariable String id) {
        var gameScore = gameScoreCache.get(id);
        if (gameScore == null) {
            log.error("GameScore with ID {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "GameScore not found"));
        }
        return ResponseEntity.ok(gameScore);
    }

    // Note: No POST endpoint for GameScore because these are created immutably by the job process

    // --- Process Methods ---

    private void processDailyScoresJob(DailyScoresJob job) {
        log.info("Processing DailyScoresJob for date: {}", job.getDate());

        // Validate date is not in the future
        try {
            java.time.LocalDate jobDate = java.time.LocalDate.parse(job.getDate());
            if (jobDate.isAfter(java.time.LocalDate.now())) {
                job.setStatus("FAILED");
                job.setCompletedAt(java.time.Instant.now().toString());
                log.error("DailyScoresJob date {} is in the future", job.getDate());
                return;
            }
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setCompletedAt(java.time.Instant.now().toString());
            log.error("Invalid date format for DailyScoresJob: {}", job.getDate());
            return;
        }

        try {
            // Simulate fetching NBA scores from external API asynchronously (simplified here)
            List<GameScore> fetchedScores = fetchNbaScores(job.getDate());

            // Persist GameScore entities immutably
            for (GameScore score : fetchedScores) {
                String id = "game-" + gameScoreIdCounter.getAndIncrement();
                gameScoreCache.put(id, score);
                processGameScore(score);
            }

            // Retrieve all subscribers and send email notifications
            for (Map.Entry<String, Subscriber> entry : subscriberCache.entrySet()) {
                Subscriber subscriber = entry.getValue();
                sendEmailNotification(subscriber.getEmail(), job.getDate(), fetchedScores);
            }

            job.setStatus("COMPLETED");
            job.setCompletedAt(java.time.Instant.now().toString());
            log.info("Completed processing DailyScoresJob for date: {}", job.getDate());

        } catch (Exception ex) {
            job.setStatus("FAILED");
            job.setCompletedAt(java.time.Instant.now().toString());
            log.error("Error processing DailyScoresJob: {}", ex.getMessage());
        }
    }

    private void processSubscriber(Subscriber subscriber) {
        log.info("Processing Subscriber with email: {}", subscriber.getEmail());

        // Validate email format simple check
        if (!subscriber.getEmail().contains("@")) {
            log.error("Invalid email format for subscriber: {}", subscriber.getEmail());
        }
        // No further processing needed immediately
    }

    private void processGameScore(GameScore gameScore) {
        log.info("Processing GameScore: {} vs {} on {}", gameScore.getHomeTeam(),
                gameScore.getAwayTeam(), gameScore.getDate());

        // Optional: Validate scores and gameStatus consistency
        if (gameScore.getHomeScore() < 0 || gameScore.getAwayScore() < 0) {
            log.error("Invalid score values for GameScore: {} vs {}", gameScore.getHomeTeam(), gameScore.getAwayTeam());
        }
    }

    // --- Helper Methods ---

    private List<GameScore> fetchNbaScores(String date) {
        // Simulated API fetch. In real implementation, call external API asynchronously.
        log.info("Fetching NBA scores for date: {}", date);

        // Dummy data for prototype
        GameScore gs1 = new GameScore();
        gs1.setDate(date);
        gs1.setHomeTeam("Lakers");
        gs1.setAwayTeam("Warriors");
        gs1.setHomeScore(102);
        gs1.setAwayScore(99);
        gs1.setGameStatus("Completed");

        GameScore gs2 = new GameScore();
        gs2.setDate(date);
        gs2.setHomeTeam("Nets");
        gs2.setAwayTeam("Celtics");
        gs2.setHomeScore(110);
        gs2.setAwayScore(115);
        gs2.setGameStatus("Completed");

        return List.of(gs1, gs2);
    }

    private void sendEmailNotification(String email, String date, List<GameScore> gameScores) {
        // Simulated email sending. Real implementation would use JavaMailSender or similar.
        log.info("Sending email notification to {} for NBA games on {}", email, date);
        // Construct email content from gameScores if needed
    }
}