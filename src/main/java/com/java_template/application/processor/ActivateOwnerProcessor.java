package com.java_template.application.processor;

import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Component
public class ActivateOwnerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ActivateOwnerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ActivateOwnerProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Owner.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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
        if (entity == null) {
            logger.warn("Owner entity is null in processing context");
            return null;
        }

        try {
            // Business rule: Activate owner only when verified == true.
            // Owner entity does not contain an explicit 'status' field, so activation
            // is represented by ensuring the entity is prepared for active use:
            // - petsOwned list initialized (so downstream logic can safely add pets)
            // - phone trimmed (normalize contact)
            // - log activation event
            if (Boolean.TRUE.equals(entity.getVerified())) {
                // Ensure petsOwned is initialized (empty list if none)
                if (entity.getPetsOwned() == null) {
                    entity.setPetsOwned(new ArrayList<>());
                } else {
                    // Remove any blank entries defensively
                    List<String> cleaned = new ArrayList<>();
                    for (String petId : entity.getPetsOwned()) {
                        if (petId != null && !petId.isBlank()) {
                            cleaned.add(petId);
                        }
                    }
                    entity.setPetsOwned(cleaned);
                }

                // Normalize phone (trim). If becomes blank set to null.
                if (entity.getPhone() != null) {
                    String trimmed = entity.getPhone().trim();
                    if (trimmed.isBlank()) {
                        entity.setPhone(null);
                    } else {
                        entity.setPhone(trimmed);
                    }
                }

                logger.info("Owner [{}] is verified and considered activated.", entity.getId());
            } else {
                // Not verified — nothing to activate. Log for observability.
                logger.info("Owner [{}] is not verified; skipping activation.", entity.getId());
            }

        } catch (Exception ex) {
            // Do not throw; log the error. Returning the entity ensures the workflow can handle persistence.
            logger.error("Error while activating owner [{}]: {}", entity.getId(), ex.getMessage(), ex);
        }

        return entity;
    }
}