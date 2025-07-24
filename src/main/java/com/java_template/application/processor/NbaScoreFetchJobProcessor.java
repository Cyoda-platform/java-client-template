package com.java_template.application.processor;

import com.java_template.application.entity.NbaScoreFetchJob;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

@Component
public class NbaScoreFetchJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public NbaScoreFetchJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("NbaScoreFetchJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing NbaScoreFetchJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(NbaScoreFetchJob.class)
            .validate(NbaScoreFetchJob::isValid)
            .map(this::processNbaScoreFetchJobLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "NbaScoreFetchJobProcessor".equals(modelSpec.operationName()) &&
               "nbaScoreFetchJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private NbaScoreFetchJob processNbaScoreFetchJobLogic(NbaScoreFetchJob job) {
        logger.info("Processing NbaScoreFetchJob with ID: {}", job.getJobId());
        try {
            job.setStatus("RUNNING");

            List<GameScore> fetchedGameScores = fetchGameScoresFromExternalApi(job.getScheduledDate());

            List<CompletableFuture<UUID>> futures = new ArrayList<>();
            for (GameScore gs : fetchedGameScores) {
                gs.setGameId(null); // clear any local ID before persisting
                futures.add(entityService.addItem("GameScore", Config.ENTITY_VERSION, gs));
            }

            List<UUID> technicalIds = new ArrayList<>();
            for (CompletableFuture<UUID> f : futures) {
                technicalIds.add(f.get());
            }

            for (int i = 0; i < fetchedGameScores.size(); i++) {
                fetchedGameScores.get(i).setGameId(technicalIds.get(i).toString());
                processGameScore(fetchedGameScores.get(i));
            }

            job.setStatus("COMPLETED");

            for (GameScore gs : fetchedGameScores) {
                triggerNotifications(gs);
            }
        } catch (Exception e) {
            logger.error("Failed to process NbaScoreFetchJob {}: {}", job.getJobId(), e.getMessage());
            job.setStatus("FAILED");
        }
        return job;
    }

    private List<GameScore> fetchGameScoresFromExternalApi(LocalDate date) {
        logger.info("Fetching NBA game scores for date {}", date);
        List<GameScore> gameScores = new ArrayList<>();

        GameScore gs1 = new GameScore();
        gs1.setGameDate(date);
        gs1.setHomeTeam("Lakers");
        gs1.setAwayTeam("Warriors");
        gs1.setHomeScore(102);
        gs1.setAwayScore(99);
        gs1.setStatus("COMPLETED");
        gameScores.add(gs1);

        GameScore gs2 = new GameScore();
        gs2.setGameDate(date);
        gs2.setHomeTeam("Celtics");
        gs2.setAwayTeam("Bulls");
        gs2.setHomeScore(110);
        gs2.setAwayScore(105);
        gs2.setStatus("COMPLETED");
        gameScores.add(gs2);

        return gameScores;
    }

    private void processGameScore(GameScore gameScore) {
        logger.info("Processing GameScore with ID: {}", gameScore.getGameId());
        if (gameScore.getHomeTeam() == null || gameScore.getHomeTeam().isBlank()
                || gameScore.getAwayTeam() == null || gameScore.getAwayTeam().isBlank()) {
            logger.error("Invalid GameScore data: missing team names");
            return;
        }
        if (gameScore.getHomeScore() == null || gameScore.getAwayScore() == null) {
            logger.error("Invalid GameScore data: missing scores");
            return;
        }
        logger.info("GameScore {} processed successfully", gameScore.getGameId());
    }

    private void triggerNotifications(GameScore gameScore) {
        logger.info("Triggering notifications for GameScore ID: {}", gameScore.getGameId());
        subscriptionCache.values().stream()
                .filter(sub -> "ACTIVE".equals(sub.getStatus()))
                .forEach(sub -> {
                    boolean notify = false;
                    if (sub.getTeam() == null || sub.getTeam().isBlank()) {
                        notify = true;
                    } else if (sub.getTeam().equalsIgnoreCase(gameScore.getHomeTeam())
                            || sub.getTeam().equalsIgnoreCase(gameScore.getAwayTeam())) {
                        notify = true;
                    }
                    if (notify) {
                        sendNotification(sub, gameScore);
                    }
                });
    }

    private void sendNotification(Subscription subscription, GameScore gameScore) {
        logger.info("Sending {} notification to user {} via {} for game {} vs {} with scores {}-{}",
                subscription.getNotificationType(),
                subscription.getUserId(),
                subscription.getChannel(),
                gameScore.getHomeTeam(),
                gameScore.getAwayTeam(),
                gameScore.getHomeScore(),
                gameScore.getAwayScore());
    }
}