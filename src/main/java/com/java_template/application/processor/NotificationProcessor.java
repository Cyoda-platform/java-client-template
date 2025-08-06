package com.java_template.application.processor;

import com.java_template.application.entity.Job;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.Subscriber;
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
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class NotificationProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public NotificationProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Notification for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidJob, "Invalid job state")
            .map(this::processNotificationLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidJob(Job job) {
        return job != null && job.isValid();
    }

    private Job processNotificationLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // Business logic:
        // Fetch subscribers and pets matching subscriber preferredPetTypes
        // Send notifications accordingly
        // Since we do not have actual API calls or sending logic, we just log it here

        try {
            // Fetch all subscribers
            CompletableFuture<List<Object>> subscribersFuture = entityService.getItems("Subscriber", "1");
            List<Object> subscribers = subscribersFuture.get();

            // Fetch all pets
            CompletableFuture<List<Object>> petsFuture = entityService.getItems("Pet", "1");
            List<Object> pets = petsFuture.get();

            // For simplicity, log the counts
            logger.info("Fetched {} subscribers and {} pets for notifications", subscribers.size(), pets.size());

            // Normally here would be filtering and sending notifications
            // We do not modify the job entity state here except maybe update status
            job.setStatus("COMPLETED");
        } catch (Exception e) {
            logger.error("Error processing notifications", e);
            job.setStatus("FAILED");
        }

        return job;
    }
}
