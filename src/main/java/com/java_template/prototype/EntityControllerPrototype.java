package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.GameScoreFetchJob;
import com.java_template.application.entity.NbaGameScore;
import com.java_template.application.entity.Subscriber;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
@Validated
public class EntityControllerPrototype {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final ConcurrentHashMap<String, GameScoreFetchJob> gameScoreFetchJobCache = new ConcurrentHashMap<>();
    private final AtomicLong gameScoreFetchJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, NbaGameScore> nbaGameScoreCache = new ConcurrentHashMap<>();
    private final AtomicLong nbaGameScoreIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Subscriber> subscriberCache = new ConcurrentHashMap<>();
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    // DTOs for validation - only primitives/String, no nested objects

    public static class GameScoreFetchJobDTO {
        @NotBlank
        private String status;

        @NotNull
        private Long scheduledDate; // use epoch millis for date validation

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public Long getScheduledDate() { return scheduledDate; }
        public void setScheduledDate(Long scheduledDate) { this.scheduledDate = scheduledDate; }
    }

    public static class NbaGameScoreDTO {
        @NotBlank
        private String gameId;

        @NotNull
        private Long date; // epoch millis

        @NotBlank
        private String homeTeam;

        @NotBlank
        private String awayTeam;

        @NotNull
        @Min(0)
        private Integer homeTeamScore;

        @NotNull
        @Min(0)
        private Integer awayTeamScore;

        @NotBlank
        private String status;

        public String getGameId() { return gameId; }
        public void setGameId(String gameId) { this.gameId = gameId; }

        public Long getDate() { return date; }
        public void setDate(Long date) { this.date = date; }

        public String getHomeTeam() { return homeTeam; }
        public void setHomeTeam(String homeTeam) { this.homeTeam = homeTeam; }

        public String getAwayTeam() { return awayTeam; }
        public void setAwayTeam(String awayTeam) { this.awayTeam = awayTeam; }

        public Integer getHomeTeamScore() { return homeTeamScore; }
        public void setHomeTeamScore(Integer homeTeamScore) { this.homeTeamScore = homeTeamScore; }

