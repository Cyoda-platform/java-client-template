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
public class ProcessSubscriber implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessSubscriber.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProcessSubscriber(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Subscriber.class)
                .validate(this::isValidEntity, "Invalid Subscriber entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber entity) {
        if (entity == null) return false;
        // Validate contactType and contactValue
        if (entity.getContactType() == null || entity.getContactType().trim().isEmpty()) {
            return false;
        }
        if (entity.getContactValue() == null || entity.getContactValue().trim().isEmpty()) {
            return false;
        }
        if (entity.getActive() == null) {
            return false;
        }
        return true;
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber subscriber = context.entity();
        // No enrichment specified for subscriber
        // Just log processing
        logger.info("Processed Subscriber with contactType: {}", subscriber.getContactType());
        return subscriber;
    }
}
