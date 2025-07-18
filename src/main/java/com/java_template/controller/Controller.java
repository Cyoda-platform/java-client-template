package com.java_template.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.GameScore;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Subscription;
import com.java_template.application.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Validated
@RestController
@RequestMapping(path = "/prototype")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    // ==================== JOB ENDPOINTS ====================

    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody @Valid JobCreateRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received request to create Job: {}", request);
        try {
            Job job = new Job();
            job.setId(request.getName()); // mapping name to id for business ID
            job.setJobId(request.getName());
            job.setSchedule(request.getDescription()); // using description as schedule for example
            UUID technicalId = UUID.randomUUID();
            job.setTechnicalId(technicalId);

            String id = entityService.addItem("job", ENTITY_VERSION, job).get();
            logger.info("Job created with id: {}", id);
            return ResponseEntity.ok().body(new IdResponse(id, "Job created and processed"));
        } catch (Exception e) {
            logger.error("Error creating Job", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create Job");
        }
    }

    @GetMapping("/jobs")
    public ResponseEntity<Job> getJob(@Valid @ModelAttribute JobGetRequest request) throws JsonProcessingException {
        logger.info("Fetching Job with id: {}", request.getId());
        Job job = null;
        try {
            UUID uuid = UUID.fromString(request.getId());
            ObjectNode entityJson = entityService.getItem("job", ENTITY_VERSION, uuid).get();
            job = objectMapper.treeToValue(entityJson, Job.class);
        } catch (Exception e) {
            logger.error("Job not found with id: {}", request.getId());
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        return ResponseEntity.ok(job);
    }

    @PutMapping("/jobs")
    public ResponseEntity<?> updateJob(@RequestBody @Valid JobUpdateRequest request) throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Updating Job with id: {}", request.getId());
        try {
            Job job = new Job();
            job.setTechnicalId(UUID.fromString(request.getId()));
            job.setId(request.getName());
            job.setJobId(request.getName());
            job.setSchedule(request.getDescription());

            entityService.updateItem("job", ENTITY_VERSION, job).get();
        } catch (Exception e) {
            logger.error("Error updating Job with id: {}", request.getId(), e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update Job");
        }
        return ResponseEntity.ok(new IdResponse(request.getId(), "Job updated and processed"));
    }

    @DeleteMapping("/jobs")
    public ResponseEntity<?> deleteJob(@Valid @ModelAttribute JobDeleteRequest request) throws ExecutionException, InterruptedException {
        logger.info("Deleting Job with id: {}", request.getId());
        try {
            UUID deletedId = entityService.deleteItem("job", ENTITY_VERSION, UUID.fromString(request.getId())).get();
        } catch (Exception e) {
            logger.error("Job not found with id: {}", request.getId());
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        return ResponseEntity.ok(new StatusResponse("Job deleted"));
    }

    // ==================== SUBSCRIPTION ENDPOINTS ====================

    @PostMapping("/subscriptions")
    public ResponseEntity<?> createSubscription(@RequestBody @Valid SubscriptionCreateRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received request to create Subscription: {}", request);
        try {
            Subscription sub = new Subscription();
            sub.setUserEmail(request.getEmail());
            // frequency is not a field in entity, so not set here
            UUID technicalId = UUID.randomUUID();
            sub.setTechnicalId(technicalId);

            String id = entityService.addItem("subscription", ENTITY_VERSION, sub).get();
            logger.info("Subscription created with id: {}", id);
            return ResponseEntity.ok(new IdResponse(id, "Subscription created and processed"));
        } catch (Exception e) {
            logger.error("Error creating Subscription", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create Subscription");
        }
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<Subscription> getSubscription(@Valid @ModelAttribute SubscriptionGetRequest request) throws JsonProcessingException {
        logger.info("Fetching Subscription with id: {}", request.getId());
        Subscription sub = null;
        try {
            UUID uuid = UUID.fromString(request.getId());
            ObjectNode entityJson = entityService.getItem("subscription", ENTITY_VERSION, uuid).get();
            sub = objectMapper.treeToValue(entityJson, Subscription.class);
        } catch (Exception e) {
            logger.error("Subscription not found with id: {}", request.getId());
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Subscription not found");
        }
        return ResponseEntity.ok(sub);
    }

    @PutMapping("/subscriptions")
    public ResponseEntity<?> updateSubscription(@RequestBody @Valid SubscriptionUpdateRequest request) throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Updating Subscription with id: {}", request.getId());
        try {
            Subscription sub = new Subscription();
            sub.setTechnicalId(UUID.fromString(request.getId()));
            sub.setUserEmail(request.getEmail());
            // frequency not present in entity, so not set here

            entityService.updateItem("subscription", ENTITY_VERSION, sub).get();
        } catch (Exception e) {
            logger.error("Error updating Subscription with id: {}", request.getId(), e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update Subscription");
        }
        return ResponseEntity.ok(new IdResponse(request.getId(), "Subscription updated and processed"));
    }

    @DeleteMapping("/subscriptions")
    public ResponseEntity<?> deleteSubscription(@Valid @ModelAttribute SubscriptionDeleteRequest request) throws ExecutionException, InterruptedException {
        logger.info("Deleting Subscription with id: {}", request.getId());
        try {
            UUID deletedId = entityService.deleteItem("subscription", ENTITY_VERSION, UUID.fromString(request.getId())).get();
        } catch (Exception e) {
            logger.error("Subscription not found with id: {}", request.getId());
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Subscription not found");
        }
        return ResponseEntity.ok(new StatusResponse("Subscription deleted"));
    }

    // ==================== GAMESCORE ENDPOINTS ====================

    @PostMapping("/gamescores")
    public ResponseEntity<?> createGameScore(@RequestBody @Valid GameScoreCreateRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received request to create GameScore: {}", request);
        try {
            GameScore gs = new GameScore();
            gs.setGameId(request.getGameId());
            gs.setTeamHome(request.getHomeTeam());
            gs.setTeamAway(request.getAwayTeam());
            gs.setScoreHome(request.getHomeScore());
            gs.setScoreAway(request.getAwayScore());
            UUID technicalId = UUID.randomUUID();
            gs.setTechnicalId(technicalId);

            String id = entityService.addItem("gameScore", ENTITY_VERSION, gs).get();
            logger.info("GameScore created with id: {}", id);
            return ResponseEntity.ok(new IdResponse(id, "GameScore created and processed"));
        } catch (Exception e) {
            logger.error("Error creating GameScore", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create GameScore");
        }
    }

    @GetMapping("/gamescores")
    public ResponseEntity<GameScore> getGameScore(@Valid @ModelAttribute GameScoreGetRequest request) throws JsonProcessingException {
        logger.info("Fetching GameScore with id: {}", request.getId());
        GameScore gs = null;
        try {
            UUID uuid = UUID.fromString(request.getId());
            ObjectNode entityJson = entityService.getItem("gameScore", ENTITY_VERSION, uuid).get();
            gs = objectMapper.treeToValue(entityJson, GameScore.class);
        } catch (Exception e) {
            logger.error("GameScore not found with id: {}", request.getId());
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "GameScore not found");
        }
        return ResponseEntity.ok(gs);
    }

    @PutMapping("/gamescores")
    public ResponseEntity<?> updateGameScore(@RequestBody @Valid GameScoreUpdateRequest request) throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Updating GameScore with id: {}", request.getId());
        try {
            GameScore gs = new GameScore();
            gs.setTechnicalId(UUID.fromString(request.getId()));
            gs.setGameId(request.getGameId());
            gs.setTeamHome(request.getHomeTeam());
            gs.setTeamAway(request.getAwayTeam());
            gs.setScoreHome(request.getHomeScore());
            gs.setScoreAway(request.getAwayScore());

            entityService.updateItem("gameScore", ENTITY_VERSION, gs).get();
        } catch (Exception e) {
            logger.error("Error updating GameScore with id: {}", request.getId(), e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update GameScore");
        }
        return ResponseEntity.ok(new IdResponse(request.getId(), "GameScore updated and processed"));
    }

    @DeleteMapping("/gamescores")
    public ResponseEntity<?> deleteGameScore(@Valid @ModelAttribute GameScoreDeleteRequest request) throws ExecutionException, InterruptedException {
        logger.info("Deleting GameScore with id: {}", request.getId());
        try {
            UUID deletedId = entityService.deleteItem("gameScore", ENTITY_VERSION, UUID.fromString(request.getId())).get();
        } catch (Exception e) {
            logger.error("GameScore not found with id: {}", request.getId());
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "GameScore not found");
        }
        return ResponseEntity.ok(new StatusResponse("GameScore deleted"));
    }

    // ==================== REQUEST & RESPONSE DTOs ====================

    public static class JobCreateRequest {
        @NotBlank
        @Size(max = 100)
        private String name;

        @Size(max = 500)
        private String description;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class JobGetRequest {
        @NotBlank
        private String id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    public static class JobUpdateRequest {
        @NotBlank
        private String id;

        @NotBlank
        @Size(max = 100)
        private String name;

        @Size(max = 500)
        private String description;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class JobDeleteRequest {
        @NotBlank
        private String id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    public static class SubscriptionCreateRequest {
        @NotBlank
        @javax.validation.constraints.Email
        private String email;

        @NotBlank
        @Pattern(regexp = "daily|weekly|monthly", flags = Pattern.Flag.CASE_INSENSITIVE)
        private String frequency;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getFrequency() { return frequency; }
        public void setFrequency(String frequency) { this.frequency = frequency; }
    }

    public static class SubscriptionGetRequest {
        @NotBlank
        private String id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    public static class SubscriptionUpdateRequest {
        @NotBlank
        private String id;

        @NotBlank
        @javax.validation.constraints.Email
        private String email;

        @NotBlank
        @Pattern(regexp = "daily|weekly|monthly", flags = Pattern.Flag.CASE_INSENSITIVE)
        private String frequency;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getFrequency() { return frequency; }
        public void setFrequency(String frequency) { this.frequency = frequency; }
    }

    public static class SubscriptionDeleteRequest {
        @NotBlank
        private String id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    public static class GameScoreCreateRequest {
        @NotBlank
        private String gameId;

        @NotBlank
        private String homeTeam;

        @NotBlank
        private String awayTeam;

        @Min(0)
        private int homeScore;

        @Min(0)
        private int awayScore;

        public String getGameId() { return gameId; }
        public void setGameId(String gameId) { this.gameId = gameId; }
        public String getHomeTeam() { return homeTeam; }
        public void setHomeTeam(String homeTeam) { this.homeTeam = homeTeam; }
        public String getAwayTeam() { return awayTeam; }
        public void setAwayTeam(String awayTeam) { this.awayTeam = awayTeam; }
        public int getHomeScore() { return homeScore; }
        public void setHomeScore(int homeScore) { this.homeScore = homeScore; }
        public int getAwayScore() { return awayScore; }
        public void setAwayScore(int awayScore) { this.awayScore = awayScore; }
    }

    public static class GameScoreGetRequest {
        @NotBlank
        private String id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    public static class GameScoreUpdateRequest {
        @NotBlank
        private String id;

        @NotBlank
        private String gameId;

        @NotBlank
        private String homeTeam;

        @NotBlank
        private String awayTeam;

        @Min(0)
        private int homeScore;

        @Min(0)
        private int awayScore;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getGameId() { return gameId; }
        public void setGameId(String gameId) { this.gameId = gameId; }
        public String getHomeTeam() { return homeTeam; }
        public void setHomeTeam(String homeTeam) { this.homeTeam = homeTeam; }
        public String getAwayTeam() { return awayTeam; }
        public void setAwayTeam(String awayTeam) { this.awayTeam = awayTeam; }
        public int getHomeScore() { return homeScore; }
        public void setHomeScore(int homeScore) { this.homeScore = homeScore; }
        public int getAwayScore() { return awayScore; }
        public void setAwayScore(int awayScore) { this.awayScore = awayScore; }
    }

    public static class GameScoreDeleteRequest {
        @NotBlank
        private String id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    public static class IdResponse {
        private final String id;
        private final String status;

        public IdResponse(String id, String status) {
            this.id = id;
            this.status = status;
        }

        public String getId() { return id; }
        public String getStatus() { return status; }
    }

    public static class StatusResponse {
        private final String status;

        public StatusResponse(String status) {
            this.status = status;
        }

        public String getStatus() { return status; }
    }
}