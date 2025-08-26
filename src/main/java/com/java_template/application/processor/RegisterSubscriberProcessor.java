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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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

        // Activation logic:
        // - Ensure contact email is present
        // - Ensure subscribedCategories contains valid entries
        // If verification passes, normalize categories and set active = true
        boolean contactValid = false;
        if (entity.getContact() != null && entity.getContact().getEmail() != null && !entity.getContact().getEmail().isBlank()) {
            contactValid = true;
        }

        boolean categoriesValid = false;
        if (entity.getSubscribedCategories() != null && !entity.getSubscribedCategories().isEmpty()) {
            // check at least one non-blank category
            categoriesValid = entity.getSubscribedCategories().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .anyMatch(s -> !s.isBlank());
        }

        if (contactValid && categoriesValid) {
            // Normalize categories: trim, lowercase, remove blanks & duplicates
            List<String> normalized = entity.getSubscribedCategories().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toList());
            entity.setSubscribedCategories(normalized);

            entity.setActive(true);
            logger.info("Subscriber {} activated", entity.getId());
        } else {
            // Do not activate if verification fails. Keep state consistent.
            entity.setActive(false);
            logger.info("Subscriber {} not activated - contactValid={} categoriesValid={}", entity.getId(), contactValid, categoriesValid);
        }

        return entity;
    }
}