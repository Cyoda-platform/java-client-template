package com.java_template.application.processor;

import com.java_template.application.entity.Subscription;
import com.java_template.common.serializer.ErrorInfo;
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

        return serializer.withRequest(request)
            .toEntity(Subscription.class)
            .validate(this::isValidSubscription, "Invalid subscription state")
            .map(this::processSubscriptionLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "SubscriptionProcessor".equals(modelSpec.operationName()) &&
               "subscription".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidSubscription(Subscription subscription) {
        if (subscription.getUserId() == null || subscription.getUserId().isBlank()) {
            logger.error("Invalid Subscription: userId is null or blank");
            return false;
        }
        // Additional validation can be added here
        return true;
    }

    private Subscription processSubscriptionLogic(Subscription subscription) {
        logger.info("Registering subscription with ID: {}", subscription.getSubscriptionId());
        // As per prototype, this method should register the subscription for event notifications
        // There is no external call or update on the current entity required here.
        // The subscription is considered ready to receive notifications post this processing.
        return subscription;
    }
}