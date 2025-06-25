package com.java_template.entity.GameFetchRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
@RequiredArgsConstructor
public class GameFetchRequestWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(GameFetchRequestWorkflow.class);

    private final ObjectMapper objectMapper;

    // TODO: Inject or set entityService with required methods: getItemsByCondition, deleteItem, addItem, getItems

    // Entry point: workflow orchestration only, no business logic here
    public CompletableFuture<ObjectNode> processGameFetchRequest(ObjectNode entity) {
        return fetchAndParseGames(entity)
                .thenCompose(this::deleteExistingGames)
                .thenCompose(this::storeGames)
                .thenCompose(this::notifySubscribers)
                .thenApply(entityNode -> {
                    // Workflow orchestration ends here
                    return entityNode;
                });
    }

    // Step 1: fetch and parse games from external API, store games array in entity attribute "fetchedGames"
    private CompletableFuture<ObjectNode> processFetchAndParseGames(ObjectNode entity) {
        try {
            if (!entity.has("date")) {
                logger.warn("GameFetchRequest entity missing 'date' field");
                return CompletableFuture.completedFuture(entity);
            }
            String date = entity.get("date").asText();
            logger.info("Processing fetch and parse for date {}", date);

            String url = String.format(NBA_API_URL_TEMPLATE, date);
            String rawJson = fetchRawJson(url); // sync call, wrap in CompletableFuture below
            if (rawJson == null || rawJson.isBlank()) {
                logger.warn("Empty response from NBA API for date {}", date);
                return CompletableFuture.completedFuture(entity);
            }

            JsonNode rootNode = objectMapper.readTree(rawJson);
            if (!rootNode.isArray()) {
                logger.error("Unexpected NBA API response format for date {}: expected JSON array", date);
                return CompletableFuture.completedFuture(entity);
            }

            ArrayNode gamesArray = objectMapper.createArrayNode();
            rootNode.forEach(gamesArray::add);

            entity.set("fetchedGames", gamesArray);
            return CompletableFuture.completedFuture(entity);

        } catch (Exception e) {
            logger.error("Error in processFetchAndParseGames", e);
            return CompletableFuture.completedFuture(entity);
        }
    }

    // Step 2: delete existing games for the date from storage, uses entity.fetchedGames to get date
    private CompletableFuture<ObjectNode> processDeleteExistingGames(ObjectNode entity) {
        try {
            if (!entity.has("date")) {
                logger.warn("GameFetchRequest entity missing 'date' field");
                return CompletableFuture.completedFuture(entity);
            }
            String date = entity.get("date").asText();

            String condition = String.format("{\"date\":\"%s\"}", date);
            CompletableFuture<ArrayNode> existingGamesFuture = entityService.getItemsByCondition(GAME_ENTITY, ENTITY_VERSION, condition);

            return existingGamesFuture.thenCompose(existingGames -> {
                List<CompletableFuture<Void>> deleteFutures = new ArrayList<>();
                for (JsonNode existingGameNode : existingGames) {
                    if (existingGameNode.has("technicalId")) {
                        UUID technicalId = UUID.fromString(existingGameNode.get("technicalId").asText());
                        deleteFutures.add(entityService.deleteItem(GAME_ENTITY, ENTITY_VERSION, technicalId).thenApply(r -> null));
                    }
                }
                return CompletableFuture.allOf(deleteFutures.toArray(new CompletableFuture[0]))
                        .thenApply(v -> entity);
            });

        } catch (Exception e) {
            logger.error("Error in processDeleteExistingGames", e);
            return CompletableFuture.completedFuture(entity);
        }
    }

    // Step 3: store fetched games, entity.fetchedGames must exist
    private CompletableFuture<ObjectNode> processStoreGames(ObjectNode entity) {
        try {
            if (!entity.has("date")) {
                logger.warn("GameFetchRequest entity missing 'date' field");
                return CompletableFuture.completedFuture(entity);
            }
            if (!entity.has("fetchedGames")) {
                logger.warn("GameFetchRequest entity missing 'fetchedGames' field");
                return CompletableFuture.completedFuture(entity);
            }

            String date = entity.get("date").asText();
            ArrayNode fetchedGames = (ArrayNode) entity.get("fetchedGames");

            List<CompletableFuture<UUID>> futures = new ArrayList<>();
            for (JsonNode gameNode : fetchedGames) {
                Game game = parseGameFromJsonNode(gameNode, date);
                futures.add(entityService.addItem(GAME_ENTITY, ENTITY_VERSION, game, this::processGame));
            }

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> {
                        logger.info("Stored {} games for date {}", futures.size(), date);
                        return entity;
                    });

        } catch (Exception e) {
            logger.error("Error in processStoreGames", e);
            return CompletableFuture.completedFuture(entity);
        }
    }

    // Step 4: notify subscribers by email about the games stored, uses entity.fetchedGames and date
    private CompletableFuture<ObjectNode> processNotifySubscribers(ObjectNode entity) {
        try {
            if (!entity.has("date")) {
                logger.warn("GameFetchRequest entity missing 'date' field");
                return CompletableFuture.completedFuture(entity);
            }
            if (!entity.has("fetchedGames")) {
                logger.warn("GameFetchRequest entity missing 'fetchedGames' field");
                return CompletableFuture.completedFuture(entity);
            }

            String date = entity.get("date").asText();
            ArrayNode fetchedGames = (ArrayNode) entity.get("fetchedGames");

            CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems(SUBSCRIBER_ENTITY, ENTITY_VERSION);

            return subscribersFuture.thenApply(subscribersArray -> {
                if (subscribersArray.size() == 0) {
                    logger.info("No subscribers to notify for date {}", date);
                    return entity;
                }

                StringBuilder summary = new StringBuilder();
                summary.append("NBA Scores for ").append(date).append(":\n");
                for (JsonNode gameNode : fetchedGames) {
                    String homeTeam = gameNode.path("HomeTeam").asText("Unknown");
                    String awayTeam = gameNode.path("AwayTeam").asText("Unknown");
                    int homeScore = gameNode.path("HomeTeamScore").asInt(-1);
                    int awayScore = gameNode.path("AwayTeamScore").asInt(-1);
                    summary.append(String.format("%s vs %s: %d - %d\n", homeTeam, awayTeam, homeScore, awayScore));
                }

                for (JsonNode subscriberNode : subscribersArray) {
                    String email = subscriberNode.path("email").asText(null);
                    if (email != null) {
                        logger.info("Sending email to {}: \n{}", email, summary);
                        // TODO: integrate real email sending
                    }
                }

                logger.info("Email notifications sent to {} subscribers for date {}", subscribersArray.size(), date);
                return entity;
            });

        } catch (Exception e) {
            logger.error("Error in processNotifySubscribers", e);
            return CompletableFuture.completedFuture(entity);
        }
    }

    // Helper method to parse game from JsonNode
    private Game parseGameFromJsonNode(JsonNode node, String date) {
        String homeTeam = node.path("HomeTeam").asText(null);
        String awayTeam = node.path("AwayTeam").asText(null);
        Integer homeScore = node.path("HomeTeamScore").isInt() ? node.path("HomeTeamScore").asInt() : null;
        Integer awayScore = node.path("AwayTeamScore").isInt() ? node.path("AwayTeamScore").asInt() : null;
        String otherInfo = node.toString();
        return new Game(date, homeTeam, awayTeam, homeScore, awayScore, otherInfo);
    }

    // Dummy placeholder for processGame passed to addItem - can be implemented as needed
    private CompletableFuture<ObjectNode> processGame(ObjectNode entity) {
        // No orchestration logic here, only business logic if needed
        return CompletableFuture.completedFuture(entity);
    }

    // Dummy fetchRawJson method to simulate synchronous fetch (replace with async if needed)
    private String fetchRawJson(String url) {
        // TODO: implement actual HTTP GET call, e.g. with RestTemplate or WebClient
        return null;
    }

    // Workflow orchestration - calls all process methods in order
    private CompletableFuture<ObjectNode> fetchAndParseGames(ObjectNode entity) {
        return processFetchAndParseGames(entity)
                .thenCompose(this::processDeleteExistingGames)
                .thenCompose(this::processStoreGames)
                .thenCompose(this::processNotifySubscribers);
    }

}
