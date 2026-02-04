package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * SubscriberProcessor - Handles subscriber lifecycle events
 * 
 * This processor manages subscription and unsubscription logic.
 */
@Component
public class SubscriberProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubscriberProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Subscriber.class)
                .validate(this::isValidSubscriber, "Invalid Subscriber")
                .map(ctx -> {
                    Subscriber subscriber = ctx.entityResponse().entity();
                    String state = ctx.entityResponse().metadata().getState();
                    
                    // Set timestamps based on state
                    if ("active".equals(state) && subscriber.getSubscribedAt() == null) {
                        subscriber.setSubscribedAt(LocalDateTime.now());
                    } else if ("unsubscribed".equals(state) && subscriber.getUnsubscribedAt() == null) {
                        subscriber.setUnsubscribedAt(LocalDateTime.now());
                    }
                    
                    logger.info("Subscriber processed: {} - {}", subscriber.getEmail(), state);
                    return ctx.entityResponse();
                })
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidSubscriber(com.java_template.common.dto.EntityWithMetadata<Subscriber> entityWithMetadata) {
        Subscriber subscriber = entityWithMetadata.entity();
        return subscriber != null && subscriber.isValid(entityWithMetadata.metadata());
    }
}

