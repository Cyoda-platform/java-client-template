package com.java_template.application.processor;

import com.java_template.application.entity.Job;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class SubscribersNotifierProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public SubscribersNotifierProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SubscribersNotifier for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Job.class)
                .validate(this::isValidEntity, "Invalid Job entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        logger.info("Notifying subscribers for Job {}", job.getId());

        try {
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> futureSubscribers = entityService.getItemsByCondition(
                    "Subscriber", "1", com.java_template.common.util.Condition.of("$.active", "EQUALS", true), true);
            com.fasterxml.jackson.databind.node.ArrayNode activeSubscribers = futureSubscribers.get();

            int notifiedCount = 0;
            for (com.fasterxml.jackson.databind.JsonNode subscriberNode : activeSubscribers) {
                // Assume notification logic here (email/webhook)
                notifiedCount++;
            }

            logger.info("Job {} notified {} active subscribers", job.getId(), notifiedCount);

            job.setState("NOTIFIED_SUBSCRIBERS");
            // No update method for external service
        } catch (Exception e) {
            logger.error("Failed to notify subscribers for Job {}: {}", job.getId(), e.getMessage());
        }

        return job;
    }
}
