package com.example.application.processor;

import com.example.application.entity.subscription.version_1.Subscription;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * SubscriptionProcessor - Manages subscription lifecycle and billing
 * Handles activation, renewal, proration, and retry logic
 */
@Component
public class SubscriptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public SubscriptionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscription for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Subscription.class)
                .validate(this::isValidEntityWithMetadata, "Invalid subscription")
                .map(this::processSubscriptionLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Subscription> entityWithMetadata) {
        Subscription entity = entityWithMetadata.entity();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) &&
               entityWithMetadata.metadata().getId() != null;
    }

    private EntityWithMetadata<Subscription> processSubscriptionLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Subscription> context) {

        EntityWithMetadata<Subscription> entityWithMetadata = context.entityResponse();
        Subscription subscription = entityWithMetadata.entity();

        logger.debug("Processing subscription: {} for customer: {}", 
                   subscription.getSubscriptionId(), subscription.getCustomerId());

        // Initialize subscription
        if (subscription.getStatus() == null) {
            subscription.setStatus("ACTIVE");
        }

        if (subscription.getStartDate() == null) {
            subscription.setStartDate(LocalDateTime.now());
        }

        // Calculate next billing date
        calculateNextBillingDate(subscription);

        // Initialize retry policy
        if (subscription.getMaxRetries() == null) {
            subscription.setMaxRetries(3);
        }

        if (subscription.getFailedAttempts() == null) {
            subscription.setFailedAttempts(0);
        }

        // Update timestamps
        subscription.setUpdatedAt(LocalDateTime.now());
        if (subscription.getCreatedAt() == null) {
            subscription.setCreatedAt(LocalDateTime.now());
        }

        logger.info("Subscription {} processed successfully", subscription.getSubscriptionId());
        return entityWithMetadata;
    }

    private void calculateNextBillingDate(Subscription subscription) {
        LocalDateTime baseDate = subscription.getLastBillingDate() != null ? 
                                subscription.getLastBillingDate() : LocalDateTime.now();

        LocalDateTime nextDate = switch (subscription.getBillingCycle()) {
            case "DAILY" -> baseDate.plus(1, ChronoUnit.DAYS);
            case "WEEKLY" -> baseDate.plus(7, ChronoUnit.DAYS);
            case "MONTHLY" -> baseDate.plus(1, ChronoUnit.MONTHS);
            case "YEARLY" -> baseDate.plus(1, ChronoUnit.YEARS);
            default -> baseDate.plus(1, ChronoUnit.MONTHS);
        };

        subscription.setNextBillingDate(nextDate);
        logger.debug("Next billing date calculated: {} for subscription: {}", 
                   nextDate, subscription.getSubscriptionId());
    }
}

