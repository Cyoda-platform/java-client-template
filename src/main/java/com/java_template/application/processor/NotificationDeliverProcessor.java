package com.java_template.application.processor;

import com.java_template.application.entity.changeevent.version_1.ChangeEvent;
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
public class NotificationDeliverProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotificationDeliverProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NotificationDeliverProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing NotificationDeliver for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ChangeEvent.class)
            .validate(this::isValidEntity, "Invalid change event for delivery")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ChangeEvent evt) {
        return evt != null && evt.getDeliveryRecords() != null;
    }

    private ChangeEvent processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ChangeEvent> context) {
        ChangeEvent evt = context.entity();
        try {
            // Simulated delivery: iterate deliveryRecords and attempt deliver
            for (Object drObj : evt.getDeliveryRecords()) {
                // In our simplified model drObj may be a Map
                if (drObj instanceof java.util.Map) {
                    java.util.Map rec = (java.util.Map) drObj;
                    String subscriberId = (String) rec.get("subscriberTechnicalId");
                    logger.info("Delivering event {} to subscriber {}", evt.getEventId(), subscriberId);
                    // Create DeliveryRecord placeholder
                }
            }
            // For demo mark status as PROCESSING
            evt.setStatus(ChangeEvent.Status.PROCESSING);
        } catch (Exception e) {
            logger.error("Error delivering notifications for event {}: {}", evt.getEventId(), e.getMessage());
        }
        return evt;
    }
}