        public Integer getAwayTeamScore() { return awayTeamScore; }
        public void setAwayTeamScore(Integer awayTeamScore) { this.awayTeamScore = awayTeamScore; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class SubscriberDTO {
        @NotBlank
        private String contactInfo;

        @NotBlank
        private String status;

        public String getContactInfo() { return contactInfo; }
        public void setContactInfo(String contactInfo) { this.contactInfo = contactInfo; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    // --- GameScoreFetchJob Endpoints ---

    @PostMapping("/gameScoreFetchJob")
    public ResponseEntity<?> createGameScoreFetchJob(@RequestBody @Valid GameScoreFetchJobDTO dto) {
        GameScoreFetchJob job = new GameScoreFetchJob();
        job.setStatus(dto.getStatus());
        job.setScheduledDate(new java.util.Date(dto.getScheduledDate()));
        String id = String.valueOf(gameScoreFetchJobIdCounter.getAndIncrement());
        job.setId(id);
        job.setTechnicalId(UUID.randomUUID());
        job.setCreatedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        job.setUpdatedAt(new java.sql.Timestamp(System.currentTimeMillis()));

        gameScoreFetchJobCache.put(id, job);
        logger.info("Created GameScoreFetchJob with ID: {}", id);

        processGameScoreFetchJob(job);

        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @GetMapping("/gameScoreFetchJob/{id}")
    public ResponseEntity<?> getGameScoreFetchJob(@PathVariable @NotBlank String id) {
        GameScoreFetchJob job = gameScoreFetchJobCache.get(id);
        if (job == null) {
            logger.error("GameScoreFetchJob not found for ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("GameScoreFetchJob not found");
        }
        return ResponseEntity.ok(job);
    }

    @PutMapping("/gameScoreFetchJob/{id}")
    public ResponseEntity<?> updateGameScoreFetchJob(@PathVariable @NotBlank String id, @RequestBody @Valid GameScoreFetchJobDTO dto) {
        if (!gameScoreFetchJobCache.containsKey(id)) {
            logger.error("GameScoreFetchJob not found for update ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("GameScoreFetchJob not found");
        }
        GameScoreFetchJob job = gameScoreFetchJobCache.get(id);
        job.setStatus(dto.getStatus());
        job.setScheduledDate(new java.util.Date(dto.getScheduledDate()));
        job.setUpdatedAt(new java.sql.Timestamp(System.currentTimeMillis()));

        gameScoreFetchJobCache.put(id, job);
        logger.info("Updated GameScoreFetchJob with ID: {}", id);

        processGameScoreFetchJob(job);

        return ResponseEntity.ok(job);
    }

    @DeleteMapping("/gameScoreFetchJob/{id}")
    public ResponseEntity<?> deleteGameScoreFetchJob(@PathVariable @NotBlank String id) {
        GameScoreFetchJob removed = gameScoreFetchJobCache.remove(id);
        if (removed == null) {
            logger.error("GameScoreFetchJob not found for delete ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("GameScoreFetchJob not found");
        }
        logger.info("Deleted GameScoreFetchJob with ID: {}", id);
        return ResponseEntity.ok("Deleted GameScoreFetchJob with ID: " + id);
    }

    private void processGameScoreFetchJob(GameScoreFetchJob job) {
        logger.info("Processing GameScoreFetchJob with ID: {}", job.getId());
        // TODO: Implement actual business logic per requirements:
        // - Validate scheduledDate
        // - Fetch NBA game scores from external API
        // - Save/update NbaGameScore entities in cache or database
        // - Update job status
        // - Trigger notifications if necessary
    }

    // --- NbaGameScore Endpoints ---

    @PostMapping("/nbaGameScore")
    public ResponseEntity<?> createNbaGameScore(@RequestBody @Valid NbaGameScoreDTO dto) {
        NbaGameScore gameScore = new NbaGameScore();
        gameScore.setGameId(dto.getGameId());
        gameScore.setDate(new java.util.Date(dto.getDate()));
        gameScore.setHomeTeam(dto.getHomeTeam());
        gameScore.setAwayTeam(dto.getAwayTeam());
        gameScore.setHomeTeamScore(dto.getHomeTeamScore());
        gameScore.setAwayTeamScore(dto.getAwayTeamScore());
        gameScore.setStatus(dto.getStatus());
        String id = String.valueOf(nbaGameScoreIdCounter.getAndIncrement());
        gameScore.setId(id);
        gameScore.setTechnicalId(UUID.randomUUID());

        nbaGameScoreCache.put(id, gameScore);
        logger.info("Created NbaGameScore with ID: {}", id);

        processNbaGameScore(gameScore);

        return ResponseEntity.status(HttpStatus.CREATED).body(gameScore);
    }

    @GetMapping("/nbaGameScore/{id}")
    public ResponseEntity<?> getNbaGameScore(@PathVariable @NotBlank String id) {
        NbaGameScore gameScore = nbaGameScoreCache.get(id);
        if (gameScore == null) {
            logger.error("NbaGameScore not found for ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("NbaGameScore not found");
        }
        return ResponseEntity.ok(gameScore);
    }

    @PutMapping("/nbaGameScore/{id}")
    public ResponseEntity<?> updateNbaGameScore(@PathVariable @NotBlank String id, @RequestBody @Valid NbaGameScoreDTO dto) {
        if (!nbaGameScoreCache.containsKey(id)) {
            logger.error("NbaGameScore not found for update ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("NbaGameScore not found");
        }
        NbaGameScore gameScore = nbaGameScoreCache.get(id);
        gameScore.setGameId(dto.getGameId());
        gameScore.setDate(new java.util.Date(dto.getDate()));
        gameScore.setHomeTeam(dto.getHomeTeam());
        gameScore.setAwayTeam(dto.getAwayTeam());
        gameScore.setHomeTeamScore(dto.getHomeTeamScore());
        gameScore.setAwayTeamScore(dto.getAwayTeamScore());
        gameScore.setStatus(dto.getStatus());

        nbaGameScoreCache.put(id, gameScore);
        logger.info("Updated NbaGameScore with ID: {}", id);

        processNbaGameScore(gameScore);

        return ResponseEntity.ok(gameScore);
    }

    @DeleteMapping("/nbaGameScore/{id}")
    public ResponseEntity<?> deleteNbaGameScore(@PathVariable @NotBlank String id) {
        NbaGameScore removed = nbaGameScoreCache.remove(id);
        if (removed == null) {
            logger.error("NbaGameScore not found for delete ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("NbaGameScore not found");
        }
        logger.info("Deleted NbaGameScore with ID: {}", id);
        return ResponseEntity.ok("Deleted NbaGameScore with ID: " + id);
    }

    private void processNbaGameScore(NbaGameScore gameScore) {
        logger.info("Processing NbaGameScore with ID: {}", gameScore.getId());
        // TODO: Implement actual business logic per requirements:
        // - Validate completeness of score data
        // - Update status to PROCESSED
        // - Trigger notifications for subscribers interested in teams
    }

    // --- Subscriber Endpoints ---

    @PostMapping("/subscriber")
    public ResponseEntity<?> createSubscriber(@RequestBody @Valid SubscriberDTO dto) {
        Subscriber subscriber = new Subscriber();
        subscriber.setContactInfo(dto.getContactInfo());
        subscriber.setStatus(dto.getStatus());
        String id = String.valueOf(subscriberIdCounter.getAndIncrement());
        subscriber.setId(id);
        subscriber.setTechnicalId(UUID.randomUUID());

        subscriberCache.put(id, subscriber);
        logger.info("Created Subscriber with ID: {}", id);

        processSubscriber(subscriber);

        return ResponseEntity.status(HttpStatus.CREATED).body(subscriber);
    }

    @GetMapping("/subscriber/{id}")
    public ResponseEntity<?> getSubscriber(@PathVariable @NotBlank String id) {
        Subscriber subscriber = subscriberCache.get(id);
        if (subscriber == null) {
            logger.error("Subscriber not found for ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
        }
        return ResponseEntity.ok(subscriber);
    }

    @PutMapping("/subscriber/{id}")
    public ResponseEntity<?> updateSubscriber(@PathVariable @NotBlank String id, @RequestBody @Valid SubscriberDTO dto) {
        if (!subscriberCache.containsKey(id)) {
            logger.error("Subscriber not found for update ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
        }
        Subscriber subscriber = subscriberCache.get(id);
        subscriber.setContactInfo(dto.getContactInfo());
        subscriber.setStatus(dto.getStatus());

        subscriberCache.put(id, subscriber);
        logger.info("Updated Subscriber with ID: {}", id);

        processSubscriber(subscriber);

        return ResponseEntity.ok(subscriber);
    }

    @DeleteMapping("/subscriber/{id}")
    public ResponseEntity<?> deleteSubscriber(@PathVariable @NotBlank String id) {
        Subscriber removed = subscriberCache.remove(id);
        if (removed == null) {
            logger.error("Subscriber not found for delete ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
        }
        logger.info("Deleted Subscriber with ID: {}", id);
        return ResponseEntity.ok("Deleted Subscriber with ID: " + id);
    }

    private void processSubscriber(Subscriber subscriber) {
        logger.info("Processing Subscriber with ID: {}", subscriber.getId());
        // TODO: Implement actual business logic per requirements:
        // - Validate contactInfo and preferences
        // - No further automatic processing unless triggered by GameScore events
    }
}