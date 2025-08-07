package com.java_template.application.processor;

import com.java_template.application.entity.Job;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class NotifySubscribersProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public NotifySubscribersProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job notification for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        if (!"SUCCEEDED".equals(job.getStatus()) && !"FAILED".equals(job.getStatus())) {
            logger.info("Job {} is not in SUCCEEDED or FAILED state, skipping notification", job.getJobName());
            return job;
        }

        try {
            CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems("Subscriber", "1");
            ArrayNode subscribersNodes = subscribersFuture.get();

            if (subscribersNodes == null || subscribersNodes.isEmpty()) {
                logger.info("No subscribers found for notification");
            } else {
                for (var node : subscribersNodes) {
                    Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
                    try {
                        sendNotification(subscriber, job);
                        logger.info("Notification sent to subscriber {}", subscriber.getSubscriberId());
                    } catch (Exception e) {
                        logger.error("Failed to send notification to subscriber: {}", e.getMessage(), e);
                    }
                }
            }

            job.setStatus("NOTIFIED_SUBSCRIBERS");
            entityService.addItem(Job.ENTITY_NAME, Job.ENTITY_VERSION, job).get();
            logger.info("Job {} status updated to NOTIFIED_SUBSCRIBERS", job.getJobName());
        } catch (Exception e) {
            logger.error("Error processing job notification for job {}: {}", job.getJobName(), e.getMessage(), e);
        }

        return job;
    }

    private void sendNotification(Subscriber subscriber, Job job) {
        // Implement notification sending logic here, e.g., email or webhook
        // For now, just log the notification
        logger.info("Sending notification to subscriber {} with contact {}", subscriber.getSubscriberId(), subscriber.getContactValue());
    }
}
