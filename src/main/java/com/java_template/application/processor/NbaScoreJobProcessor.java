package com.java_template.application.processor;

import com.java_template.application.entity.NbaScoreJob;
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
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Date;
import java.util.concurrent.ExecutionException;

@Component
public class NbaScoreJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public NbaScoreJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("NbaScoreJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing NbaScoreJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(NbaScoreJob.class)
                .validate(NbaScoreJob::isValid, "Invalid NbaScoreJob entity state")
                .map(this::processNbaScoreJobLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "NbaScoreJobProcessor".equals(modelSpec.operationName()) &&
                "nbaScoreJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private NbaScoreJob processNbaScoreJobLogic(NbaScoreJob job) {
        try {
            logger.info("Processing NbaScoreJob with technicalId: {}", job.getTechnicalId());
            job.setStatus("IN_PROGRESS");
            entityService.updateItem("nbaScoreJob", Config.ENTITY_VERSION, job.getTechnicalId(), job).get();

            // Simulate asynchronous data fetch from external API for job.date
            Date gameDate = java.sql.Date.valueOf(job.getDate());
            // Create sample GameScore entity
            com.java_template.application.entity.GameScore sampleScore = new com.java_template.application.entity.GameScore();
            sampleScore.setGameDate(job.getDate());
            sampleScore.setHomeTeam("Lakers");
            sampleScore.setAwayTeam("Warriors");
            sampleScore.setHomeScore(110);
            sampleScore.setAwayScore(108);
            sampleScore.setStatus("NEW");
            UUID gsTechnicalId = entityService.addItem("gameScore", Config.ENTITY_VERSION, sampleScore).get();
            sampleScore.setTechnicalId(gsTechnicalId);
            processGameScore(sampleScore);

            // Notify all active subscribers
            com.java_template.common.workflow.Condition statusCondition = com.java_template.common.workflow.Condition.of("$.status", "IEQUALS", "ACTIVE");
            com.java_template.common.workflow.SearchConditionRequest activeCondition = com.java_template.common.workflow.SearchConditionRequest.group("AND", statusCondition);
            ArrayNode activeSubsNodes = entityService.getItemsByCondition("subscriber", Config.ENTITY_VERSION, activeCondition).get();
            List<com.java_template.application.entity.Subscriber> activeSubscribers = new ArrayList<>();
            if (activeSubsNodes != null) {
                for (int i = 0; i < activeSubsNodes.size(); i++) {
                    activeSubscribers.add(nodeToSubscriber((ObjectNode) activeSubsNodes.get(i)));
                }
            }
            for (com.java_template.application.entity.Subscriber sub : activeSubscribers) {
                logger.info("Sending NBA scores email notification to subscriber: {}", sub.getEmail());
            }
            job.setStatus("COMPLETED");
            job.setCompletedAt(new java.util.Date(System.currentTimeMillis()));
        } catch (Exception e) {
            logger.error("Failed to process NbaScoreJob technicalId {}: {}", job.getTechnicalId(), e.getMessage());
            job.setStatus("FAILED");
        }
        try {
            entityService.updateItem("nbaScoreJob", Config.ENTITY_VERSION, job.getTechnicalId(), job).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to update NbaScoreJob after processing: {}", e.getMessage());
        }
        return job;
    }

    private void processGameScore(com.java_template.application.entity.GameScore score) throws ExecutionException, InterruptedException {
        logger.info("Processing GameScore with technicalId: {}", score.getTechnicalId());
        if ("NEW".equalsIgnoreCase(score.getStatus())) {
            score.setStatus("PROCESSED");
            entityService.updateItem("gameScore", Config.ENTITY_VERSION, score.getTechnicalId(), score).get();
            logger.info("GameScore technicalId {} marked as PROCESSED", score.getTechnicalId());
        }
    }

    private com.java_template.application.entity.Subscriber nodeToSubscriber(ObjectNode node) {
        com.java_template.application.entity.Subscriber sub = new com.java_template.application.entity.Subscriber();
        sub.setId(node.get("id").asText());
        sub.setEmail(node.get("email").asText());
        sub.setStatus(node.get("status").asText());
        sub.setSubscribedAt(java.time.LocalDateTime.parse(node.get("subscribedAt").asText()));
        return sub;
    }
}
