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

import java.time.Instant;

@Component
public class RecordDeliveryResultProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RecordDeliveryResultProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RecordDeliveryResultProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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

        // Record the time of this delivery attempt
        String now = Instant.now().toString();
        try {
            subscriber.setLastNotifiedAt(now);
            logger.info("Recorded lastNotifiedAt={} for subscriber id={}", now, subscriber.getId());

            // Basic validation of contact details: if webhook but contactDetails is not a valid URL,
            // mark subscriber inactive to avoid repeated failing deliveries.
            String contactType = subscriber.getContactType();
            String contactDetails = subscriber.getContactDetails();

            if (contactType != null && "webhook".equalsIgnoreCase(contactType)) {
                if (contactDetails == null || contactDetails.isBlank() || 
                    !(contactDetails.startsWith("http://") || contactDetails.startsWith("https://"))) {
                    // mark inactive to prevent further webhook delivery attempts
                    subscriber.setActive(Boolean.FALSE);
                    logger.warn("Subscriber id={} marked inactive due to invalid webhook contactDetails='{}'", subscriber.getId(), contactDetails);
                }
            }

            // For email contact type, do a lightweight check and log if invalid (don't change active automatically)
            if (contactType != null && "email".equalsIgnoreCase(contactType)) {
                if (contactDetails == null || contactDetails.isBlank() || !contactDetails.contains("@")) {
                    logger.warn("Subscriber id={} has potentially invalid email contactDetails='{}'", subscriber.getId(), contactDetails);
                }
            }

        } catch (Exception ex) {
            logger.error("Error processing delivery result for subscriber id={}: {}", subscriber != null ? subscriber.getId() : "unknown", ex.getMessage(), ex);
            // Do not throw; allow Cyoda to persist the (possibly partially updated) entity state.
        }

        return subscriber;
    }
}