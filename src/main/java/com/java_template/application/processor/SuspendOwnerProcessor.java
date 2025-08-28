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

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SuspendOwnerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SuspendOwnerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SuspendOwnerProcessor(SerializerFactory serializerFactory) {
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

        // Business rules for suspending an owner:
        // - If owner is already suspended (verificationStatus == "suspended"), do nothing.
        // - Otherwise set verificationStatus to "suspended".
        // - Preserve other fields (savedPets, adoptedPets, contact info).
        // Note: Owner entity does not have a dedicated "state" field; reuse verificationStatus for suspension state.

        try {
            String currentStatus = entity.getVerificationStatus();
            if (currentStatus != null && currentStatus.equalsIgnoreCase("suspended")) {
                logger.info("Owner {} is already suspended. No changes applied.", entity.getOwnerId());
                return entity;
            }

            // Apply suspension
            entity.setVerificationStatus("suspended");
            logger.info("Owner {} suspended (verificationStatus set to 'suspended').", entity.getOwnerId());

            // Additional safety checks/logging can be added here if needed.
        } catch (Exception ex) {
            logger.error("Error while suspending owner {}: {}", entity != null ? entity.getOwnerId() : "unknown", ex.getMessage(), ex);
            // If an unexpected error occurs, do not throw to avoid breaking the processor chain.
            // The serializer's error handler will handle toEntity extraction errors. Here we just log.
        }

        return entity;
    }
}