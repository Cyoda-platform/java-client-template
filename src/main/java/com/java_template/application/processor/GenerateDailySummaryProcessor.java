package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.dailysummary.version_1.DailySummary;
import com.java_template.application.entity.fetchjob.version_1.FetchJob;
import com.java_template.application.entity.game.version_1.Game;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.JsonUtils;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
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
public class GenerateDailySummaryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GenerateDailySummaryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final JsonUtils jsonUtils;

    public GenerateDailySummaryProcessor(SerializerFactory serializerFactory, EntityService entityService, JsonUtils jsonUtils) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.jsonUtils = jsonUtils;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing GenerateDailySummary for request: {}", request.getId());

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
        try {
            // Query games for the job.requestDate
            Object condition = SearchConditionRequest.group("AND",
                Condition.of("$.date", "EQUALS", job.getRequestDate())
            );
            CompletableFuture<ArrayNode> gamesFuture = entityService.getItemsByCondition(Game.ENTITY_NAME, String.valueOf(Game.ENTITY_VERSION), condition, true);
            ArrayNode games = gamesFuture.get();
            if (games == null || games.size() == 0) {
                logger.warn("No games found for date {} when generating summary", job.getRequestDate());
                // Depending on policy, we may mark job failed; here we proceed to create an empty summary
            }
            List<ObjectNode> summaries = new ArrayList<>();
            if (games != null) {
                for (int i = 0; i < games.size(); i++) {
                    ObjectNode g = (ObjectNode) games.get(i);
                    ObjectNode s = jsonUtils.createObjectNode();
                    s.put("game_id", g.has("gameId") ? g.get("gameId").asText() : "");
                    s.put("home_team", g.has("homeTeam") ? g.get("homeTeam").asText() : "");
                    s.put("away_team", g.has("awayTeam") ? g.get("awayTeam").asText() : "");
                    s.put("home_score", g.has("homeScore") && g.get("homeScore").isInt() ? g.get("homeScore").asInt() : 0);
                    s.put("away_score", g.has("awayScore") && g.get("awayScore").isInt() ? g.get("awayScore").asInt() : 0);
                    s.put("status", g.has("status") ? g.get("status").asText() : "");
                    summaries.add(s);
                }
            }

            DailySummary summary = new DailySummary();
            summary.setDate(job.getRequestDate());
            summary.setGamesSummary(jsonUtils.toJson(summaries));
            summary.setGeneratedAt(Instant.now().toString());
            summary.setSourceFetchJobId(job.getRequestDate());
            summary.setSummaryId(java.util.UUID.randomUUID().toString());

            // Persist summary
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(DailySummary.ENTITY_NAME, String.valueOf(DailySummary.ENTITY_VERSION), summary);
            java.util.UUID created = idFuture.get();
            if (created != null) {
                logger.info("DailySummary created for date {} id {}", job.getRequestDate(), created.toString());
            }
        } catch (Exception e) {
            logger.error("Error in GenerateDailySummaryProcessor: {}", e.getMessage(), e);
        }
        return job;
    }
}
