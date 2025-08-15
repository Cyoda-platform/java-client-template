package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.concurrent.CompletableFuture;

@Component
public class MergeOrInsertGameProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MergeOrInsertGameProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final JsonUtils jsonUtils;

    public MergeOrInsertGameProcessor(SerializerFactory serializerFactory, EntityService entityService, JsonUtils jsonUtils) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.jsonUtils = jsonUtils;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Game for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Game.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Game entity) {
        return entity != null && entity.isValid();
    }

    private Game processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Game> context) {
        Game incoming = context.entity();
        try {
            // Search for existing game by gameId + date
            Object condition = com.java_template.common.util.SearchConditionRequest.group("AND",
                com.java_template.common.util.Condition.of("$.gameId", "EQUALS", incoming.getGameId()),
                com.java_template.common.util.Condition.of("$.date", "EQUALS", incoming.getDate())
            );
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItemsByCondition(Game.ENTITY_NAME, String.valueOf(Game.ENTITY_VERSION), condition, true);
            com.fasterxml.jackson.databind.node.ArrayNode items = itemsFuture.get();
            if (items != null && items.size() > 0) {
                // Merge with the first found
                ObjectNode existing = (ObjectNode) items.get(0);
                Game current = jsonUtils.fromJsonNode(existing, Game.class);
                // Deterministic merge: prefer latest non-null fields; prefer incoming if incoming has more recent lastUpdated
                boolean incomingIsNewer = true;
                if (incoming.getLastUpdated() != null && current.getLastUpdated() != null) {
                    incomingIsNewer = incoming.getLastUpdated().compareTo(current.getLastUpdated()) >= 0;
                }

                Game merged = mergeGames(current, incoming, incomingIsNewer);
                merged.setLastUpdated(Instant.now().toString());
                // Persist update via entityService.updateItem if technicalId known - but processors should not call updateItem directly per instructions. We'll just return the merged entity to be persisted by the framework.
                return merged;
            } else {
                // New insert - set lastUpdated
                incoming.setLastUpdated(Instant.now().toString());
                return incoming;
            }
        } catch (Exception e) {
            logger.error("Error in MergeOrInsertGameProcessor: {}", e.getMessage(), e);
            return incoming;
        }
    }

    private Game mergeGames(Game current, Game incoming, boolean incomingIsNewer) {
        Game result = new Game();
        result.setGameId(current.getGameId() != null && !current.getGameId().isBlank() ? current.getGameId() : incoming.getGameId());
        result.setDate(current.getDate() != null && !current.getDate().isBlank() ? current.getDate() : incoming.getDate());
        result.setHomeTeam(incoming.getHomeTeam() != null ? incoming.getHomeTeam() : current.getHomeTeam());
        result.setAwayTeam(incoming.getAwayTeam() != null ? incoming.getAwayTeam() : current.getAwayTeam());
        // Scores: prefer incoming if newer or non-null
        result.setHomeScore(incomingIsNewer && incoming.getHomeScore() != null ? incoming.getHomeScore() : current.getHomeScore());
        result.setAwayScore(incomingIsNewer && incoming.getAwayScore() != null ? incoming.getAwayScore() : current.getAwayScore());
        result.setStatus(incomingIsNewer && incoming.getStatus() != null ? incoming.getStatus() : current.getStatus());
        result.setVenue(incoming.getVenue() != null ? incoming.getVenue() : current.getVenue());
        result.setLeague(incoming.getLeague() != null ? incoming.getLeague() : current.getLeague());
        // Merge raw payload: prefer incoming if newer
        result.setRawPayload(incomingIsNewer && incoming.getRawPayload() != null ? incoming.getRawPayload() : current.getRawPayload());
        return result;
    }
}
