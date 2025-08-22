package com.java_template.application.processor;

import com.java_template.application.entity.owner.version_1.Owner;
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

@Component
public class OwnerActivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OwnerActivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public OwnerActivationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Owner.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Owner entity) {
        return entity != null && entity.isValid();
    }

    private Owner processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Owner> context) {
        Owner entity = context.entity();

        // Activation business logic:
        // - Only activate if owner is verified (verified == true)
        // - Ensure role is set (default to "user") when activating
        // - Do not modify other entities from here; only adjust this entity's state (it will be persisted by Cyoda)
        if (entity == null) {
            logger.warn("Owner entity is null in execution context");
            return null;
        }

        Boolean verified = entity.getVerified();
        if (Boolean.TRUE.equals(verified)) {
            // If role is missing, assign default role "user" upon activation
            if (entity.getRole() == null || entity.getRole().isBlank()) {
                entity.setRole("user");
                logger.info("Owner {} verified — setting default role to 'user'", entity.getId());
            } else {
                logger.info("Owner {} verified — existing role '{}'", entity.getId(), entity.getRole());
            }
            // No explicit 'status' field on Owner entity; verification flag + role indicate activation.
        } else {
            logger.warn("Owner {} is not verified. Skipping activation changes.", entity.getId());
        }

        return entity;
    }
}