package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EnqueueNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnqueueNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnqueueNotificationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber entity = context.entity();
        if (entity == null) {
            logger.warn("Subscriber entity is null in execution context");
            return null;
        }

        String status = entity.getStatus();
        if (status == null || !status.equalsIgnoreCase("ACTIVE")) {
            logger.info("Subscriber {} is not ACTIVE (status={}). Skipping enqueue.", entity.getId(), status);
            return entity;
        }

        // Basic contact validation (defensive double-check before enqueue)
        String method = entity.getContactMethod();
        String details = entity.getContactDetails();
        boolean contactValid = true;
        if (method == null || method.isBlank() || details == null || details.isBlank()) {
            contactValid = false;
        } else if ("email".equalsIgnoreCase(method)) {
            contactValid = details.contains("@");
        } else if ("webhook".equalsIgnoreCase(method)) {
            contactValid = details.startsWith("http://") || details.startsWith("https://");
        } // other methods accepted as-is

        if (!contactValid) {
            logger.warn("Subscriber {} contact validation failed (method={}, details={}) - marking FAILED",
                entity.getId(), method, details);
            entity.setStatus("FAILED");
            return entity;
        }

        // Determine enqueue action based on preference
        String preference = entity.getPreference();
        if (preference == null || preference.isBlank()) {
            preference = "immediate"; // default
        }

        switch (preference.toLowerCase()) {
            case "immediate":
                // For immediate preference, we mark as RECEIVING to indicate notifications will be dispatched now.
                entity.setStatus("RECEIVING");
                logger.info("Subscriber {} enqueued for immediate notifications", entity.getId());
                break;
            case "dailydigest":
            case "daily":
            case "dailyDigest":
                entity.setStatus("ENQUEUED_DAILY");
                logger.info("Subscriber {} enqueued for daily digest", entity.getId());
                break;
            case "weeklydigest":
            case "weekly":
            case "weeklyDigest":
                entity.setStatus("ENQUEUED_WEEKLY");
                logger.info("Subscriber {} enqueued for weekly digest", entity.getId());
                break;
            default:
                // Unknown preference -> treat as immediate
                entity.setStatus("RECEIVING");
                logger.info("Subscriber {} preference '{}' unknown - defaulting to immediate enqueue", entity.getId(), preference);
                break;
        }

        // Note: actual dispatching/enqueueing to external queue or services should be implemented elsewhere.
        // This processor only updates the subscriber state to reflect enqueue decision. Cyoda will persist the entity.

        return entity;
    }
}