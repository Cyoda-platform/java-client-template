package com.java_template.application.processor;

import com.java_template.application.entity.changeevent.version_1.ChangeEvent;
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
public class DeliveryRetryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryRetryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DeliveryRetryProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DeliveryRetry for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ChangeEvent.class)
            .validate(this::isValidEntity, "Invalid change event for retry")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ChangeEvent evt) {
        return evt != null && evt.getDeliveryRecords() != null && !evt.getDeliveryRecords().isEmpty();
    }

    private ChangeEvent processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ChangeEvent> context) {
        ChangeEvent evt = context.entity();
        try {
            // Simulate scheduling retry - in real system create retry records with backoff
            logger.info("Scheduling retry for event {}", evt.getEventId());
            evt.setStatus(ChangeEvent.Status.CREATED);
        } catch (Exception e) {
            logger.error("Error scheduling retry for event {}: {}", evt.getEventId(), e.getMessage());
        }
        return evt;
    }
}
