package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.GameScore;
import com.java_template.application.entity.NbaScoreJob;
import com.java_template.application.entity.Subscriber;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    private static final String NBA_SCORE_JOB_MODEL = "NbaScoreJob";
    private static final String SUBSCRIBER_MODEL = "Subscriber";
    private static final String GAME_SCORE_MODEL = "GameScore";

    // --- NbaScoreJob Endpoints ---

    @PostMapping("/nbaScoreJob")
    public ResponseEntity<?> createNbaScoreJob(@RequestBody NbaScoreJob job) throws ExecutionException, InterruptedException {
        if (job == null || job.getDate() == null) {
            log.error("Invalid NbaScoreJob creation request: missing date");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing or invalid required fields");
        }
        job.setStatus("PENDING");
        job.setCreatedAt(new Date(System.currentTimeMillis()));
        // technicalId will be set by entityService, ignore setting manually
        UUID technicalId = entityService.addItem(NBA_SCORE_JOB_MODEL, ENTITY_VERSION, job).get();
        job.setTechnicalId(technicalId);
        log.info("Created NbaScoreJob with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @GetMapping("/nbaScoreJob/{id}")
    public ResponseEntity<?> getNbaScoreJob(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> future = entityService.getItem(NBA_SCORE_JOB_MODEL, ENTITY_VERSION, id);
        ObjectNode node = future.get();
        if (node == null) {
            log.error("NbaScoreJob not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("NbaScoreJob not found");
        }
        NbaScoreJob job = nodeToNbaScoreJob(node);
        return ResponseEntity.ok(job);
    }

    // --- Subscriber Endpoints ---

    @PostMapping("/subscriber")
    public ResponseEntity<?> createSubscriber(@RequestBody Subscriber subscriber) throws ExecutionException, InterruptedException {
        if (subscriber == null || subscriber.getEmail() == null || subscriber.getEmail().isBlank()) {
            log.error("Invalid Subscriber creation request: missing or blank email");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing or invalid email");
        }
        // Check for duplicate email - query active subscribers by condition
        Condition emailCondition = Condition.of("$.email", "IEQUALS", subscriber.getEmail());
        Condition statusCondition = Condition.of("$.status", "IEQUALS", "ACTIVE");
        SearchConditionRequest condition = SearchConditionRequest.group("AND", emailCondition, statusCondition);
        ArrayNode existingSubscribers = entityService.getItemsByCondition(SUBSCRIBER_MODEL, ENTITY_VERSION, condition).get();
        if (existingSubscribers != null && existingSubscribers.size() > 0) {
            log.error("Subscriber creation failed: email already subscribed {}", subscriber.getEmail());
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email already subscribed");
        }
        subscriber.setStatus("ACTIVE");
        subscriber.setSubscribedAt(new Date(System.currentTimeMillis()));
        UUID technicalId = entityService.addItem(SUBSCRIBER_MODEL, ENTITY_VERSION, subscriber).get();
        subscriber.setTechnicalId(technicalId);
        log.info("Created Subscriber with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriber);
    }

    @GetMapping("/subscriber/{id}")
    public ResponseEntity<?> getSubscriber(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> future = entityService.getItem(SUBSCRIBER_MODEL, ENTITY_VERSION, id);
        ObjectNode node = future.get();
        if (node == null) {
            log.error("Subscriber not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
        }
        Subscriber subscriber = nodeToSubscriber(node);
        return ResponseEntity.ok(subscriber);
    }

    @GetMapping("/subscribers")
    public ResponseEntity<?> getAllSubscribers() throws ExecutionException, InterruptedException {
        ArrayNode nodes = entityService.getItems(SUBSCRIBER_MODEL, ENTITY_VERSION).get();
        List<Subscriber> subscribers = new ArrayList<>();
        if (nodes != null) {
            for (int i = 0; i < nodes.size(); i++) {
                Subscriber s = nodeToSubscriber((ObjectNode) nodes.get(i));
                subscribers.add(s);
            }
        }
        return ResponseEntity.ok(subscribers);
    }

    // --- GameScore Endpoints ---

    @PostMapping("/gameScore")
    public ResponseEntity<?> createGameScore(@RequestBody GameScore score) throws ExecutionException, InterruptedException {
        if (score == null || score.getGameDate() == null ||
                score.getHomeTeam() == null || score.getHomeTeam().isBlank() ||
                score.getAwayTeam() == null || score.getAwayTeam().isBlank()) {
            log.error("Invalid GameScore creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing or invalid required fields");
        }
        score.setStatus("NEW");
        UUID technicalId = entityService.addItem(GAME_SCORE_MODEL, ENTITY_VERSION, score).get();
        score.setTechnicalId(technicalId);
        log.info("Created GameScore with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(score);
    }

    @GetMapping("/gameScore/{id}")
    public ResponseEntity<?> getGameScore(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> future = entityService.getItem(GAME_SCORE_MODEL, ENTITY_VERSION, id);
        ObjectNode node = future.get();
        if (node == null) {
            log.error("GameScore not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("GameScore not found");
        }
        GameScore score = nodeToGameScore(node);
        return ResponseEntity.ok(score);
    }

    @GetMapping("/gameScores")
    public ResponseEntity<?> getAllGameScores(@RequestParam(required = false) Integer limit,
                                              @RequestParam(required = false) Integer offset) throws ExecutionException, InterruptedException {
        ArrayNode nodes = entityService.getItems(GAME_SCORE_MODEL, ENTITY_VERSION).get();
        List<GameScore> scores = new ArrayList<>();
        if (nodes != null) {
            for (int i = 0; i < nodes.size(); i++) {
                GameScore score = nodeToGameScore((ObjectNode) nodes.get(i));
                scores.add(score);
            }
        }
        // Simple pagination
        int start = offset != null && offset >= 0 ? offset : 0;
        int end = limit != null && limit > 0 ? Math.min(start + limit, scores.size()) : scores.size();
        if (start > scores.size()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<GameScore> sublist = scores.subList(start, end);
        return ResponseEntity.ok(sublist);
    }

    @GetMapping("/gameScores/date/{date}")
    public ResponseEntity<?> getGameScoresByDate(@PathVariable String date) throws ExecutionException, InterruptedException {
        Condition dateCondition = Condition.of("$.gameDate", "EQUALS", date);
        SearchConditionRequest condition = SearchConditionRequest.group("AND", dateCondition);
        ArrayNode nodes = entityService.getItemsByCondition(GAME_SCORE_MODEL, ENTITY_VERSION, condition).get();
        List<GameScore> filtered = new ArrayList<>();
        if (nodes != null) {
            for (int i = 0; i < nodes.size(); i++) {
                filtered.add(nodeToGameScore((ObjectNode) nodes.get(i)));
            }
        }
        return ResponseEntity.ok(filtered);
    }

    // --- Helper mapping methods ---

    private NbaScoreJob nodeToNbaScoreJob(ObjectNode node) {
        NbaScoreJob job = new NbaScoreJob();
        if (node.has("technicalId")) {
            job.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
        }
        if (node.has("date")) {
            job.setDate(node.get("date").asText());
        }
        if (node.has("status")) {
            job.setStatus(node.get("status").asText());
        }
        if (node.has("createdAt")) {
            long ts = node.get("createdAt").asLong(0L);
            if (ts > 0L) job.setCreatedAt(new Date(ts));
        }
        if (node.has("completedAt")) {
            long ts = node.get("completedAt").asLong(0L);
            if (ts > 0L) job.setCompletedAt(new Date(ts));
        }
        if (node.has("id")) {
            job.setId(node.get("id").asText());
        }
        return job;
    }

    private Subscriber nodeToSubscriber(ObjectNode node) {
        Subscriber subscriber = new Subscriber();
        if (node.has("technicalId")) {
            subscriber.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
        }
        if (node.has("email")) {
            subscriber.setEmail(node.get("email").asText());
        }
        if (node.has("status")) {
            subscriber.setStatus(node.get("status").asText());
        }
        if (node.has("subscribedAt")) {
            long ts = node.get("subscribedAt").asLong(0L);
            if (ts > 0L) subscriber.setSubscribedAt(new Date(ts));
        }
        if (node.has("id")) {
            subscriber.setId(node.get("id").asText());
        }
        return subscriber;
    }

    private GameScore nodeToGameScore(ObjectNode node) {
        GameScore score = new GameScore();
        if (node.has("technicalId")) {
            score.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
        }
        if (node.has("gameDate")) {
            score.setGameDate(node.get("gameDate").asText());
        }
        if (node.has("homeTeam")) {
            score.setHomeTeam(node.get("homeTeam").asText());
        }
        if (node.has("awayTeam")) {
            score.setAwayTeam(node.get("awayTeam").asText());
        }
        if (node.has("homeScore")) {
            score.setHomeScore(node.get("homeScore").asInt());
        }
        if (node.has("awayScore")) {
            score.setAwayScore(node.get("awayScore").asInt());
        }
        if (node.has("status")) {
            score.setStatus(node.get("status").asText());
        }
        if (node.has("id")) {
            score.setId(node.get("id").asText());
        }
        return score;
    }
}