package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
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
public class ReconciliationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReconciliationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final JsonUtils jsonUtils;

    public ReconciliationProcessor(SerializerFactory serializerFactory, EntityService entityService, JsonUtils jsonUtils) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.jsonUtils = jsonUtils;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing reconciliation for request: {}", request.getId());

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
            // Find existing by gameId + date
            Object condition = com.java_template.common.util.SearchConditionRequest.group("AND",
                com.java_template.common.util.Condition.of("$.gameId", "EQUALS", incoming.getGameId()),
                com.java_template.common.util.Condition.of("$.date", "EQUALS", incoming.getDate())
            );
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(Game.ENTITY_NAME, String.valueOf(Game.ENTITY_VERSION), condition, true);
            ArrayNode items = itemsFuture.get();
            if (items != null && items.size() > 0) {
                ObjectNode existing = (ObjectNode) items.get(0);
                Game current = jsonUtils.fromJsonNode(existing, Game.class);
                // Compare key fields: scores and status
                boolean changed = false;
                if ((current.getHomeScore() == null && incoming.getHomeScore() != null) || (current.getHomeScore() != null && !current.getHomeScore().equals(incoming.getHomeScore()))) {
                    changed = true;
                }
                if ((current.getAwayScore() == null && incoming.getAwayScore() != null) || (current.getAwayScore() != null && !current.getAwayScore().equals(incoming.getAwayScore()))) {
                    changed = true;
                }
                if (current.getStatus() == null && incoming.getStatus() != null || (current.getStatus() != null && !current.getStatus().equalsIgnoreCase(incoming.getStatus()))) {
                    changed = true;
                }
                if (changed) {
                    logger.info("Reconciliation: detected changes for game {} on {}", incoming.getGameId(), incoming.getDate());
                    // Merge and mark for summary regeneration by updating lastUpdated
                    Game merged = incoming;
                    merged.setLastUpdated(Instant.now().toString());
                    // Trigger DailySummary regeneration: create a FetchJob or schedule summary regeneration. Here we just log and return merged.
                    logger.info("Flagging daily summary regeneration for date {}", merged.getDate());
                    return merged;
                }
            }
        } catch (Exception e) {
            logger.error("Error in ReconciliationProcessor: {}", e.getMessage(), e);
        }
        return incoming;
    }
}
