package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.NbaScoreFetchJob;
import com.java_template.application.entity.GameScore;
import com.java_template.application.entity.Subscription;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Cache and ID counter for NbaScoreFetchJob (orchestration entity)
    private final ConcurrentHashMap<String, NbaScoreFetchJob> nbaScoreFetchJobCache = new ConcurrentHashMap<>();
    private final AtomicLong nbaScoreFetchJobIdCounter = new AtomicLong(1);

    // Cache and ID counter for GameScore (business entity)
    private final ConcurrentHashMap<String, GameScore> gameScoreCache = new ConcurrentHashMap<>();
    private final AtomicLong gameScoreIdCounter = new AtomicLong(1);

    // Cache and ID counter for Subscription (business entity)
    private final ConcurrentHashMap<String, Subscription> subscriptionCache = new ConcurrentHashMap<>();
    private final AtomicLong subscriptionIdCounter = new AtomicLong(1);

    // -------------------- NbaScoreFetchJob Endpoints --------------------

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createNbaScoreFetchJob(@RequestBody Map<String, String> request) {
        String scheduledDateStr = request.get("scheduledDate");
        if (scheduledDateStr == null || scheduledDateStr.isBlank()) {
            log.error("Scheduled date is missing or blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "scheduledDate is required"));
        }
        NbaScoreFetchJob job = new NbaScoreFetchJob();
        String technicalId = "job_" + nbaScoreFetchJobIdCounter.getAndIncrement();
        job.setJobId(technicalId);
        try {
            job.setScheduledDate(java.time.LocalDate.parse(scheduledDateStr));
        } catch (Exception e) {
            log.error("Invalid scheduledDate format: {}", scheduledDateStr);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "scheduledDate must be in YYYY-MM-DD format"));
        }
        job.setStatus("PENDING");
        job.setCreatedAt(java.time.LocalDateTime.now());
        nbaScoreFetchJobCache.put(technicalId, job);

        processNbaScoreFetchJob(job);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getNbaScoreFetchJob(@PathVariable("id") String id) {
        NbaScoreFetchJob job = nbaScoreFetchJobCache.get(id);
        if (job == null) {
            log.error("NbaScoreFetchJob not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found"));
        }
        return ResponseEntity.ok(job);
    }

    // -------------------- GameScore Endpoints --------------------

    @GetMapping("/games/{id}")
    public ResponseEntity<?> getGameScore(@PathVariable("id") String id) {
        GameScore gameScore = gameScoreCache.get(id);
        if (gameScore == null) {
            log.error("GameScore not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "GameScore not found"));
        }
        return ResponseEntity.ok(gameScore);
    }

    // No POST endpoint for GameScore as per EDA principle,
    // creation of GameScore happens inside processNbaScoreFetchJob

    // -------------------- Subscription Endpoints --------------------

    @PostMapping("/subscriptions")
    public ResponseEntity<Map<String, String>> createSubscription(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        String notificationType = request.get("notificationType");
        String channel = request.get("channel");
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "userId is required"));
        }
        if (notificationType == null || notificationType.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "notificationType is required"));
        }
        if (channel == null || channel.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "channel is required"));
        }
        // team is optional, can be null or blank for general notifications
        String team = request.getOrDefault("team", null);

        Subscription subscription = new Subscription();
        String technicalId = "sub_" + subscriptionIdCounter.getAndIncrement();
        subscription.setSubscriptionId(technicalId);
        subscription.setUserId(userId);
        subscription.setNotificationType(notificationType);
        subscription.setChannel(channel);
        subscription.setTeam(team);
        subscription.setStatus("ACTIVE");
        subscription.setCreatedAt(java.time.LocalDateTime.now());

        subscriptionCache.put(technicalId, subscription);

        processSubscription(subscription);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/subscriptions/{id}")
    public ResponseEntity<?> getSubscription(@PathVariable("id") String id) {
        Subscription subscription = subscriptionCache.get(id);
        if (subscription == null) {
            log.error("Subscription not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Subscription not found"));
        }
        return ResponseEntity.ok(subscription);
    }

    // -------------------- Process Methods --------------------

    private void processNbaScoreFetchJob(NbaScoreFetchJob job) {
        log.info("Processing NbaScoreFetchJob with ID: {}", job.getJobId());
        try {
            job.setStatus("RUNNING");
            // Simulate external NBA API call to fetch scores for scheduledDate
            // Here we simulate data, in real case call external API and handle response
            List<GameScore> fetchedGameScores = fetchGameScoresFromExternalApi(job.getScheduledDate());

            // Persist each GameScore entity
            for (GameScore gs : fetchedGameScores) {
                String gsId = "game_" + gameScoreIdCounter.getAndIncrement();
                gs.setGameId(gsId);
                gameScoreCache.put(gsId, gs);
                processGameScore(gs);
            }

            job.setStatus("COMPLETED");
            log.info("NbaScoreFetchJob {} completed successfully with {} games fetched.", job.getJobId(), fetchedGameScores.size());

            // Trigger notifications for each GameScore and active subscriptions
            for (GameScore gs : fetchedGameScores) {
                triggerNotifications(gs);
            }
        } catch (Exception e) {
            log.error("Failed to process NbaScoreFetchJob {}: {}", job.getJobId(), e.getMessage());
            job.setStatus("FAILED");
        }
    }

    private List<GameScore> fetchGameScoresFromExternalApi(java.time.LocalDate date) {
        // Simulated data fetching - replace with real HTTP client calls to NBA API
        log.info("Fetching NBA game scores for date {}", date);
        List<GameScore> gameScores = new ArrayList<>();

        // Simulate two games
        GameScore gs1 = new GameScore();
        gs1.setGameDate(date);
        gs1.setHomeTeam("Lakers");
        gs1.setAwayTeam("Warriors");
        gs1.setHomeScore(102);
        gs1.setAwayScore(99);
        gs1.setStatus("COMPLETED");
        gameScores.add(gs1);

        GameScore gs2 = new GameScore();
        gs2.setGameDate(date);
        gs2.setHomeTeam("Celtics");
        gs2.setAwayTeam("Bulls");
        gs2.setHomeScore(110);
        gs2.setAwayScore(105);
        gs2.setStatus("COMPLETED");
        gameScores.add(gs2);

        return gameScores;
    }

    private void processGameScore(GameScore gameScore) {
        log.info("Processing GameScore with ID: {}", gameScore.getGameId());
        // Validate score data completeness
        if (gameScore.getHomeTeam() == null || gameScore.getHomeTeam().isBlank()
                || gameScore.getAwayTeam() == null || gameScore.getAwayTeam().isBlank()) {
            log.error("Invalid GameScore data: missing team names");
            return;
        }
        if (gameScore.getHomeScore() == null || gameScore.getAwayScore() == null) {
            log.error("Invalid GameScore data: missing scores");
            return;
        }
        // Additional processing logic can be added here if needed
        log.info("GameScore {} processed successfully", gameScore.getGameId());
    }

    private void processSubscription(Subscription subscription) {
        log.info("Processing Subscription with ID: {}", subscription.getSubscriptionId());
        // Validate user ID
        if (subscription.getUserId() == null || subscription.getUserId().isBlank()) {
            log.error("Subscription userId is invalid");
            return;
        }
        // Validate notification type and channel
        if (subscription.getNotificationType() == null || subscription.getNotificationType().isBlank()) {
            log.error("Subscription notificationType is invalid");
            return;
        }
        if (subscription.getChannel() == null || subscription.getChannel().isBlank()) {
            log.error("Subscription channel is invalid");
            return;
        }
        log.info("Subscription {} processed successfully", subscription.getSubscriptionId());
    }

    private void triggerNotifications(GameScore gameScore) {
        log.info("Triggering notifications for GameScore ID: {}", gameScore.getGameId());
        // Iterate over active subscriptions and send notifications if criteria match
        subscriptionCache.values().stream()
                .filter(sub -> "ACTIVE".equals(sub.getStatus()))
                .forEach(sub -> {
                    boolean notify = false;
                    // Check notification criteria
                    if (sub.getTeam() == null || sub.getTeam().isBlank()) {
                        // General NBA notifications
                        notify = true;
                    } else if (sub.getTeam().equalsIgnoreCase(gameScore.getHomeTeam())
                            || sub.getTeam().equalsIgnoreCase(gameScore.getAwayTeam())) {
                        notify = true;
                    }
                    if (notify) {
                        sendNotification(sub, gameScore);
                    }
                });
    }

    private void sendNotification(Subscription subscription, GameScore gameScore) {
        // Simulated notification sending
        log.info("Sending {} notification to user {} via {} for game {} vs {} with scores {}-{}",
                subscription.getNotificationType(),
                subscription.getUserId(),
                subscription.getChannel(),
                gameScore.getHomeTeam(),
                gameScore.getAwayTeam(),
                gameScore.getHomeScore(),
                gameScore.getAwayScore());
        // In real implementation, integrate with SendGrid, Twilio, FCM, etc.
    }
}