package com.java_template.entity.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Component("game")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private static final String EXTERNAL_API_KEY = "test"; // TODO: Replace with secure config
    private static final String EXTERNAL_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=" + EXTERNAL_API_KEY;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // Mocked entityService to represent the interface (inject real one as needed)
    private EntityService entityService;

    @PostConstruct
    private void init() {
        // TODO: Inject or assign real entityService instance here
        this.entityService = new EntityService();
    }

    // Condition Functions

    public CompletableFuture<ObjectNode> hasValidDate(ObjectNode entity) {
        boolean valid = false;
        if (entity.hasNonNull("date")) {
            String dateStr = entity.get("date").asText();
            try {
                LocalDate.parse(dateStr);
                valid = true;
            } catch (DateTimeParseException ignored) {
            }
        }
        entity.put("hasValidDate", valid);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> not_hasValidDate(ObjectNode entity) {
        return hasValidDate(entity).thenApply(e -> {
            boolean valid = e.get("hasValidDate").asBoolean(false);
            e.put("hasValidDate", !valid);
            return e;
        });
    }

    public CompletableFuture<ObjectNode> externalApiResponseIsArray(ObjectNode entity) {
        // The response is stored in "externalResponse" field as JsonNode string
        boolean isArray = false;
        if (entity.hasNonNull("externalResponse")) {
            try {
                JsonNode node = objectMapper.readTree(entity.get("externalResponse").asText());
                isArray = node.isArray();
            } catch (Exception e) {
                logger.error("Error parsing externalResponse JSON: {}", e.getMessage());
            }
        }
        entity.put("externalApiResponseIsArray", isArray);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> not_externalApiResponseIsArray(ObjectNode entity) {
        return externalApiResponseIsArray(entity).thenApply(e -> {
            boolean val = e.get("externalApiResponseIsArray").asBoolean(false);
            e.put("externalApiResponseIsArray", !val);
            return e;
        });
    }

    public CompletableFuture<ObjectNode> subscribersExist(ObjectNode entity) {
        boolean exist = false;
        try {
            List<JsonNode> subscribers = entityService.getItems("Subscriber", ENTITY_VERSION).join();
            exist = !subscribers.isEmpty();
        } catch (Exception e) {
            logger.error("Error fetching subscribers: {}", e.getMessage());
        }
        entity.put("subscribersExist", exist);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> not_subscribersExist(ObjectNode entity) {
        return subscribersExist(entity).thenApply(e -> {
            boolean val = e.get("subscribersExist").asBoolean(false);
            e.put("subscribersExist", !val);
            return e;
        });
    }

    public CompletableFuture<ObjectNode> noErrors(ObjectNode entity) {
        boolean noErrors = !entity.hasNonNull("error");
        entity.put("noErrors", noErrors);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> not_noErrors(ObjectNode entity) {
        return noErrors(entity).thenApply(e -> {
            boolean val = e.get("noErrors").asBoolean(true);
            e.put("noErrors", !val);
            return e;
        });
    }

    // Action Functions

    public CompletableFuture<ObjectNode> validateFetchScoresEntity(ObjectNode entity) {
        // Validation is done in hasValidDate condition, here just log and return entity
        logger.info("Validating fetchScores entity, date={}", entity.hasNonNull("date") ? entity.get("date").asText() : "null");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> fetchExternalScores(ObjectNode entity) {
        try {
            if (!entity.hasNonNull("date")) {
                entity.put("error", "Missing 'date' field");
                return CompletableFuture.completedFuture(entity);
            }
            String dateStr = entity.get("date").asText();
            String url = String.format(EXTERNAL_API_URL_TEMPLATE, dateStr);
            String jsonResponse = restTemplate.getForObject(url, String.class);
            if (jsonResponse == null) {
                entity.put("error", "Empty response from external API");
                return CompletableFuture.completedFuture(entity);
            }
            entity.put("externalResponse", jsonResponse);
            logger.info("Fetched external scores for date {}", dateStr);
        } catch (Exception e) {
            logger.error("Error fetching external scores: {}", e.getMessage());
            entity.put("error", e.getMessage());
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> deleteExistingGames(ObjectNode entity) {
        try {
            String dateStr = entity.get("date").asText();
            List<JsonNode> existingGames = entityService.getItemsByCondition(
                    "Game",
                    ENTITY_VERSION,
                    com.java_template.common.util.SearchConditionRequest.group("AND",
                            com.java_template.common.util.Condition.of("$.date", "EQUALS", dateStr))
            ).join();

            List<CompletableFuture<Void>> deleteFutures = new ArrayList<>();
            for (JsonNode gameNode : existingGames) {
                JsonNode techIdNode = gameNode.get("technicalId");
                if (techIdNode != null && !techIdNode.isNull()) {
                    try {
                        UUID techId = UUID.fromString(techIdNode.asText());
                        deleteFutures.add(entityService.deleteItem("Game", ENTITY_VERSION, techId).thenAccept(uuid -> {}));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            CompletableFuture.allOf(deleteFutures.toArray(new CompletableFuture[0])).join();
            logger.info("Deleted {} existing games for date {}", deleteFutures.size(), dateStr);
        } catch (Exception e) {
            logger.error("Error deleting existing games: {}", e.getMessage());
            entity.put("error", e.getMessage());
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> addNewGames(ObjectNode entity) {
        try {
            if (!entity.hasNonNull("externalResponse")) {
                entity.put("error", "No externalResponse to add games from");
                return CompletableFuture.completedFuture(entity);
            }
            String dateStr = entity.get("date").asText();
            JsonNode root = objectMapper.readTree(entity.get("externalResponse").asText());
            if (!root.isArray()) {
                entity.put("error", "externalResponse is not an array");
                return CompletableFuture.completedFuture(entity);
            }

            List<ObjectNode> gamesToAdd = new ArrayList<>();
            for (JsonNode gameNode : root) {
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
        } catch (Exception e) {
            logger.error("Error adding new games: {}", e.getMessage());
            entity.put("error", e.getMessage());
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> sendEmailNotifications(ObjectNode entity) {
        try {
            String dateStr = entity.get("date").asText();
            List<JsonNode> subscribers = entityService.getItems("Subscriber", ENTITY_VERSION).join();
            int subscriberCount = subscribers.size();

            for (JsonNode subscriberNode : subscribers) {
                JsonNode emailNode = subscriberNode.get("email");
                if (emailNode != null && !emailNode.isNull()) {
                    String email = emailNode.asText();
                    CompletableFuture.runAsync(() -> {
                        logger.info("Sending email to {} with games summary for {}", email, dateStr);
                        // TODO: Implement real email sending here
                    });
                }
            }
            logger.info("Sent notifications to {} subscribers for date {}", subscriberCount, dateStr);
            entity.put("subscribersNotified", subscriberCount);
        } catch (Exception e) {
            logger.error("Error sending email notifications: {}", e.getMessage());
            entity.put("error", e.getMessage());
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Helper functions

    private String safeGetText(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull()) ? f.asText() : null;
    }

    private Integer safeGetInt(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull() && f.isInt()) ? f.asInt() : null;
    }

    // Dummy processGame function to comply with usage in addNewGames
    public CompletableFuture<UUID> processGame(ObjectNode gameEntity) {
        // Example processing: trim strings
        JsonNode homeTeamNode = gameEntity.get("homeTeam");
        if (homeTeamNode != null && !homeTeamNode.isNull()) {
            gameEntity.put("homeTeam", homeTeamNode.asText().trim());
        }
        JsonNode awayTeamNode = gameEntity.get("awayTeam");
        if (awayTeamNode != null && !awayTeamNode.isNull()) {
            gameEntity.put("awayTeam", awayTeamNode.asText().trim());
        }
        return CompletableFuture.completedFuture(UUID.randomUUID()); // TODO: Replace with real persistence result
    }

    // Mock EntityService stub
    private static class EntityService {
        public CompletableFuture<List<JsonNode>> getItems(String entityType, String version) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        public CompletableFuture<List<JsonNode>> getItemsByCondition(String entityType, String version, Object condition) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        public CompletableFuture<Void> deleteItem(String entityType, String version, UUID id) {
            return CompletableFuture.completedFuture(null);
        }
        public CompletableFuture<UUID> addItem(String entityType, String version, ObjectNode entity, java.util.function.Function<ObjectNode, CompletableFuture<UUID>> processor) {
            return CompletableFuture.completedFuture(UUID.randomUUID());
        }
    }
}