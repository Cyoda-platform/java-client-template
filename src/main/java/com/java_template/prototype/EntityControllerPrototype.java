package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.regex.Pattern;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cache and ID counters for NbaScoresFetchJob
    private final ConcurrentHashMap<String, NbaScoresFetchJob> nbaScoresFetchJobCache = new ConcurrentHashMap<>();
    private final AtomicLong nbaScoresFetchJobIdCounter = new AtomicLong(1);

    // Cache and ID counters for NbaGame
    private final ConcurrentHashMap<String, NbaGame> nbaGameCache = new ConcurrentHashMap<>();
    private final AtomicLong nbaGameIdCounter = new AtomicLong(1);

    // Cache and ID counters for Subscriber
    private final ConcurrentHashMap<String, Subscriber> subscriberCache = new ConcurrentHashMap<>();
    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    // ------------------ NbaScoresFetchJob Endpoints ------------------

    @PostMapping("/jobs/fetch-scores")
    public ResponseEntity<Map<String, String>> createNbaScoresFetchJob(@RequestBody Map<String, String> request) {
        String scheduledDateStr = request.get("scheduledDate");
        if (scheduledDateStr == null || scheduledDateStr.isBlank()) {
            log.error("Missing or blank scheduledDate");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "scheduledDate is required"));
        }

        LocalDate scheduledDate;
        try {
            scheduledDate = LocalDate.parse(scheduledDateStr);
        } catch (Exception e) {
            log.error("Invalid scheduledDate format: {}", scheduledDateStr);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "scheduledDate must be in YYYY-MM-DD format"));
        }

        NbaScoresFetchJob job = new NbaScoresFetchJob();
        String id = "job-" + nbaScoresFetchJobIdCounter.getAndIncrement();
        job.setId(id);
        job.setScheduledDate(scheduledDate);
        job.setFetchTimeUTC(LocalTime.of(18,0)); // fixed 6:00 PM UTC
        job.setStatus("PENDING");
        job.setSummary(null);

        nbaScoresFetchJobCache.put(id, job);

        // Trigger processing
        try {
            processNbaScoresFetchJob(job);
        } catch (Exception ex) {
            log.error("Failed to process NbaScoresFetchJob id {}: {}", id, ex.getMessage());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));
    }

    @GetMapping("/jobs/fetch-scores/{id}")
    public ResponseEntity<?> getNbaScoresFetchJobById(@PathVariable String id) {
        NbaScoresFetchJob job = nbaScoresFetchJobCache.get(id);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NbaScoresFetchJob not found"));
        }
        return ResponseEntity.ok(job);
    }

    // ------------------ Subscriber Endpoints ------------------

    @PostMapping("/subscribers")
    public ResponseEntity<Map<String, String>> createSubscriber(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isBlank()) {
            log.error("Missing or blank email");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "email is required"));
        }
        if (!isValidEmail(email)) {
            log.error("Invalid email format: {}", email);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid email format"));
        }

        // Check uniqueness
        boolean exists = subscriberCache.values().stream()
                .anyMatch(sub -> sub.getEmail().equalsIgnoreCase(email) && "ACTIVE".equals(sub.getStatus()));
        if (exists) {
            log.error("Email already subscribed: {}", email);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "email already subscribed"));
        }

        Subscriber subscriber = new Subscriber();
        String id = "subscriber-" + subscriberIdCounter.getAndIncrement();
        subscriber.setId(id);
        subscriber.setEmail(email);
        subscriber.setSubscriptionDate(new Date());
        subscriber.setStatus("ACTIVE");

        subscriberCache.put(id, subscriber);

        // Trigger processing
        try {
            processSubscriber(subscriber);
        } catch (Exception ex) {
            log.error("Failed to process Subscriber id {}: {}", id, ex.getMessage());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));
    }

    @GetMapping("/subscribers/{id}")
    public ResponseEntity<?> getSubscriberById(@PathVariable String id) {
        Subscriber subscriber = subscriberCache.get(id);
        if (subscriber == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Subscriber not found"));
        }
        return ResponseEntity.ok(subscriber);
    }

    // ------------------ NbaGame Endpoints ------------------

    @GetMapping("/games/{date}")
    public ResponseEntity<?> getGamesByDate(@PathVariable String date) {
        LocalDate queryDate;
        try {
            queryDate = LocalDate.parse(date);
        } catch (Exception e) {
            log.error("Invalid date format: {}", date);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "date must be in YYYY-MM-DD format"));
        }

        List<NbaGame> games = new ArrayList<>();
        for (NbaGame game : nbaGameCache.values()) {
            if (queryDate.equals(game.getGameDate())) {
                games.add(game);
            }
        }

        return ResponseEntity.ok(games);
    }

    @GetMapping("/games/all")
    public ResponseEntity<?> getAllGames(@RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size) {
        List<NbaGame> allGames = new ArrayList<>(nbaGameCache.values());
        if (page != null && size != null && page > 0 && size > 0) {
            int fromIndex = (page - 1) * size;
            if (fromIndex >= allGames.size()) {
                return ResponseEntity.ok(Collections.emptyList());
            }
            int toIndex = Math.min(fromIndex + size, allGames.size());
            return ResponseEntity.ok(allGames.subList(fromIndex, toIndex));
        }
        return ResponseEntity.ok(allGames);
    }

    // ------------------ Processing Methods ------------------

    private void processNbaScoresFetchJob(NbaScoresFetchJob job) {
        log.info("Processing NbaScoresFetchJob with ID: {}", job.getId());

        if (job.getScheduledDate().isAfter(LocalDate.now())) {
            job.setStatus("FAILED");
            job.setSummary("Scheduled date cannot be in the future");
            log.error("Job {} failed: scheduledDate in future", job.getId());
            return;
        }

        job.setStatus("IN_PROGRESS");

        String apiKey = "test"; // Replace with valid API key or config
        String url = String.format("https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s", job.getScheduledDate(), apiKey);

        try {
            // Call external API
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.isArray()) {
                int gamesCount = 0;
                for (JsonNode node : root) {
                    NbaGame game = new NbaGame();
                    String gameId = "game-" + nbaGameIdCounter.getAndIncrement();
                    game.setId(gameId);
                    game.setGameDate(job.getScheduledDate());
                    game.setHomeTeam(node.path("HomeTeam").asText());
                    game.setAwayTeam(node.path("AwayTeam").asText());
                    game.setHomeScore(node.path("HomeTeamScore").isNull() ? null : node.path("HomeTeamScore").asInt());
                    game.setAwayScore(node.path("AwayTeamScore").isNull() ? null : node.path("AwayTeamScore").asInt());
                    game.setStatus(node.path("Status").asText(null));
                    nbaGameCache.put(gameId, game);
                    gamesCount++;
                }
                job.setStatus("COMPLETED");
                job.setSummary("Fetched " + gamesCount + " games for " + job.getScheduledDate());

                // Notify subscribers
                StringBuilder summaryBuilder = new StringBuilder();
                summaryBuilder.append("NBA Scores for ").append(job.getScheduledDate()).append(":\n");

                for (JsonNode node : root) {
                    summaryBuilder.append(node.path("HomeTeam").asText()).append(" ")
                            .append(node.path("HomeTeamScore").isNull() ? "-" : node.path("HomeTeamScore").asInt())
                            .append(" - ")
                            .append(node.path("AwayTeamScore").isNull() ? "-" : node.path("AwayTeamScore").asInt())
                            .append(" ")
                            .append(node.path("AwayTeam").asText()).append("\n");
                }

                subscriberCache.values().stream()
                        .filter(s -> "ACTIVE".equals(s.getStatus()))
                        .forEach(s -> {
                            log.info("Sending email to {} with summary:\n{}", s.getEmail(), summaryBuilder.toString());
                            // Implement real email sending here if available
                        });

            } else {
                job.setStatus("FAILED");
                job.setSummary("Unexpected API response format");
                log.error("Unexpected API response format for job {}", job.getId());
            }
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setSummary("Exception during fetch: " + e.getMessage());
            log.error("Error processing job {}: {}", job.getId(), e.getMessage());
        }
    }

    private void processSubscriber(Subscriber subscriber) {
        log.info("Processing Subscriber with ID: {}", subscriber.getId());
        // No additional processing currently
    }

    private boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        return Pattern.compile(emailRegex).matcher(email).matches();
    }

    // ------------------ Entity Classes for Prototype ------------------

    public static class NbaScoresFetchJob {
        private String id;
        private LocalDate scheduledDate;
        private LocalTime fetchTimeUTC;
        private String status;
        private String summary;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public LocalDate getScheduledDate() { return scheduledDate; }
        public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }

        public LocalTime getFetchTimeUTC() { return fetchTimeUTC; }
        public void setFetchTimeUTC(LocalTime fetchTimeUTC) { this.fetchTimeUTC = fetchTimeUTC; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
    }

    public static class NbaGame {
        private String id;
        private LocalDate gameDate;
        private String homeTeam;
        private String awayTeam;
        private Integer homeScore;
        private Integer awayScore;
        private String status;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public LocalDate getGameDate() { return gameDate; }
        public void setGameDate(LocalDate gameDate) { this.gameDate = gameDate; }

        public String getHomeTeam() { return homeTeam; }
        public void setHomeTeam(String homeTeam) { this.homeTeam = homeTeam; }

        public String getAwayTeam() { return awayTeam; }
        public void setAwayTeam(String awayTeam) { this.awayTeam = awayTeam; }

        public Integer getHomeScore() { return homeScore; }
        public void setHomeScore(Integer homeScore) { this.homeScore = homeScore; }

        public Integer getAwayScore() { return awayScore; }
        public void setAwayScore(Integer awayScore) { this.awayScore = awayScore; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class Subscriber {
        private String id;
        private String email;
        private Date subscriptionDate;
        private String status;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public Date getSubscriptionDate() { return subscriptionDate; }
        public void setSubscriptionDate(Date subscriptionDate) { this.subscriptionDate = subscriptionDate; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
