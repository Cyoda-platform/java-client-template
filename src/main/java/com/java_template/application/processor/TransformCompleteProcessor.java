package com.java_template.application.processor;
import com.java_template.application.entity.petingestionjob.version_1.PetIngestionJob;
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

import java.util.ArrayList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Component
public class TransformCompleteProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TransformCompleteProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public TransformCompleteProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetIngestionJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PetIngestionJob.class)
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

    private boolean isValidEntity(PetIngestionJob entity) {
        return entity != null && entity.isValid();
    }

    private PetIngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetIngestionJob> context) {
        PetIngestionJob entity = context.entity();
        
        // Business logic: mark transform as complete and move job to PERSISTING stage.
        // Ensure required numeric and collection fields are initialized to keep entity valid.
        try {
            logger.info("TransformCompleteProcessor - marking job as PERSISTING. Entity: {}", entity);

            // Ensure processedCount is non-null (required by isValid)
            if (entity.getProcessedCount() == null) {
                entity.setProcessedCount(0);
            }

            // Ensure errors list is present
            if (entity.getErrors() == null) {
                entity.setErrors(new ArrayList<>());
            }

            // Attempt to set status to indicate next phase: PERSISTING using reflection if direct setter is not available.
            boolean statusSet = false;
            try {
                Method setStatusMethod = null;
                try {
                    setStatusMethod = entity.getClass().getMethod("setStatus", String.class);
                } catch (NoSuchMethodException nsme) {
                    // method not found, will try field
                    setStatusMethod = null;
                }
                if (setStatusMethod != null) {
                    setStatusMethod.invoke(entity, "PERSISTING");
                    statusSet = true;
                } else {
                    // try to set a field named "status"
                    try {
                        Field statusField = entity.getClass().getDeclaredField("status");
                        statusField.setAccessible(true);
                        statusField.set(entity, "PERSISTING");
                        statusSet = true;
                    } catch (NoSuchFieldException | IllegalAccessException fieldEx) {
                        statusSet = false;
                    }
                }
            } catch (Exception ex) {
                logger.warn("Unable to set status via reflection: {}", ex.getMessage());
                statusSet = false;
            }

            if (!statusSet) {
                // If we couldn't set status, record this as an informational error on the entity
                if (entity.getErrors() == null) {
                    entity.setErrors(new ArrayList<>());
                }
                entity.getErrors().add("TransformCompleteProcessor: unable to set status to PERSISTING");
            }

        } catch (Exception ex) {
            logger.error("Error while processing PetIngestionJob in TransformCompleteProcessor: {}", ex.getMessage(), ex);
            // Do not throw — allow serializer to handle persistence; keep entity in a safe state
            if (entity.getErrors() == null) {
                entity.setErrors(new ArrayList<>());
            }
            entity.getErrors().add("TransformCompleteProcessor error: " + ex.getMessage());
        }

        return entity;
    }
}