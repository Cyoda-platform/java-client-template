package com.java_template.application.processor;

import com.java_template.application.entity.GameScoreFetchJob;
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
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GameScoreFetchJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final NbaGameScoreController nbaGameScoreController;
    private final SubscriberController subscriberController;

    public GameScoreFetchJobProcessor(SerializerFactory serializerFactory,
                                      NbaGameScoreController nbaGameScoreController,
                                      SubscriberController subscriberController) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.nbaGameScoreController = nbaGameScoreController;
        this.subscriberController = subscriberController;
        logger.info("GameScoreFetchJobProcessor initialized with SerializerFactory and Controllers");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing GameScoreFetchJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(GameScoreFetchJob.class)
                .validate(GameScoreFetchJob::isValid)
                .map(this::processGameScoreFetchJob)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "GameScoreFetchJobProcessor".equals(modelSpec.operationName()) &&
                "gameScoreFetchJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private GameScoreFetchJob processGameScoreFetchJob(GameScoreFetchJob job) {
        logger.info("Processing GameScoreFetchJob with technicalId: {}", job.getTechnicalId());

        // Validate scheduledDate
        if (job.getScheduledDate() == null) {
            job.setStatus("FAILED");
            logger.error("Scheduled date is null, marking job as FAILED");
            return job;
        }

        if (job.getScheduledDate().isAfter(java.time.LocalDate.now())) {
            job.setStatus("FAILED");
            logger.error("Scheduled date is in the future, marking job as FAILED");
            return job;
        }

        // Fetch NBA game scores from external API
        List<NbaGameScore> scores = nbaGameScoreController.fetchScoresForDate(job.getScheduledDate());

        // Save/update NbaGameScore entities
        List<NbaGameScore> savedScores = scores.stream()
                .map(nbaGameScoreController::saveOrUpdate)
                .collect(Collectors.toList());

        // Update job status
        job.setStatus(savedScores.isEmpty() ? "FAILED" : "COMPLETED");

        // Trigger notifications for each saved score
        for (NbaGameScore score : savedScores) {
            List<Subscriber> subscribers = subscriberController.findSubscribersByTeams(score.getHomeTeam(), score.getAwayTeam());
            for (Subscriber subscriber : subscribers) {
                subscriberController.notifySubscriber(subscriber, score);
            }
        }

        return job;
    }

} // end class
