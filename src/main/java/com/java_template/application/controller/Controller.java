package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String NBA_SCORE_JOB_MODEL = "NbaScoreJob";
    private static final String SUBSCRIBER_MODEL = "Subscriber";
    private static final String GAME_SCORE_MODEL = "GameScore";

    // --- NbaScoreJob Endpoints ---

    @PostMapping("/nbaScoreJob")
    public ResponseEntity<?> createNbaScoreJob(@Valid @RequestBody NbaScoreJob job) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (job == null || job.getDate() == null) {
            log.error("Invalid NbaScoreJob creation request: missing date");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing or invalid required fields");
        }
        job.setStatus("PENDING");
        job.setCreatedAt(LocalDateTime.now());
        // technicalId will be set by entityService, ignore setting manually
        UUID technicalId = entityService.addItem(NBA_SCORE_JOB_MODEL, ENTITY_VERSION, job).get();
        job.setTechnicalId(technicalId);
        log.info("Created NbaScoreJob with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @GetMapping("/nbaScoreJob/{id}")
    public ResponseEntity<?> getNbaScoreJob(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> future = entityService.getItem(NBA_SCORE_JOB_MODEL, ENTITY_VERSION, technicalId);
        ObjectNode node = future.get();
        if (node == null) {
            log.error("NbaScoreJob not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("NbaScoreJob not found");
        }
        NbaScoreJob job = objectMapper.treeToValue(node, NbaScoreJob.class);
        return ResponseEntity.ok(job);
    }

    // --- Subscriber Endpoints ---

    @PostMapping("/subscriber")
    public ResponseEntity<?> createSubscriber(@Valid @RequestBody Subscriber subscriber) throws ExecutionException, InterruptedException, JsonProcessingException {
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
        subscriber.setSubscribedAt(LocalDateTime.now());
        UUID technicalId = entityService.addItem(SUBSCRIBER_MODEL, ENTITY_VERSION, subscriber).get();
        subscriber.setTechnicalId(technicalId);
        log.info("Created Subscriber with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriber);
    }

    @GetMapping("/subscriber/{id}")
    public ResponseEntity<?> getSubscriber(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> future = entityService.getItem(SUBSCRIBER_MODEL, ENTITY_VERSION, technicalId);
        ObjectNode node = future.get();
        if (node == null) {
            log.error("Subscriber not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
        }
        Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
        return ResponseEntity.ok(subscriber);
    }

    @GetMapping("/subscribers")
    public ResponseEntity<?> getAllSubscribers() throws ExecutionException, InterruptedException, JsonProcessingException {
        ArrayNode nodes = entityService.getItems(SUBSCRIBER_MODEL, ENTITY_VERSION).get();
        List<Subscriber> subscribers = new ArrayList<>();
        if (nodes != null) {
            for (int i = 0; i < nodes.size(); i++) {
                Subscriber s = objectMapper.treeToValue((ObjectNode) nodes.get(i), Subscriber.class);
                subscribers.add(s);
            }
        }
        return ResponseEntity.ok(subscribers);
    }

    // --- GameScore Endpoints ---

    @PostMapping("/gameScore")
    public ResponseEntity<?> createGameScore(@Valid @RequestBody GameScore score) throws ExecutionException, InterruptedException, JsonProcessingException {
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
    public ResponseEntity<?> getGameScore(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> future = entityService.getItem(GAME_SCORE_MODEL, ENTITY_VERSION, technicalId);
        ObjectNode node = future.get();
        if (node == null) {
            log.error("GameScore not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("GameScore not found");
        }
        GameScore score = objectMapper.treeToValue(node, GameScore.class);
        return ResponseEntity.ok(score);
    }

    @GetMapping("/gameScores")
    public ResponseEntity<?> getAllGameScores(@RequestParam(required = false) Integer limit,
                                              @RequestParam(required = false) Integer offset) throws ExecutionException, InterruptedException, JsonProcessingException {
        ArrayNode nodes = entityService.getItems(GAME_SCORE_MODEL, ENTITY_VERSION).get();
        List<GameScore> scores = new ArrayList<>();
        if (nodes != null) {
            for (int i = 0; i < nodes.size(); i++) {
                GameScore score = objectMapper.treeToValue((ObjectNode) nodes.get(i), GameScore.class);
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
    public ResponseEntity<?> getGameScoresByDate(@PathVariable String date) throws ExecutionException, InterruptedException, JsonProcessingException {
        Condition dateCondition = Condition.of("$.gameDate", "EQUALS", date);
        SearchConditionRequest condition = SearchConditionRequest.group("AND", dateCondition);
        ArrayNode nodes = entityService.getItemsByCondition(GAME_SCORE_MODEL, ENTITY_VERSION, condition).get();
        List<GameScore> filtered = new ArrayList<>();
        if (nodes != null) {
            for (int i = 0; i < nodes.size(); i++) {
                filtered.add(objectMapper.treeToValue((ObjectNode) nodes.get(i), GameScore.class));
            }
        }
        return ResponseEntity.ok(filtered);
    }
}