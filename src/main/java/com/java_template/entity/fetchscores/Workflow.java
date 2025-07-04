package com.java_template.entity.fetchscores;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Component("fetchscores")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private static final String EXTERNAL_API_KEY = "test";
    private static final String EXTERNAL_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=" + EXTERNAL_API_KEY;

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public Workflow(EntityService entityService) {
        this.entityService = entityService;
    }

    private String safeGetText(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull()) ? f.asText() : null;
    }

    private Integer safeGetInt(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull() && f.isInt()) ? f.asInt() : null;
    }

    // Condition function: returns true if entity has valid date field (YYYY-MM-DD)
    public CompletableFuture<ObjectNode> hasValidDate(ObjectNode entity) {
        boolean valid = false;
        try {
            if (entity.hasNonNull("date")) {
                String dateStr = entity.get("date").asText();
                LocalDate.parse(dateStr);
                valid = true;
            }
        } catch (DateTimeParseException ignored) {
        }
        entity.put("success", valid);
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: negation of hasValidDate
    public CompletableFuture<ObjectNode> not_hasValidDate(ObjectNode entity) {
        return hasValidDate(entity).thenApply(e -> {
            boolean valid = e.get("success").asBoolean();
            e.put("success", !valid);
            return e;
        });
    }

    // Action function to fetch external scores and attach them as "externalScores" array in entity
    public CompletableFuture<ObjectNode> fetchExternalScores(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String dateStr = entity.get("date").asText();
                String url = String.format(EXTERNAL_API_URL_TEMPLATE, dateStr);
                String jsonResponse = restTemplate.getForObject(url, String.class);
                if (jsonResponse == null) {
                    entity.put("error", "Empty response from external API");
                    entity.putArray("externalScores"); // empty array to indicate response structure
                    return entity;
                }
                JsonNode root = objectMapper.readTree(jsonResponse);
                if (!root.isArray()) {
                    entity.put("error", "Unexpected JSON structure from external API");
                    entity.putArray("externalScores"); // empty array
                    return entity;
                }
                entity.set("externalScores", root);
                entity.put("success", true);
            } catch (Exception e) {
                logger.error("Error fetching external scores: {}", e.getMessage(), e);
                entity.put("error", e.getMessage());
                entity.putArray("externalScores");
                entity.put("success", false);
            }
            return entity;
        });
    }

    // Condition to check if externalScores is array (non-empty or empty)
    public CompletableFuture<ObjectNode> externalApiResponseIsArray(ObjectNode entity) {
        boolean isArray = entity.has("externalScores") && entity.get("externalScores").isArray();
        entity.put("success", isArray);
        return CompletableFuture.completedFuture(entity);
    }

    // Negation of externalApiResponseIsArray
    public CompletableFuture<ObjectNode> not_externalApiResponseIsArray(ObjectNode entity) {
        return externalApiResponseIsArray(entity).thenApply(e -> {
            boolean res = e.get("success").asBoolean();
            e.put("success", !res);
            return e;
        });
    }

    // Action function to delete existing games for the date in entity
    public CompletableFuture<ObjectNode> deleteExistingGames(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String dateStr = entity.get("date").asText();
                List<JsonNode> existingGames = entityService.getItemsByCondition(
                        "Game",
                        ENTITY_VERSION,
                        com.java_template.common.util.SearchConditionRequest.group("AND",
                                com.java_template.common.util.Condition.of("$.date", "EQUALS", dateStr))
                ).join();

                List<CompletableFuture<Void>> deleteFutures = new ArrayList<>();
                for (JsonNode existingGameNode : existingGames) {
                    JsonNode technicalIdNode = existingGameNode.get("technicalId");
                    if (technicalIdNode != null && !technicalIdNode.isNull()) {
                        try {
                            UUID technicalId = UUID.fromString(technicalIdNode.asText());
                            deleteFutures.add(entityService.deleteItem("Game", ENTITY_VERSION, technicalId).thenAccept(uuid -> {}));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                CompletableFuture.allOf(deleteFutures.toArray(new CompletableFuture[0])).join();
                logger.info("Deleted {} existing games for date {}", deleteFutures.size(), dateStr);
                entity.put("success", true);
            } catch (Exception e) {
                logger.error("Error deleting existing games: {}", e.getMessage(), e);
                entity.put("error", e.getMessage());
                entity.put("success", false);
            }
            return entity;
        });
    }

    // Action function to add new games from externalScores in entity
    public CompletableFuture<ObjectNode> addNewGames(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!entity.has("externalScores") || !entity.get("externalScores").isArray()) {
                    entity.put("error", "No externalScores array to add");
                    entity.put("success", false);
                    return entity;
                }
                String dateStr = entity.get("date").asText();
                List<ObjectNode> gamesToAdd = new ArrayList<>();
                for (JsonNode gameNode : entity.get("externalScores")) {
                    String homeTeam = safeGetText(gameNode, "HomeTeam");
                    String awayTeam = safeGetText(gameNode, "AwayTeam");
                    Integer homeScore = safeGetInt(gameNode, "HomeTeamScore");
                    Integer awayScore = safeGetInt(gameNode, "AwayTeamScore");
                    if (homeTeam == null || awayTeam == null) {
                        logger.warn("Skipping game with incomplete team info: {}", gameNode.toString());
                        continue;
                    }
                    ObjectNode gameEntity = objectMapper.createObjectNode();
                    gameEntity.put("date", dateStr);
                    gameEntity.put("homeTeam", homeTeam);
                    gameEntity.put("awayTeam", awayTeam);
                    if (homeScore != null) gameEntity.put("homeScore", homeScore);
                    if (awayScore != null) gameEntity.put("awayScore", awayScore);
                    gamesToAdd.add(gameEntity);
                }
                List<CompletableFuture<UUID>> addFutures = gamesToAdd.stream()
                        .map(gameEntity -> entityService.addItem("Game", ENTITY_VERSION, gameEntity, this::processGame))
                        .collect(Collectors.toList());
                CompletableFuture.allOf(addFutures.toArray(new CompletableFuture[0])).join();
                logger.info("Added {} new games for date {}", gamesToAdd.size(), dateStr);
                entity.put("gamesFetched", gamesToAdd.size());
                entity.put("success", true);
            } catch (Exception e) {
                logger.error("Error adding new games: {}", e.getMessage(), e);
                entity.put("error", e.getMessage());
                entity.put("success", false);
            }
            return entity;
        });
    }

    // Condition to check if subscribers exist
    public CompletableFuture<ObjectNode> subscribersExist(ObjectNode entity) {
        List<JsonNode> subscribers = entityService.getItems("Subscriber", ENTITY_VERSION).join();
        boolean exist = !subscribers.isEmpty();
        entity.put("success", exist);
        return CompletableFuture.completedFuture(entity);
    }

    // Negation of subscribersExist
    public CompletableFuture<ObjectNode> not_subscribersExist(ObjectNode entity) {
        return subscribersExist(entity).thenApply(e -> {
            boolean res = e.get("success").asBoolean();
            e.put("success", !res);
            return e;
        });
    }

    // Action function to send email notifications asynchronously
    public CompletableFuture<ObjectNode> sendEmailNotifications(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<JsonNode> subscribers = entityService.getItems("Subscriber", ENTITY_VERSION).join();
                String dateStr = entity.get("date").asText();
                int gamesCount = entity.has("gamesFetched") ? entity.get("gamesFetched").asInt() : 0;
                int subscriberCount = subscribers.size();

                for (JsonNode subscriberNode : subscribers) {
                    JsonNode emailNode = subscriberNode.get("email");
                    if (emailNode != null && !emailNode.isNull()) {
                        String email = emailNode.asText();
                        CompletableFuture.runAsync(() -> {
                            logger.info("Sending email to {} with {} games summary for {}", email, gamesCount, dateStr);
                            // TODO: Implement real email sending here
                        });
                    }
                }
                logger.info("Sent notifications to {} subscribers for date {}", subscriberCount, dateStr);
                entity.put("subscribersNotified", subscriberCount);
                entity.put("success", true);
            } catch (Exception e) {
                logger.error("Error sending notifications: {}", e.getMessage(), e);
                entity.put("error", e.getMessage());
                entity.put("success", false);
            }
            return entity;
        });
    }

    // Condition to check if entity has no error
    public CompletableFuture<ObjectNode> noErrors(ObjectNode entity) {
        boolean noError = !entity.hasNonNull("error");
        entity.put("success", noError);
        return CompletableFuture.completedFuture(entity);
    }

    // Negation of noErrors
    public CompletableFuture<ObjectNode> not_noErrors(ObjectNode entity) {
        return noErrors(entity).thenApply(e -> {
            boolean res = e.get("success").asBoolean();
            e.put("success", !res);
            return e;
        });
    }

    // Placeholder for processGame workflow (called when adding games)
    private CompletableFuture<ObjectNode> processGame(ObjectNode gameEntity) {
        // TODO: Implement if needed
        return CompletableFuture.completedFuture(gameEntity);
    }
}