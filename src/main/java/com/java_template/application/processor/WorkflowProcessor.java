package com.java_template.application.processor;

import com.java_template.application.entity.Workflow;
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

import com.java_template.common.service.EntityService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class WorkflowProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public WorkflowProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("WorkflowProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Workflow for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Workflow.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "WorkflowProcessor".equals(modelSpec.operationName()) &&
               "workflow".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(Workflow entity) {
        return entity.isValid();
    }

    private Workflow processEntityLogic(Workflow workflow) {
        try {
            logger.info("Processing Workflow with subscriberEmail: {} requestedDate: {}", workflow.getSubscriberEmail(), workflow.getRequestedDate());

            workflow.setStatus("PROCESSING");
            entityService.addItem("workflow", Config.ENTITY_VERSION, workflow).get();

            if (!workflow.getSubscriberEmail().contains("@")) {
                logger.error("Invalid subscriber email format: {}", workflow.getSubscriberEmail());
                workflow.setStatus("FAILED");
                entityService.addItem("workflow", Config.ENTITY_VERSION, workflow).get();
                return workflow;
            }

            if (!workflow.getRequestedDate().matches("\\d{4}-\\d{2}-\\d{2}")) {
                logger.error("Invalid requestedDate format: {}", workflow.getRequestedDate());
                workflow.setStatus("FAILED");
                entityService.addItem("workflow", Config.ENTITY_VERSION, workflow).get();
                return workflow;
            }

            List<com.java_template.application.entity.NBAGameScore> fetchedScores = fetchNBAScores(workflow.getRequestedDate());
            List<com.java_template.application.entity.NBAGameScore> savedScores = new ArrayList<>();
            if (!fetchedScores.isEmpty()) {
                CompletableFuture<List<UUID>> idsFuture = entityService.addItems("nbagamescore", Config.ENTITY_VERSION, fetchedScores);
                List<UUID> scoreIds = idsFuture.get();
                for (int i = 0; i < fetchedScores.size(); i++) {
                    com.java_template.application.entity.NBAGameScore score = fetchedScores.get(i);
                    UUID scoreId = scoreIds.get(i);
                    processNBAGameScore(scoreId, score);
                }
            }

            com.java_template.application.entity.EmailNotification notification = new com.java_template.application.entity.EmailNotification();
            notification.setSubscriberEmail(workflow.getSubscriberEmail());
            notification.setNotificationDate(workflow.getRequestedDate());
            notification.setEmailSentStatus("PENDING");
            notification.setSentAt(null);
            CompletableFuture<UUID> notificationIdFuture = entityService.addItem("emailnotification", Config.ENTITY_VERSION, notification);
            UUID notificationId = notificationIdFuture.get();
            processEmailNotification(notificationId, notification);

            workflow.setStatus("COMPLETED");
            entityService.addItem("workflow", Config.ENTITY_VERSION, workflow).get();

            logger.info("Workflow processing COMPLETED for subscriberEmail: {}", workflow.getSubscriberEmail());
        } catch (Exception ex) {
            logger.error("Error processing Workflow: {}", ex.getMessage(), ex);
            try {
                workflow.setStatus("FAILED");
                entityService.addItem("workflow", Config.ENTITY_VERSION, workflow).get();
            } catch (Exception e) {
                logger.error("Error updating Workflow status to FAILED: {}", e.getMessage(), e);
            }
        }
        return workflow;
    }

    private List<com.java_template.application.entity.NBAGameScore> fetchNBAScores(String date) {
        try {
            logger.info("Fetching NBA scores for date: {}", date);
            String url = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/" + date + "?key=test";
            var headers = new org.springframework.http.HttpHeaders();
            headers.setAccept(java.util.List.of(org.springframework.http.MediaType.APPLICATION_JSON));
            var entity = new org.springframework.http.HttpEntity<String>(headers);
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            com.java_template.application.entity.NBAGameScore[] response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, com.java_template.application.entity.NBAGameScore[].class).getBody();
            if (response == null) {
                logger.error("No data returned from NBA API for date {}", date);
                return java.util.Collections.emptyList();
            }
            logger.info("Fetched {} NBA games for date {}", response.length, date);
            return java.util.Arrays.asList(response);
        } catch (Exception e) {
            logger.error("Error fetching NBA scores: {}", e.getMessage(), e);
            return java.util.Collections.emptyList();
        }
    }

    private void processNBAGameScore(UUID scoreId, com.java_template.application.entity.NBAGameScore score) {
        logger.info("Processing NBAGameScore with technicalId: {}", scoreId);
        // No extra processing required as per requirements (immutable records)
    }

    private void processEmailNotification(UUID notificationId, com.java_template.application.entity.EmailNotification notification) {
        logger.info("Processing EmailNotification with technicalId: {}", notificationId);
        try {
            boolean emailSent = sendEmail(notification.getSubscriberEmail(), notification.getNotificationDate());
            if (emailSent) {
                notification.setEmailSentStatus("SENT");
                notification.setSentAt(java.time.Instant.now().toString());
                logger.info("Email sent successfully to {}", notification.getSubscriberEmail());
            } else {
                notification.setEmailSentStatus("FAILED");
                logger.error("Failed to send email to {}", notification.getSubscriberEmail());
            }
            entityService.addItem("emailnotification", Config.ENTITY_VERSION, notification).get();
        } catch (Exception ex) {
            notification.setEmailSentStatus("FAILED");
            try {
                entityService.addItem("emailnotification", Config.ENTITY_VERSION, notification).get();
            } catch (Exception e) {
                logger.error("Error updating EmailNotification status to FAILED: {}", e.getMessage(), e);
            }
            logger.error("Exception while sending email to {}: {}", notification.getSubscriberEmail(), ex.getMessage(), ex);
        }
    }

    private boolean sendEmail(String toEmail, String notificationDate) {
        logger.info("Sending email to {} with NBA scores for date {}", toEmail, notificationDate);
        return true; // Simulated email sending always successful
    }

}
