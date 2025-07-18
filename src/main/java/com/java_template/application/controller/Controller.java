package com.java_template.prototype;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.GameScoreFetchJob;
import com.java_template.application.entity.NbaGameScore;
import com.java_template.application.entity.Subscriber;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDate;
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
    private final ObjectMapper objectMapper;

    public GameScoreFetchJobController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Data
    public static class GameScoreFetchJobDTO {
        @NotBlank
        private String status;

        @NotBlank
        private Long scheduledDate; // use epoch millis for date validation
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<?>> createGameScoreFetchJob(@RequestBody @Valid GameScoreFetchJobDTO dto) throws JsonProcessingException {
        GameScoreFetchJob job = new GameScoreFetchJob();
        job.setStatus(dto.getStatus());
        job.setScheduledDate(LocalDate.ofEpochDay(dto.getScheduledDate() / (24 * 60 * 60 * 1000))); // convert epoch millis to LocalDate
        job.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        job.setUpdatedAt(new Timestamp(System.currentTimeMillis()));

        return entityService.addItem("gameScoreFetchJob", ENTITY_VERSION, job)
                .thenApply(technicalId -> {
                    job.setTechnicalId(technicalId);
                    job.setId(technicalId.toString());
                    logger.info("Created GameScoreFetchJob with technicalId: {}", technicalId);
                    return ResponseEntity.status(HttpStatus.CREATED).body(job);
                });
    }

    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> getGameScoreFetchJob(@PathVariable @NotBlank String id) throws JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        return entityService.getItem("gameScoreFetchJob", ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        logger.error("GameScoreFetchJob not found for technicalId: {}", id);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("GameScoreFetchJob not found");
                    }
                    try {
                        GameScoreFetchJob job = objectMapper.treeToValue(objectNode, GameScoreFetchJob.class);
                        return ResponseEntity.ok(job);
                    } catch (JsonProcessingException e) {
                        logger.error("Error processing GameScoreFetchJob JSON", e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing response");
                    }
                });
    }

    @PutMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> updateGameScoreFetchJob(@PathVariable @NotBlank String id,
                                                                        @RequestBody @Valid GameScoreFetchJobDTO dto) throws JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        GameScoreFetchJob job = new GameScoreFetchJob();
        job.setStatus(dto.getStatus());
        job.setScheduledDate(LocalDate.ofEpochDay(dto.getScheduledDate() / (24 * 60 * 60 * 1000))); // convert epoch millis to LocalDate
        job.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        job.setTechnicalId(technicalId);
        job.setId(id);

        return entityService.updateItem("gameScoreFetchJob", ENTITY_VERSION, technicalId, job)
                .thenApply(updatedId -> {
                    logger.info("Updated GameScoreFetchJob with technicalId: {}", updatedId);
                    return ResponseEntity.ok(job);
                });
    }

    @DeleteMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> deleteGameScoreFetchJob(@PathVariable @NotBlank String id) {
        UUID technicalId = UUID.fromString(id);
        return entityService.deleteItem("gameScoreFetchJob", ENTITY_VERSION, technicalId)
                .thenApply(deletedId -> {
                    logger.info("Deleted GameScoreFetchJob with technicalId: {}", deletedId);
                    return ResponseEntity.ok("Deleted GameScoreFetchJob with technicalId: " + deletedId);
                });
    }
}

