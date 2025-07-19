package com.java_template.application.processor;

import com.java_template.application.entity.FetchJob;
import com.java_template.application.entity.Notification;
import com.java_template.application.entity.Subscriber;
import com.java_template.common.config.Config;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class FetchJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public FetchJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("FetchJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FetchJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(FetchJob.class)
                .validate(FetchJob::isValid, "Invalid FetchJob state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "FetchJobProcessor".equals(modelSpec.operationName()) &&
                "config_v2".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private FetchJob processEntityLogic(FetchJob fetchJob) {
        try {
            logger.info("Processing FetchJob with technicalId: {}", fetchJob.getTechnicalId());
            fetchJob.setStatus(FetchJob.StatusEnum.PROCESSING);

            // Update status to PROCESSING as new entity version
            entityService.addItem("FetchJob", Config.ENTITY_VERSION, fetchJob).get();

            // Simulate fetching NBA scores from external API for scheduledDate
            try {
                Thread.sleep(100); // simulate delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Update status and resultSummary - create new version
            FetchJob updatedFetchJob = new FetchJob();
            updatedFetchJob.setScheduledDate(fetchJob.getScheduledDate());
            updatedFetchJob.setStatus(FetchJob.StatusEnum.COMPLETED);
            updatedFetchJob.setResultSummary("Simulated fetch success for date " + fetchJob.getScheduledDate());

            entityService.addItem("FetchJob", Config.ENTITY_VERSION, updatedFetchJob).get();

            // Trigger notifications for all active subscribers
            CompletableFuture<ArrayNode> subscribersFuture = entityService.getItemsByCondition("Subscriber", Config.ENTITY_VERSION,
                    SearchConditionRequest.group("AND",
                            Condition.of("$.status", "EQUALS", "ACTIVE")));

            ArrayNode subscribersArray = subscribersFuture.get();

            for (int i = 0; i < subscribersArray.size(); i++) {
                ObjectNode subscriberNode = (ObjectNode) subscribersArray.get(i);
                Subscriber subscriber = new Subscriber();
                subscriber.setTechnicalId(UUID.fromString(subscriberNode.get("technicalId").asText()));
                subscriber.setId(subscriberNode.get("technicalId").asText());
                subscriber.setEmail(subscriberNode.hasNonNull("email") ? subscriberNode.get("email").asText() : null);
                subscriber.setStatus(Subscriber.StatusEnum.valueOf(subscriberNode.get("status").asText()));

                Notification notification = new Notification();
                notification.setSubscriberId(subscriber.getId());
                notification.setJobId(fetchJob.getTechnicalId().toString());
                notification.setStatus(Notification.StatusEnum.PENDING);
                notification.setSentAt(null);

                UUID notifTechnicalId = entityService.addItem("Notification", Config.ENTITY_VERSION, notification).get();
                notification.setTechnicalId(notifTechnicalId);
                notification.setId(notifTechnicalId.toString());

                processNotification(notification);
            }
        } catch (Exception e) {
            logger.error("Error processing FetchJob: {}", e.getMessage(), e);
        }

        return fetchJob;
    }

    private void processNotification(Notification notification) {
        logger.info("Processing Notification with subscriberId: {}, for jobId: {}", notification.getSubscriberId(), notification.getJobId());

        try {
            Thread.sleep(50); // simulate delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Notification updatedNotification = new Notification();
        updatedNotification.setSubscriberId(notification.getSubscriberId());
        updatedNotification.setJobId(notification.getJobId());
        updatedNotification.setStatus(Notification.StatusEnum.SENT);
        updatedNotification.setSentAt(java.time.OffsetDateTime.now());

        try {
            entityService.addItem("Notification", Config.ENTITY_VERSION, updatedNotification).get();
        } catch (Exception e) {
            logger.error("Failed to update Notification status to SENT: {}", e.getMessage());
        }

        logger.info("Notification sent to subscriberId: {} for jobId: {}", notification.getSubscriberId(), notification.getJobId());
    }
}
