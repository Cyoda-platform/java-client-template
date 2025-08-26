package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SendNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SendNotificationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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
        Subscriber subscriber = context.entity();

        // Basic guard: subscriber must be active to receive notifications
        if (subscriber.getActive() == null || !subscriber.getActive()) {
            logger.info("Subscriber {} (technicalId={}) is not active. Skipping notification.", subscriber.getName(), subscriber.getTechnicalId());
            return subscriber;
        }

        // Ensure contact information is present (validated earlier, but double-check)
        String contactType = subscriber.getContactType() != null ? subscriber.getContactType().trim().toLowerCase() : "";
        String contactAddress = subscriber.getContactAddress();

        if (contactType.isBlank() || contactAddress == null || contactAddress.isBlank()) {
            logger.warn("Subscriber {} (technicalId={}) missing contact info. Skipping notification.", subscriber.getName(), subscriber.getTechnicalId());
            return subscriber;
        }

        // Build a simple payload based on subscriber preference.
        String payload;
        String pref = subscriber.getPreferredPayload();
        if (pref == null || pref.isBlank() || "summary".equalsIgnoreCase(pref)) {
            payload = String.format("Notification for subscriber '%s' (id=%s): new job completion event.", subscriber.getName(), subscriber.getId());
        } else {
            // preferredPayload = "full" or custom -> provide a more verbose message
            payload = String.format("Full notification for subscriber '%s' (id=%s). ContactType=%s, ContactAddress=%s", subscriber.getName(), subscriber.getId(), subscriber.getContactType(), subscriber.getContactAddress());
        }

        // Simulate delivery attempts by logging. Actual delivery mechanisms (HTTP/email) are out of scope here.
        try {
            if (contactType.contains("webhook") || contactType.contains("http")) {
                // Simulated webhook dispatch
                logger.info("Dispatching webhook to {} with payload: {}", contactAddress, payload);
            } else if (contactType.contains("email")) {
                // Simulated email dispatch
                logger.info("Sending email to {} with payload: {}", contactAddress, payload);
            } else {
                // Unknown contact type
                logger.warn("Unsupported contact type '{}' for subscriber {}. Skipping delivery.", subscriber.getContactType(), subscriber.getTechnicalId());
            }
        } catch (Exception ex) {
            // Log the failure. Retry orchestration is handled by separate RetryNotificationProcessor based on configured policies.
            logger.error("Failed to deliver notification to subscriber {} (technicalId={}). Error: {}", subscriber.getName(), subscriber.getTechnicalId(), ex.getMessage(), ex);
        }

        // No changes to Subscriber persistent state are required here per rules (Cyoda will persist entity state automatically if modified).
        return subscriber;
    }
}