@RestController
@RequestMapping(path = "/nbaGameScore")
@Slf4j
@Validated
class NbaGameScoreController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NbaGameScoreController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public NbaGameScoreController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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
    public CompletableFuture<ResponseEntity<?>> createNbaGameScore(@RequestBody @Valid NbaGameScoreDTO dto) throws JsonProcessingException {
        NbaGameScore gameScore = new NbaGameScore();
        gameScore.setGameId(dto.getGameId());
        gameScore.setDate(LocalDate.ofEpochDay(dto.getDate() / (24 * 60 * 60 * 1000))); // convert epoch millis to LocalDate
        gameScore.setHomeTeam(dto.getHomeTeam());
        gameScore.setAwayTeam(dto.getAwayTeam());
        gameScore.setHomeScore(dto.getHomeTeamScore());
        gameScore.setAwayScore(dto.getAwayTeamScore());
        gameScore.setStatus(dto.getStatus());

        return entityService.addItem("nbaGameScore", ENTITY_VERSION, gameScore)
                .thenApply(technicalId -> {
                    gameScore.setTechnicalId(technicalId);
                    gameScore.setId(technicalId.toString());
                    logger.info("Created NbaGameScore with technicalId: {}", technicalId);
                    return ResponseEntity.status(HttpStatus.CREATED).body(gameScore);
                });
    }

    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> getNbaGameScore(@PathVariable @NotBlank String id) throws JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        return entityService.getItem("nbaGameScore", ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        logger.error("NbaGameScore not found for technicalId: {}", id);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("NbaGameScore not found");
                    }
                    try {
                        NbaGameScore gameScore = objectMapper.treeToValue(objectNode, NbaGameScore.class);
                        return ResponseEntity.ok(gameScore);
                    } catch (JsonProcessingException e) {
                        logger.error("Error processing NbaGameScore JSON", e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing response");
                    }
                });
    }

    @PutMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> updateNbaGameScore(@PathVariable @NotBlank String id,
                                                                   @RequestBody @Valid NbaGameScoreDTO dto) throws JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        NbaGameScore gameScore = new NbaGameScore();
        gameScore.setGameId(dto.getGameId());
        gameScore.setDate(LocalDate.ofEpochDay(dto.getDate() / (24 * 60 * 60 * 1000))); // convert epoch millis to LocalDate
        gameScore.setHomeTeam(dto.getHomeTeam());
        gameScore.setAwayTeam(dto.getAwayTeam());
        gameScore.setHomeScore(dto.getHomeTeamScore());
        gameScore.setAwayScore(dto.getAwayTeamScore());
        gameScore.setStatus(dto.getStatus());
        gameScore.setTechnicalId(technicalId);
        gameScore.setId(id);

        return entityService.updateItem("nbaGameScore", ENTITY_VERSION, technicalId, gameScore)
                .thenApply(updatedId -> {
                    logger.info("Updated NbaGameScore with technicalId: {}", updatedId);
                    return ResponseEntity.ok(gameScore);
                });
    }

    @DeleteMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> deleteNbaGameScore(@PathVariable @NotBlank String id) {
        UUID technicalId = UUID.fromString(id);
        return entityService.deleteItem("nbaGameScore", ENTITY_VERSION, technicalId)
                .thenApply(deletedId -> {
                    logger.info("Deleted NbaGameScore with technicalId: {}", deletedId);
                    return ResponseEntity.ok("Deleted NbaGameScore with technicalId: " + deletedId);
                });
    }
}

@RestController
@RequestMapping(path = "/subscriber")
@Slf4j
@Validated
class SubscriberController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SubscriberController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Data
    public static class SubscriberDTO {
        @NotBlank
        private String contactInfo;

        private String[] teamPreferences; // optional if we want to allow setting this in DTO

        @NotBlank
        private String status;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<?>> createSubscriber(@RequestBody @Valid SubscriberDTO dto) throws JsonProcessingException {
        Subscriber subscriber = new Subscriber();
        subscriber.setContactInfo(dto.getContactInfo());
        if(dto.getTeamPreferences() != null) {
            subscriber.setTeamPreferences(java.util.Arrays.asList(dto.getTeamPreferences()));
        }
        subscriber.setStatus(dto.getStatus());

        return entityService.addItem("subscriber", ENTITY_VERSION, subscriber)
                .thenApply(technicalId -> {
                    subscriber.setTechnicalId(technicalId);
                    subscriber.setId(technicalId.toString());
                    logger.info("Created Subscriber with technicalId: {}", technicalId);
                    return ResponseEntity.status(HttpStatus.CREATED).body(subscriber);
                });
    }

    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> getSubscriber(@PathVariable @NotBlank String id) throws JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        return entityService.getItem("subscriber", ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        logger.error("Subscriber not found for technicalId: {}", id);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
                    }
                    try {
                        Subscriber subscriber = objectMapper.treeToValue(objectNode, Subscriber.class);
                        return ResponseEntity.ok(subscriber);
                    } catch (JsonProcessingException e) {
                        logger.error("Error processing Subscriber JSON", e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing response");
                    }
                });
    }

    @PutMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> updateSubscriber(@PathVariable @NotBlank String id,
                                                                 @RequestBody @Valid SubscriberDTO dto) throws JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        Subscriber subscriber = new Subscriber();
        subscriber.setContactInfo(dto.getContactInfo());
        if(dto.getTeamPreferences() != null) {
            subscriber.setTeamPreferences(java.util.Arrays.asList(dto.getTeamPreferences()));
        }
        subscriber.setStatus(dto.getStatus());
        subscriber.setTechnicalId(technicalId);
        subscriber.setId(id);

        return entityService.updateItem("subscriber", ENTITY_VERSION, technicalId, subscriber)
                .thenApply(updatedId -> {
                    logger.info("Updated Subscriber with technicalId: {}", updatedId);
                    return ResponseEntity.ok(subscriber);
                });
    }

    @DeleteMapping("/{id}")
    public CompletableFuture<ResponseEntity<?>> deleteSubscriber(@PathVariable @NotBlank String id) {
        UUID technicalId = UUID.fromString(id);
        return entityService.deleteItem("subscriber", ENTITY_VERSION, technicalId)
                .thenApply(deletedId -> {
                    logger.info("Deleted Subscriber with technicalId: {}", deletedId);
                    return ResponseEntity.ok("Deleted Subscriber with technicalId: " + deletedId);
                });
    }
}