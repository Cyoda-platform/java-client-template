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
public class SubscriberValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubscriberValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Validating Subscriber for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidSubscriber)
            .map(this::processValidationLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidSubscriber(Subscriber subscriber) {
        if (subscriber == null) {
            logger.error("Subscriber entity is null");
            return false;
        }
        if (subscriber.getContactType() == null || subscriber.getContactType().isEmpty()) {
            logger.error("ContactType is required");
            return false;
        }
        if (subscriber.getContactValue() == null || subscriber.getContactValue().isEmpty()) {
            logger.error("ContactValue is required");
            return false;
        }
        if (!subscriber.getContactType().equalsIgnoreCase("email") && !subscriber.getContactType().equalsIgnoreCase("webhook")) {
            logger.error("ContactType must be 'email' or 'webhook'");
            return false;
        }
        return true;
    }

    private Subscriber processValidationLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber subscriber = context.entity();
        // Additional validation or flag setting can be done here
        return subscriber;
    }
}
