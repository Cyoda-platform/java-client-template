package com.java_template.application.processor;

import com.java_template.application.entity.DailyScoresJob;
import com.java_template.common.serializer.ErrorInfo;
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

import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DailyScoresJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public DailyScoresJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("DailyScoresJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DailyScoresJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DailyScoresJob.class)
            .validate(this::isValidEntity, "Invalid DailyScoresJob entity state")
            .map(this::processDailyScoresJob)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DailyScoresJobProcessor".equals(modelSpec.operationName()) &&
               "dailyscoresjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(DailyScoresJob job) {
        return job.isValid();
    }

    private DailyScoresJob processDailyScoresJob(DailyScoresJob job) {
        logger.info("Processing DailyScoresJob for date: {}", job.getDate());

        try {
            java.time.LocalDate jobDate = java.time.LocalDate.parse(job.getDate());
            if (jobDate.isAfter(java.time.LocalDate.now())) {
                job.setStatus("FAILED");
                job.setCompletedAt(java.time.Instant.now().toString());
                logger.error("DailyScoresJob date {} is in the future", job.getDate());
                return job;
            }
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setCompletedAt(java.time.Instant.now().toString());
            logger.error("Invalid date format for DailyScoresJob: {}", job.getDate());
            return job;
        }

        try {
            List<com.java_template.application.entity.GameScore> fetchedScores = fetchNbaScores(job.getDate());

            List<CompletableFuture<UUID>> futures = new ArrayList<>();
            for (com.java_template.application.entity.GameScore score : fetchedScores) {
                CompletableFuture<UUID> future = entityService.addItem("GameScore", Config.ENTITY_VERSION, score);
                futures.add(future);
                processGameScore(score);
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

            CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems("Subscriber", Config.ENTITY_VERSION);
            ArrayNode subscribers = subscribersFuture.get();

            for (var subscriberNode : subscribers) {
                String email = null;
                if (subscriberNode.has("email") && !subscriberNode.get("email").isNull()) {
                    email = subscriberNode.get("email").asText();
                }
                if (email != null) {
                    sendEmailNotification(email, job.getDate(), fetchedScores);
                }
            }

            job.setStatus("COMPLETED");
            job.setCompletedAt(java.time.Instant.now().toString());
            logger.info("Completed processing DailyScoresJob for date: {}", job.getDate());

        } catch (Exception ex) {
            job.setStatus("FAILED");
            job.setCompletedAt(java.time.Instant.now().toString());
            logger.error("Error processing DailyScoresJob: {}", ex.getMessage(), ex);
        }
        return job;
    }

    private List<com.java_template.application.entity.GameScore> fetchNbaScores(String date) {
        logger.info("Fetching NBA scores for date: {}", date);

        com.java_template.application.entity.GameScore gs1 = new com.java_template.application.entity.GameScore();
        gs1.setDate(date);
        gs1.setHomeTeam("Lakers");
        gs1.setAwayTeam("Warriors");
        gs1.setHomeScore(102);
        gs1.setAwayScore(99);
        gs1.setGameStatus("Completed");

        com.java_template.application.entity.GameScore gs2 = new com.java_template.application.entity.GameScore();
        gs2.setDate(date);
        gs2.setHomeTeam("Nets");
        gs2.setAwayTeam("Celtics");
        gs2.setHomeScore(110);
        gs2.setAwayScore(115);
        gs2.setGameStatus("Completed");

        return List.of(gs1, gs2);
    }

    private void sendEmailNotification(String email, String date, List<com.java_template.application.entity.GameScore> gameScores) {
        logger.info("Sending email notification to {} for NBA games on {}", email, date);
    }

    private void processGameScore(com.java_template.application.entity.GameScore score) {
        // No additional processing logic defined for GameScore in the prototype
    }
}
