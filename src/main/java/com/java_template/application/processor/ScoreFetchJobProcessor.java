package com.java_template.application.processor;

import com.java_template.application.entity.ScoreFetchJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

@Component
public class ScoreFetchJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ScoreFetchJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("ScoreFetchJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ScoreFetchJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ScoreFetchJob.class)
            .validate(ScoreFetchJob::isValid, "Invalid ScoreFetchJob entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ScoreFetchJobProcessor".equals(modelSpec.operationName()) &&
               "scoreFetchJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private ScoreFetchJob processEntityLogic(ScoreFetchJob job) {
        logger.info("Processing ScoreFetchJob for date: {}", job.getJobDate());
        try {
            // Simulate fetch NBA scores asynchronously
            List<com.java_template.application.entity.GameScore> fetchedScores = new ArrayList<>();
            com.java_template.application.entity.GameScore gs = new com.java_template.application.entity.GameScore();
            gs.setGameDate(job.getJobDate());
            gs.setHomeTeam("Team A");
            gs.setAwayTeam("Team B");
            gs.setHomeScore(100);
            gs.setAwayScore(98);
            gs.setStatus("RECEIVED");
            fetchedScores.add(gs);

            List<CompletableFuture<UUID>> futures = new ArrayList<>();
            for (com.java_template.application.entity.GameScore gsEntity : fetchedScores) {
                futures.add(entityService.addItem("GameScore", Config.ENTITY_VERSION, gsEntity));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

            ScoreFetchJob updatedJob = new ScoreFetchJob();
            updatedJob.setJobDate(job.getJobDate());
            updatedJob.setStatus("COMPLETED");
            updatedJob.setTriggeredAt(job.getTriggeredAt());
            updatedJob.setCompletedAt(Instant.now());

            entityService.addItem("ScoreFetchJob", Config.ENTITY_VERSION, updatedJob).get();

            processGameScoreNotification(job.getJobDate());
        } catch (Exception e) {
            logger.error("Exception in processEntityLogic", e);
        }
        return job;
    }

    private void processGameScoreNotification(LocalDate jobDate) {
        logger.info("Sending notifications for GameScores on date: {}", jobDate);
        try {
            Condition condition1 = Condition.of("$.gameDate", "EQUALS", jobDate.toString());
            Condition condition2 = Condition.of("$.status", "EQUALS", "RECEIVED");
            SearchConditionRequest condition = SearchConditionRequest.group("AND", condition1, condition2);

            CompletableFuture<ArrayNode> scoresFuture = entityService.getItemsByCondition("GameScore", Config.ENTITY_VERSION, condition, true);
            ArrayNode scoresArray = scoresFuture.get();

            List<com.java_template.application.entity.GameScore> scoresToNotify = new ArrayList<>();
            for (int i = 0; i < scoresArray.size(); i++) {
                ObjectNode obj = (ObjectNode) scoresArray.get(i);
                com.java_template.application.entity.GameScore gs = entityService.getObjectMapper().treeToValue(obj, com.java_template.application.entity.GameScore.class);
                scoresToNotify.add(gs);
            }

            StringBuilder summary = new StringBuilder("NBA Scores for " + jobDate + ":\n");
            for (com.java_template.application.entity.GameScore gs : scoresToNotify) {
                summary.append(gs.getHomeTeam()).append(" ").append(gs.getHomeScore())
                        .append(" - ").append(gs.getAwayTeam()).append(" ").append(gs.getAwayScore())
                        .append("\n");
            }

            logger.info("Notification summary:\n{}", summary.toString());

            for (com.java_template.application.entity.GameScore gs : scoresToNotify) {
                com.java_template.application.entity.GameScore updatedGs = new com.java_template.application.entity.GameScore();
                updatedGs.setGameDate(gs.getGameDate());
                updatedGs.setHomeTeam(gs.getHomeTeam());
                updatedGs.setAwayTeam(gs.getAwayTeam());
                updatedGs.setHomeScore(gs.getHomeScore());
                updatedGs.setAwayScore(gs.getAwayScore());
                updatedGs.setStatus("PROCESSED");
                entityService.addItem("GameScore", Config.ENTITY_VERSION, updatedGs).get();
            }
        } catch (Exception e) {
            logger.error("Exception in processGameScoreNotification", e);
        }
    }

}
