package com.java_template.application.processor;

import com.java_template.application.entity.petimportjob.version_1.PetImportJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StartImportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartImportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StartImportProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetImportJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PetImportJob.class)
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

    private boolean isValidEntity(PetImportJob entity) {
        return entity != null && entity.isValid();
    }

    private PetImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetImportJob> context) {
        PetImportJob entity = context.entity();

        // StartImportProcessor:
        // - Initialize counters and errors
        // - Transition job from PENDING to VALIDATING to trigger subsequent validation processors
        // - If job is already beyond PENDING, do not regress status
        try {
            // Ensure importedCount is initialized
            if (entity.getImportedCount() == null || entity.getImportedCount() < 0) {
                entity.setImportedCount(0);
            }

            // Clear previous errors when (re)starting
            entity.setErrors(entity.getErrors() == null ? "" : entity.getErrors());

            String currentStatus = entity.getStatus();
            if (currentStatus == null) {
                // Defensive: ensure a status exists; set to VALIDATING to continue workflow
                entity.setStatus("VALIDATING");
                logger.info("PetImportJob {} had null status. Setting to VALIDATING.", entity.getRequestId());
            } else {
                // Only advance if in initial PENDING state; do not change if job already running or completed/failed
                String normalized = currentStatus.trim().toUpperCase();
                if ("PENDING".equals(normalized)) {
                    entity.setStatus("VALIDATING");
                    logger.info("PetImportJob {} transitioned from PENDING to VALIDATING.", entity.getRequestId());
                } else {
                    logger.info("PetImportJob {} status is '{}'; no transition performed by StartImportProcessor.", entity.getRequestId(), currentStatus);
                }
            }
        } catch (Exception ex) {
            // On unexpected error, mark job as FAILED and capture error summary
            logger.error("Error while starting import for PetImportJob {}: {}", entity.getRequestId(), ex.getMessage(), ex);
            entity.setStatus("FAILED");
            String prevErrors = entity.getErrors() == null ? "" : entity.getErrors();
            String newError = "StartImportProcessor error: " + ex.getMessage();
            entity.setErrors((prevErrors.isBlank() ? "" : prevErrors + " | ") + newError);
        }

        return entity;
    }
}