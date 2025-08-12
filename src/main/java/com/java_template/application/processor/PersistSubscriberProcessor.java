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
public class PersistSubscriberProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistSubscriberProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PersistSubscriberProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Persisting subscriber for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid Subscriber entity")
            .map(this::persistSubscriber)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber subscriber) {
        if (subscriber == null) {
            logger.error("Subscriber entity is null");
            return false;
        }
        if (subscriber.getContactType() == null || subscriber.getContactType().isEmpty()) {
            logger.error("ContactType is required");
            return false;
        }
        if (subscriber.getContactDetails() == null || subscriber.getContactDetails().isEmpty()) {
            logger.error("ContactDetails is required");
            return false;
        }
        // Additional validation for email or webhook format could be added here
        return true;
    }

    private Subscriber persistSubscriber(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber subscriber = context.entity();
        // Simulate persistence logic
        logger.info("Persisting Subscriber: {}", subscriber.getSubscriberId());
        return subscriber;
    }
}
