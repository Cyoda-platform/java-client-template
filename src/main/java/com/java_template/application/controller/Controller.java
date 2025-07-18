package com.java_template.prototype;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.GameScoreFetchJob;
import com.java_template.application.entity.NbaGameScore;
import com.java_template.application.entity.Subscriber;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/gameScoreFetchJob")
@Slf4j
@Validated
public class GameScoreFetchJobController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GameScoreFetchJobController.class);

    private final EntityService entityService;

    public GameScoreFetchJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    public static class GameScoreFetchJobDTO {
        @NotBlank
        private String status;

        @NotBlank
        private Long scheduledDate; // use epoch millis for date validation
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<?>> createGameScoreFetchJob(@RequestBody @Valid GameScoreFetchJobDTO dto) {
        GameScoreFetchJob job = new GameScoreFetchJob();
        job.setStatus(dto.getStatus());
        job.setScheduledDate(new Date(dto.getScheduledDate()));
        job.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        job.setUpdatedAt(new Timestamp(System.currentTimeMillis()));

        return entityService.addItem("GameScoreFetchJob", ENTITY_VERSION, job)
                .thenApply(technicalId -> {
                    job.setTechnicalId(technicalId);
                    job.setId(technicalId.toString());
                    logger.info("Created GameScoreFetchJob with technicalId: {}", technicalId);
                    processGameScoreFetchJob(job);
                    return ResponseEntity.status(HttpStatus.CREATED).body(job);
                });
    }

    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> getGameScoreFetchJob(@PathVariable @NotBlank String id) {
        UUID technicalId = UUID.fromString(id);
        return entityService.getItem("GameScoreFetchJob", ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        logger.error("GameScoreFetchJob not found for technicalId: {}", id);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("GameScoreFetchJob not found");
                    }
                    return ResponseEntity.ok(objectNode);
                });
    }

    @PutMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> updateGameScoreFetchJob(@PathVariable @NotBlank String id,
                                                                        @RequestBody @Valid GameScoreFetchJobDTO dto) {
        UUID technicalId = UUID.fromString(id);
        GameScoreFetchJob job = new GameScoreFetchJob();
        job.setStatus(dto.getStatus());
        job.setScheduledDate(new Date(dto.getScheduledDate()));
        job.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        job.setTechnicalId(technicalId);
        job.setId(id);

        return entityService.updateItem("GameScoreFetchJob", ENTITY_VERSION, technicalId, job)
                .thenApply(updatedId -> {
                    logger.info("Updated GameScoreFetchJob with technicalId: {}", updatedId);
                    processGameScoreFetchJob(job);
                    return ResponseEntity.ok(job);
                });
    }

    @DeleteMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> deleteGameScoreFetchJob(@PathVariable @NotBlank String id) {
        UUID technicalId = UUID.fromString(id);
        return entityService.deleteItem("GameScoreFetchJob", ENTITY_VERSION, technicalId)
                .thenApply(deletedId -> {
                    logger.info("Deleted GameScoreFetchJob with technicalId: {}", deletedId);
                    return ResponseEntity.ok("Deleted GameScoreFetchJob with technicalId: " + deletedId);
                });
    }

    private void processGameScoreFetchJob(GameScoreFetchJob job) {
        logger.info("Processing GameScoreFetchJob with technicalId: {}", job.getTechnicalId());
        // TODO: Implement actual business logic per requirements:
        // - Validate scheduledDate
        // - Fetch NBA game scores from external API
        // - Save/update NbaGameScore entities via NbaGameScoreController or entityService
        // - Update job status
        // - Trigger notifications if necessary
    }
}

