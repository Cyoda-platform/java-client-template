package com.java_template.application.processor;

import com.java_template.application.entity.Subscription;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public SubscriptionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("SubscriptionProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscription for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(Subscription.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "SubscriptionProcessor".equals(modelSpec.operationName()) &&
               "subscription".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(Subscription entity) {
        return entity.isValid();
    }

    private Subscription processEntityLogic(Subscription subscription) {
        logger.info("Processing Subscription with ID: {}", subscription.getSubscriptionId());
        if (subscription.getUserId() == null || subscription.getUserId().isBlank()) {
            logger.error("Subscription userId is invalid");
            return subscription;
        }
        if (subscription.getNotificationType() == null || subscription.getNotificationType().isBlank()) {
            logger.error("Subscription notificationType is invalid");
            return subscription;
        }
        if (subscription.getChannel() == null || subscription.getChannel().isBlank()) {
            logger.error("Subscription channel is invalid");
            return subscription;
        }
        logger.info("Subscription {} processed successfully", subscription.getSubscriptionId());
        return subscription;
    }

}