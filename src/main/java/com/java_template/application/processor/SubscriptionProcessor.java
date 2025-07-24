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

import java.util.function.Function;

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
                .validate(Subscription::isValid, "Invalid subscription state")
                .map(this::processSubscriptionLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "SubscriptionProcessor".equals(modelSpec.operationName()) &&
                "subscription".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Subscription processSubscriptionLogic(Subscription subscription) {
        logger.info("Processing subscription with ID: {}", subscription.getSubscriptionId());
        // Business logic copied from processSubscription flow:
        // Initial State: Subscription entity created with ACTIVE status.
        // Validation: Check subscription fields (valid userId, team exists, etc.).
        // Processing: Register subscription for event notifications.
        // Completion: Subscription ready to receive notifications.

        // Since no specific business logic methods were provided in the prototype for Subscription processing,
        // we assume the subscription is valid and ready to be registered.
        // No modifications done to subscription entity here.
        return subscription;
    }
}