@RestController
@RequestMapping(path = "/nbaGameScore")
@Slf4j
@Validated
class NbaGameScoreController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NbaGameScoreController.class);

    private final EntityService entityService;

    public NbaGameScoreController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    public static class NbaGameScoreDTO {
        @NotBlank
        private String gameId;

        @NotBlank
        private Long date; // epoch millis

        @NotBlank
        private String homeTeam;

        @NotBlank
        private String awayTeam;

        @NotBlank
        private Integer homeTeamScore;

        @NotBlank
        private Integer awayTeamScore;

        @NotBlank
        private String status;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<?>> createNbaGameScore(@RequestBody @Valid NbaGameScoreDTO dto) {
        NbaGameScore gameScore = new NbaGameScore();
        gameScore.setGameId(dto.getGameId());
        gameScore.setDate(new Date(dto.getDate()));
        gameScore.setHomeTeam(dto.getHomeTeam());
        gameScore.setAwayTeam(dto.getAwayTeam());
        gameScore.setHomeTeamScore(dto.getHomeTeamScore());
        gameScore.setAwayTeamScore(dto.getAwayTeamScore());
        gameScore.setStatus(dto.getStatus());

        return entityService.addItem("NbaGameScore", ENTITY_VERSION, gameScore)
                .thenApply(technicalId -> {
                    gameScore.setTechnicalId(technicalId);
                    gameScore.setId(technicalId.toString());
                    logger.info("Created NbaGameScore with technicalId: {}", technicalId);
                    processNbaGameScore(gameScore);
                    return ResponseEntity.status(HttpStatus.CREATED).body(gameScore);
                });
    }

    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> getNbaGameScore(@PathVariable @NotBlank String id) {
        UUID technicalId = UUID.fromString(id);
        return entityService.getItem("NbaGameScore", ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        logger.error("NbaGameScore not found for technicalId: {}", id);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("NbaGameScore not found");
                    }
                    return ResponseEntity.ok(objectNode);
                });
    }

    @PutMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> updateNbaGameScore(@PathVariable @NotBlank String id,
                                                                   @RequestBody @Valid NbaGameScoreDTO dto) {
        UUID technicalId = UUID.fromString(id);
        NbaGameScore gameScore = new NbaGameScore();
        gameScore.setGameId(dto.getGameId());
        gameScore.setDate(new Date(dto.getDate()));
        gameScore.setHomeTeam(dto.getHomeTeam());
        gameScore.setAwayTeam(dto.getAwayTeam());
        gameScore.setHomeTeamScore(dto.getHomeTeamScore());
        gameScore.setAwayTeamScore(dto.getAwayTeamScore());
        gameScore.setStatus(dto.getStatus());
        gameScore.setTechnicalId(technicalId);
        gameScore.setId(id);

        return entityService.updateItem("NbaGameScore", ENTITY_VERSION, technicalId, gameScore)
                .thenApply(updatedId -> {
                    logger.info("Updated NbaGameScore with technicalId: {}", updatedId);
                    processNbaGameScore(gameScore);
                    return ResponseEntity.ok(gameScore);
                });
    }

    @DeleteMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> deleteNbaGameScore(@PathVariable @NotBlank String id) {
        UUID technicalId = UUID.fromString(id);
        return entityService.deleteItem("NbaGameScore", ENTITY_VERSION, technicalId)
                .thenApply(deletedId -> {
                    logger.info("Deleted NbaGameScore with technicalId: {}", deletedId);
                    return ResponseEntity.ok("Deleted NbaGameScore with technicalId: " + deletedId);
                });
    }

    private void processNbaGameScore(NbaGameScore gameScore) {
        logger.info("Processing NbaGameScore with technicalId: {}", gameScore.getTechnicalId());
        // TODO: Implement actual business logic per requirements:
        // - Validate completeness of score data
        // - Update status to PROCESSED
        // - Trigger notifications for subscribers interested in teams
    }
}

@RestController
@RequestMapping(path = "/subscriber")
@Slf4j
@Validated
class SubscriberController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    public static class SubscriberDTO {
        @NotBlank
        private String contactInfo;

        @NotBlank
        private String status;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<?>> createSubscriber(@RequestBody @Valid SubscriberDTO dto) {
        Subscriber subscriber = new Subscriber();
        subscriber.setContactInfo(dto.getContactInfo());
        subscriber.setStatus(dto.getStatus());

        return entityService.addItem("Subscriber", ENTITY_VERSION, subscriber)
                .thenApply(technicalId -> {
                    subscriber.setTechnicalId(technicalId);
                    subscriber.setId(technicalId.toString());
                    logger.info("Created Subscriber with technicalId: {}", technicalId);
                    processSubscriber(subscriber);
                    return ResponseEntity.status(HttpStatus.CREATED).body(subscriber);
                });
    }

    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> getSubscriber(@PathVariable @NotBlank String id) {
        UUID technicalId = UUID.fromString(id);
        return entityService.getItem("Subscriber", ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        logger.error("Subscriber not found for technicalId: {}", id);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
                    }
                    return ResponseEntity.ok(objectNode);
                });
    }

    @PutMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> updateSubscriber(@PathVariable @NotBlank String id,
                                                                 @RequestBody @Valid SubscriberDTO dto) {
        UUID technicalId = UUID.fromString(id);
        Subscriber subscriber = new Subscriber();
        subscriber.setContactInfo(dto.getContactInfo());
        subscriber.setStatus(dto.getStatus());
        subscriber.setTechnicalId(technicalId);
        subscriber.setId(id);

        return entityService.updateItem("Subscriber", ENTITY_VERSION, technicalId, subscriber)
                .thenApply(updatedId -> {
                    logger.info("Updated Subscriber with technicalId: {}", updatedId);
                    processSubscriber(subscriber);
                    return ResponseEntity.ok(subscriber);
                });
    }

    @DeleteMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> deleteSubscriber(@PathVariable @NotBlank String id) {
        UUID technicalId = UUID.fromString(id);
        return entityService.deleteItem("Subscriber", ENTITY_VERSION, technicalId)
                .thenApply(deletedId -> {
                    logger.info("Deleted Subscriber with technicalId: {}", deletedId);
                    return ResponseEntity.ok("Deleted Subscriber with technicalId: " + deletedId);
                });
    }

    private void processSubscriber(Subscriber subscriber) {
        logger.info("Processing Subscriber with technicalId: {}", subscriber.getTechnicalId());
        // TODO: Implement actual business logic per requirements:
        // - Validate contactInfo and preferences
        // - No further automatic processing unless triggered by GameScore events
    }
}