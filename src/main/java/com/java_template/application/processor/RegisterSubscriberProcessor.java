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
public class RegisterSubscriberProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RegisterSubscriberProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RegisterSubscriberProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber entity = context.entity();
        if (entity == null) return null;

        logger.info("RegisterSubscriberProcessor: registering technicalId={}", entity.getTechnicalId());

        // Normalize contact type
        if (entity.getContactType() != null) {
            String normalized = entity.getContactType().trim().toLowerCase();
            entity.setContactType(normalized);
        }

        // Ensure filters validity: if filters present but categories invalid, remove filters to avoid leaving entity invalid
        if (entity.getFilters() != null) {
            try {
                if (entity.getFilters().getCategories() == null || entity.getFilters().getCategories().isEmpty()) {
                    entity.setFilters(null);
                } else {
                    boolean anyInvalid = entity.getFilters().getCategories().stream()
                        .anyMatch(c -> c == null || c.isBlank());
                    if (anyInvalid) {
                        entity.setFilters(null);
                    }
                }
            } catch (Exception ex) {
                logger.warn("Invalid filters for subscriber {}: removing filters", entity.getTechnicalId());
                entity.setFilters(null);
            }
        }

        // Validate contact address according to type
        boolean contactValid = true;
        String type = entity.getContactType();
        String address = entity.getContactAddress();
        if (type != null) {
            if ("email".equalsIgnoreCase(type)) {
                if (address == null || address.isBlank() || !address.contains("@")) {
                    contactValid = false;
                    logger.warn("Subscriber {} has invalid email address: {}", entity.getTechnicalId(), address);
                }
            } else if ("webhook".equalsIgnoreCase(type)) {
                if (address == null || address.isBlank() ||
                    !(address.startsWith("http://") || address.startsWith("https://"))) {
                    contactValid = false;
                    logger.warn("Subscriber {} has invalid webhook url: {}", entity.getTechnicalId(), address);
                }
            } else {
                // unknown contact type: mark as invalid
                contactValid = false;
                logger.warn("Subscriber {} has unknown contactType: {}", entity.getTechnicalId(), type);
            }
        } else {
            contactValid = false;
            logger.warn("Subscriber {} contactType is null", entity.getTechnicalId());
        }

        // Apply activation logic: activate only if contact is valid
        if (contactValid) {
            entity.setActive(Boolean.TRUE);
        } else {
            entity.setActive(Boolean.FALSE);
        }

        // Ensure preferred payload default
        if (entity.getPreferredPayload() == null || entity.getPreferredPayload().isBlank()) {
            entity.setPreferredPayload("summary");
        }

        // Ensure retry policy defaults and validation
        if (entity.getRetryPolicy() == null) {
            Subscriber.RetryPolicy rp = new Subscriber.RetryPolicy();
            rp.setMaxAttempts(3);
            rp.setBackoffSeconds(60);
            entity.setRetryPolicy(rp);
        } else {
            if (entity.getRetryPolicy().getMaxAttempts() == null || entity.getRetryPolicy().getMaxAttempts() < 1) {
                entity.getRetryPolicy().setMaxAttempts(3);
            }
            if (entity.getRetryPolicy().getBackoffSeconds() == null || entity.getRetryPolicy().getBackoffSeconds() < 0) {
                entity.getRetryPolicy().setBackoffSeconds(60);
            }
        }

        logger.info("Subscriber {} registration completed. active={}, contactType={}", entity.getTechnicalId(), entity.getActive(), entity.getContactType());
        return entity;
    }
}