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
public class DeliveryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DeliveryProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Delivery for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid subscriber entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber subscriber) {
        return subscriber != null && subscriber.getTechnicalId() != null && !subscriber.getTechnicalId().isEmpty();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber subscriber = context.entity();
        try {
            logger.info("Delivering notification to subscriber {} (type={})", subscriber.getTechnicalId(), subscriber.getContactType());
            // In a real implementation: deliver by email or webhook depending on contactType
            // Implement idempotency key for webhooks and retry logic for transient errors
            subscriber.setLastNotifiedAt(Instant.now().toString());
            subscriber.setLastNotificationStatus("DELIVERED");
        } catch (Exception e) {
            logger.error("Delivery failed for subscriber {}: {}", subscriber.getTechnicalId(), e.getMessage(), e);
            subscriber.setLastNotificationStatus("FAILED");
        }
        return subscriber;
    }
}
