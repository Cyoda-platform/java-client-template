package com.java_template.application.processor;

import com.java_template.application.entity.catfact.version_1.CatFact;
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

@Component
public class ArchiveInvalidFactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveInvalidFactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ArchiveInvalidFactProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CatFact for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(CatFact.class)
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

    private boolean isValidEntity(CatFact entity) {
        return entity != null && "INVALID".equalsIgnoreCase(entity.getValidationStatus());
    }

    private CatFact processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CatFact> context) {
        CatFact entity = context.entity();

        // Business logic:
        // Archive CatFact entities that have validationStatus == "INVALID".
        // We represent archived state by setting validationStatus to "ARCHIVED".
        // Do not perform any external entity add/update/delete operations here.
        if (entity == null) {
            logger.warn("Received null CatFact in ArchiveInvalidFactProcessor");
            return null;
        }

        String currentStatus = entity.getValidationStatus();
        if (currentStatus == null) {
            logger.warn("CatFact has null validationStatus, skipping archive");
            return entity;
        }

        if ("ARCHIVED".equalsIgnoreCase(currentStatus)) {
            logger.info("CatFact already archived (technicalId={}): no action taken", entity.getTechnicalId());
            return entity;
        }

        // Only archive if currently marked INVALID (validated by isValidEntity), but double-check:
        if ("INVALID".equalsIgnoreCase(currentStatus)) {
            entity.setValidationStatus("ARCHIVED");
            logger.info("Archived invalid CatFact (technicalId={})", entity.getTechnicalId());
        } else {
            logger.info("CatFact validationStatus is not INVALID (technicalId={}, status={}), no archive performed",
                    entity.getTechnicalId(), currentStatus);
        }

        return entity;
    }
}