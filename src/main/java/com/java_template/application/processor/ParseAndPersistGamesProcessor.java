package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.fetchjob.version_1.FetchJob;
import com.java_template.application.entity.game.version_1.Game;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.JsonUtils;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class ParseAndPersistGamesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ParseAndPersistGamesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final JsonUtils jsonUtils;
    private final EntityService entityService;

    public ParseAndPersistGamesProcessor(SerializerFactory serializerFactory, JsonUtils jsonUtils, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.jsonUtils = jsonUtils;
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FetchJob parse and persist for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(FetchJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(FetchJob entity) {
        return entity != null && entity.isValid();
    }

    private FetchJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<FetchJob> context) {
        FetchJob job = context.entity();
        int fetched = 0;
        int failed = 0;
        try {
            String payload = job.getResponsePayload();
            if (payload == null || payload.isBlank()) {
                logger.warn("No payload to parse for fetch job {}", job.getRequestDate());
                job.setFailedCount(0);
                job.setFetchedCount(0);
                return job;
            }
            JsonNode root = jsonUtils.parse(payload);
            ArrayNode gamesArray;
            if (root.isArray()) {
                gamesArray = (ArrayNode) root;
            } else if (root.has("games") && root.get("games`).isArray()) {
                gamesArray = (ArrayNode) root.get("games");
            } else {
                logger.error("Unexpected payload structure for fetch job {}", job.getRequestDate());
                job.setStatus("FAILED");
                job.setErrorMessage("Unexpected payload structure");
                return job;
            }

            List<Game> toPersist = new ArrayList<>();
            for (JsonNode node : gamesArray) {
                try {
                    Game game = new Game();
                    String gameId = node.has("GameID") ? node.get("GameID").asText() : null;
                    if (gameId == null) {
                        // generate stable id based on teams + date + venue
                        gameId = (node.has("HomeTeam") ? node.get("HomeTeam").asText() : "") + "-" + (node.has("AwayTeam") ? node.get("AwayTeam").asText() : "") + "-" + job.getRequestDate();
                    }
                    game.setGameId(gameId);
                    game.setDate(job.getRequestDate());
                    game.setHomeTeam(node.has("HomeTeam") ? node.get("HomeTeam").asText() : null);
                    game.setAwayTeam(node.has("AwayTeam") ? node.get("AwayTeam").asText() : null);
                    game.setHomeScore(node.has("HomeScore") && node.get("HomeScore").isInt() ? node.get("HomeScore").asInt() : null);
                    game.setAwayScore(node.has("AwayScore") && node.get("AwayScore").isInt() ? node.get("AwayScore").asInt() : null);
                    game.setStatus(node.has("Status") ? node.get("Status").asText() : null);
                    game.setVenue(node.has("Venue") ? node.get("Venue").asText() : null);
                    game.setLeague("NBA");
                    game.setRawPayload(jsonUtils.toJson(node));
                    game.setLastUpdated(Instant.now().toString());

                    // Persist each game via entityService.addItem
                    CompletableFuture<java.util.UUID> addFuture = entityService.addItem(Game.ENTITY_NAME, String.valueOf(Game.ENTITY_VERSION), game);
                    java.util.UUID createdId = addFuture.get();
                    if (createdId != null) {
                        fetched++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse/persist a game: {}", e.getMessage());
                    failed++;
                }
            }
            job.setFetchedCount(fetched);
            job.setFailedCount(failed);
        } catch (Exception e) {
            logger.error("Error in ParseAndPersistGamesProcessor: {}", e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
        }
        return job;
    }
}
