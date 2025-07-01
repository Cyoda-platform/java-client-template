package com.java_template.entity.fetchscorestask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("fetchscorestask")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final RestTemplate restTemplate = new RestTemplate();

    // Mock entityService with addItem method - TODO: replace with actual service injection if needed
    private final EntityServiceMock entityService = new EntityServiceMock();

    // This method corresponds to the "startFetchScores" action in workflow initial transition
    public CompletableFuture<ObjectNode> startFetchScores(ObjectNode entity) {
        logger.info("Starting fetch scores workflow");
        // Just pass through the entity
        return CompletableFuture.completedFuture(entity);
    }

    // This method corresponds to the "fetchScoresSuccess" and "fetchScoresFailure" conditions and fetch logic
    public CompletableFuture<ObjectNode> fetchScores(ObjectNode entity) {
        String dateStr = entity.has("date") ? entity.get("date").asText() : null;
        if (dateStr == null || dateStr.isBlank()) {
            logger.warn("FetchScoresTask missing valid date, skipping fetch");
            entity.put("fetchSuccess", false);
            return CompletableFuture.completedFuture(entity);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                LocalDate date = LocalDate.parse(dateStr);
                logger.info("Starting fetch/store/notify pipeline for date {}", date);

                String url = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/" + date + "?key=test";
                String raw = restTemplate.getForObject(new URI(url), String.class);
                JsonNode arr = entityService.getObjectMapper().readTree(raw);
                if (!arr.isArray()) {
                    logger.warn("Expected array JSON but got {}", arr.getNodeType());
                    entity.put("fetchSuccess", false);
                    return entity;
                }

                List<GameScore> fetchedScores = new ArrayList<>();
                for (JsonNode n : arr) {
                    String home = n.path("HomeTeam").asText(null);
                    String away = n.path("AwayTeam").asText(null);
                    Integer hScore = n.path("HomeTeamScore").isInt() ? n.path("HomeTeamScore").asInt() : null;
                    Integer aScore = n.path("AwayTeamScore").isInt() ? n.path("AwayTeamScore").asInt() : null;
                    String stat = n.path("Status").asText(null);
                    if (home == null || away == null) continue;

                    GameScore gs = new GameScore(date, home, away, hScore, aScore, stat);
                    fetchedScores.add(gs);
                }
                entity.set("fetchedScores", entityService.getObjectMapper().valueToTree(fetchedScores));
                entity.put("fetchSuccess", true);
                return entity;
            } catch (Exception ex) {
                logger.error("Error in fetchScores", ex);
                entity.put("fetchSuccess", false);
                return entity;
            }
        });
    }

    // Condition function to check if fetchScores succeeded
    public boolean fetchScoresSuccess(ObjectNode entity) {
        return entity.has("fetchSuccess") && entity.get("fetchSuccess").asBoolean(false);
    }

    // Condition function to check if fetchScores failed
    public boolean fetchScoresFailure(ObjectNode entity) {
        return !fetchScoresSuccess(entity);
    }

    // Store the fetched scores into persistence
    public CompletableFuture<ObjectNode> storeScores(ObjectNode entity) {
        try {
            if (!entity.has("fetchedScores")) {
                logger.warn("No fetchedScores found to store");
                entity.put("storeSuccess", false);
                return CompletableFuture.completedFuture(entity);
            }
            JsonNode scoresArray = entity.get("fetchedScores");
            if (!scoresArray.isArray()) {
                logger.warn("fetchedScores is not an array");
                entity.put("storeSuccess", false);
                return CompletableFuture.completedFuture(entity);
            }

            List<CompletableFuture<UUID>> futures = new ArrayList<>();
            for (JsonNode n : scoresArray) {
                GameScore gs = entityService.getObjectMapper().convertValue(n, GameScore.class);
                CompletableFuture<UUID> future = entityService.addItem("GameScore", ENTITY_VERSION, gs, this::processGameScore);
                futures.add(future);
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            logger.info("Stored {} GameScore entities", futures.size());
            entity.put("storeSuccess", true);
        } catch (Exception e) {
            logger.error("Error storing scores", e);
            entity.put("storeSuccess", false);
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function to check if storeScores succeeded
    public boolean storeScoresSuccess(ObjectNode entity) {
        return entity.has("storeSuccess") && entity.get("storeSuccess").asBoolean(false);
    }

    // Condition function to check if storeScores failed
    public boolean storeScoresFailure(ObjectNode entity) {
        return !storeScoresSuccess(entity);
    }

    // Send notifications to subscribers
    public CompletableFuture<ObjectNode> sendNotifications(ObjectNode entity) {
        try {
            if (!entity.has("fetchedScores")) {
                logger.warn("No fetchedScores found to notify");
                entity.put("notifySuccess", false);
                return CompletableFuture.completedFuture(entity);
            }

            LocalDate date = null;
            if (entity.has("date")) {
                try {
                    date = LocalDate.parse(entity.get("date").asText());
                } catch (Exception ignore) {}
            }

            // Compose notification message
            StringBuilder sb = new StringBuilder();
            sb.append("Daily NBA Scores");
            if (date != null) sb.append(" for ").append(date);
            sb.append(":\n");

            for (JsonNode n : entity.get("fetchedScores")) {
                String away = n.path("awayTeam").asText("N/A");
                String home = n.path("homeTeam").asText("N/A");
                int aScore = n.path("awayScore").asInt(-1);
                int hScore = n.path("homeScore").asInt(-1);
                sb.append(String.format("%s vs %s: %d - %d\n", away, home, aScore, hScore));
            }

            // TODO: Implement actual email sending logic here
            logger.info("Sending notifications to subscribers:\n{}", sb.toString());

            entity.put("notifySuccess", true);
        } catch (Exception e) {
            logger.error("Error sending notifications", e);
            entity.put("notifySuccess", false);
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function to check if notification sending succeeded
    public boolean notifySubscribersSuccess(ObjectNode entity) {
        return entity.has("notifySuccess") && entity.get("notifySuccess").asBoolean(false);
    }

    // Condition function to check if notification sending failed
    public boolean notifySubscribersFailure(ObjectNode entity) {
        return !notifySubscribersSuccess(entity);
    }

    // Example processGameScore method from initial code, used as a callback in addItem
    public CompletableFuture<ObjectNode> processGameScore(ObjectNode entity) {
        if (entity.has("status") && !entity.get("status").isNull()) {
            String status = entity.get("status").asText();
            entity.put("status", status.toUpperCase());
        }
        return CompletableFuture.completedFuture(entity);
    }

    // --- Mock classes and entities for compilation purpose ---

    // Mock EntityService to satisfy addItem method
    private static class EntityServiceMock {
        public CompletableFuture<UUID> addItem(String entityName, String version, GameScore gs, java.util.function.Function<ObjectNode, CompletableFuture<ObjectNode>> processor) {
            // Simulate async storage and processing
            ObjectNode node = getObjectMapper().valueToTree(gs);
            CompletableFuture<ObjectNode> processed = processor.apply(node);
            return processed.thenApply(objNode -> UUID.randomUUID());
        }

        public ObjectMapper getObjectMapper() {
            return new ObjectMapper();
        }
    }

    // GameScore entity class
    public static class GameScore {
        public LocalDate date;
        public String homeTeam;
        public String awayTeam;
        public Integer homeScore;
        public Integer awayScore;
        public String status;

        public GameScore() {}

        public GameScore(LocalDate date, String homeTeam, String awayTeam, Integer homeScore, Integer awayScore, String status) {
            this.date = date;
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
            this.homeScore = homeScore;
            this.awayScore = awayScore;
            this.status = status;
        }
    }
}