package com.java_template.application.processor;

import com.java_template.application.entity.NbaScoresFetchJob;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class NbaScoresFetchJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.java_template.common.service.EntityService entityService;

    public NbaScoresFetchJobProcessor(SerializerFactory serializerFactory, com.fasterxml.jackson.databind.ObjectMapper objectMapper, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
        logger.info("NbaScoresFetchJobProcessor initialized with SerializerFactory, ObjectMapper and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing NbaScoresFetchJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(NbaScoresFetchJob.class)
            .validate(this::isValidEntity)
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "NbaScoresFetchJobProcessor".equals(modelSpec.operationName()) &&
               "nbaScoresFetchJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(NbaScoresFetchJob entity) {
        return entity.isValid();
    }

    private NbaScoresFetchJob processEntityLogic(NbaScoresFetchJob entity) {
        try {
            UUID jobTechnicalId = UUID.fromString(entity.getModelKey().getName()); // This is a placeholder; actual technicalId should be extracted differently if needed
            LocalDate scheduledDate = entity.getScheduledDate();
            processNbaScoresFetchJobAsync(jobTechnicalId, scheduledDate);
        } catch (Exception e) {
            logger.error("Error processing NBA Scores Fetch Job: {}", e.getMessage());
            // Optionally update entity status to FAILED
            entity.setStatus("FAILED");
            entity.setSummary("Exception during processing: " + e.getMessage());
        }
        return entity;
    }

    private void processNbaScoresFetchJobAsync(UUID jobTechnicalId, LocalDate scheduledDate) {
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Processing NbaScoresFetchJob with technicalId: {}", jobTechnicalId);

                if (scheduledDate.isAfter(LocalDate.now())) {
                    updateJobStatus(jobTechnicalId, "FAILED", "Scheduled date cannot be in the future");
                    logger.error("Job {} failed: scheduledDate in future", jobTechnicalId);
                    return;
                }

                updateJobStatus(jobTechnicalId, "IN_PROGRESS", null);

                String apiKey = "test"; // Replace with valid API key or config
                String url = String.format("https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s", scheduledDate, apiKey);

                String response = entityService.getRestTemplate().getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(response);
                if (root.isArray()) {
                    int gamesCount = 0;
                    List<ObjectNode> newGames = new ArrayList<>();
                    for (JsonNode node : root) {
                        ObjectNode gameNode = objectMapper.createObjectNode();
                        gameNode.put("gameDate", scheduledDate.toString());
                        gameNode.put("homeTeam", node.path("HomeTeam").asText());
                        if (!node.path("HomeTeamScore").isNull())
                            gameNode.put("homeScore", node.path("HomeTeamScore").asInt());
                        else
                            gameNode.putNull("homeScore");
                        gameNode.put("awayTeam", node.path("AwayTeam").asText());
                        if (!node.path("AwayTeamScore").isNull())
                            gameNode.put("awayScore", node.path("AwayTeamScore").asInt());
                        else
                            gameNode.putNull("awayScore");
                        gameNode.put("status", node.path("Status").asText(null));
                        newGames.add(gameNode);
                        gamesCount++;
                    }

                    if (!newGames.isEmpty()) {
                        CompletableFuture<List<UUID>> idsFuture = entityService.addItems("NbaGame", Config.ENTITY_VERSION, newGames);
                        List<UUID> technicalIds = idsFuture.get();
                        logger.info("Added {} games with technical IDs: {}", technicalIds.size(), technicalIds);
                    }

                    updateJobStatus(jobTechnicalId, "COMPLETED", "Fetched " + gamesCount + " games for " + scheduledDate);

                    StringBuilder summaryBuilder = new StringBuilder();
                    summaryBuilder.append("NBA Scores for ").append(scheduledDate).append(":\n");
                    for (JsonNode node : root) {
                        summaryBuilder.append(node.path("HomeTeam").asText()).append(" ")
                            .append(node.path("HomeTeamScore").isNull() ? "-" : node.path("HomeTeamScore").asInt())
                            .append(" - ")
                            .append(node.path("AwayTeamScore").isNull() ? "-" : node.path("AwayTeamScore").asInt())
                            .append(" ")
                            .append(node.path("AwayTeam").asText()).append("\n");
                    }

                    entityService.getSubscriberCache().values().stream()
                        .filter(s -> "ACTIVE".equals(s.getStatus()))
                        .forEach(s -> {
                            logger.info("Sending email to {} with summary:\n{}", s.getEmail(), summaryBuilder.toString());
                            // Real email sending implementation can be added here
                        });

                } else {
                    updateJobStatus(jobTechnicalId, "FAILED", "Unexpected API response format");
                    logger.error("Unexpected API response format for job {}", jobTechnicalId);
                }
            } catch (Exception e) {
                try {
                    updateJobStatus(jobTechnicalId, "FAILED", "Exception during fetch: " + e.getMessage());
                } catch (Exception ex) {
                    logger.error("Failed to update job status for {}: {}", jobTechnicalId, ex.getMessage());
                }
                logger.error("Error processing job {}: {}", jobTechnicalId, e.getMessage());
            }
        });
    }

    private void updateJobStatus(UUID jobTechnicalId, String status, String summary) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> jobFuture = entityService.getItem("nbaScoresFetchJob", Config.ENTITY_VERSION, jobTechnicalId);
        ObjectNode oldJob = jobFuture.get();
        if (oldJob == null) {
            logger.error("Job not found for status update: {}", jobTechnicalId);
            return;
        }
        ObjectNode newJob = oldJob.deepCopy();
        newJob.put("status", status);
        if (summary != null) {
            newJob.put("summary", summary);
        } else {
            newJob.putNull("summary");
        }
        newJob.put("scheduledDate", oldJob.path("scheduledDate").asText());
        newJob.put("fetchTimeUTC", oldJob.path("fetchTimeUTC").asText());

        entityService.addItem("nbaScoresFetchJob", Config.ENTITY_VERSION, newJob).get();
    }

}