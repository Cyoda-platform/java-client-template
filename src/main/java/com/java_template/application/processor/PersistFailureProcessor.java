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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;

@Component
public class PersistFailureProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistFailureProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PersistFailureProcessor(SerializerFactory serializerFactory) {
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

        // Mark job as FAILED and record timestamp and error entry.
        String identifier = extractEntityIdentifier(entity);
        logger.info("PersistFailureProcessor updating job '{}' status to FAILED", identifier);

        // Ensure processedCount is not null
        try {
            Method getProcessedCount = entity.getClass().getMethod("getProcessedCount");
            Object processedCount = getProcessedCount.invoke(entity);
            if (processedCount == null) {
                // try to find and call setProcessedCount(Integer) or setProcessedCount(int)
                invokeSetterIfExists(entity, "setProcessedCount", Integer.class, 0);
                invokeSetterIfExists(entity, "setProcessedCount", int.class, 0);
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            // If processed count accessors don't exist, ignore — preserve functionality where possible.
        }

        // Ensure errors list is initialized
        try {
            Method getErrors = entity.getClass().getMethod("getErrors");
            Object errorsObj = getErrors.invoke(entity);
            if (errorsObj == null) {
                invokeSetterIfExists(entity, "setErrors", java.util.List.class, new ArrayList<>());
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            // If errors accessors don't exist, ignore.
        }

        // Append failure detail with timestamp and processor info
        String errDetail = String.format("Job marked FAILED by %s at %s", className, Instant.now().toString());
        try {
            Method getErrors = entity.getClass().getMethod("getErrors");
            Object errorsObj = getErrors.invoke(entity);
            if (errorsObj instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> errorsList = (java.util.List<Object>) errorsObj;
                errorsList.add(errDetail);
            } else {
                // If getErrors isn't present or isn't a list, attempt to call addError(String) if exists
                invokeMethodIfExists(entity, "addError", String.class, errDetail);
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            // fallback: try addError
            invokeMethodIfExists(entity, "addError", String.class, errDetail);
        }

        // Update status and completion time if setters exist
        invokeSetterIfExists(entity, "setStatus", String.class, "FAILED");
        invokeSetterIfExists(entity, "setCompletedAt", String.class, Instant.now().toString());

        logger.warn("PetIngestionJob '{}' failed: {}", identifier, errDetail);

        return entity;
    }

    private String extractEntityIdentifier(PetIngestionJob entity) {
        // Try multiple common identifier methods via reflection
        String[] candidateGetters = {"getJobName", "getJob", "getName", "getId", "getJobId"};
        for (String getter : candidateGetters) {
            try {
                Method m = entity.getClass().getMethod(getter);
                Object val = m.invoke(entity);
                if (val != null) {
                    return String.valueOf(val);
                }
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            }
        }
        // Fallback to toString
        return String.valueOf(entity);
    }

    private void invokeSetterIfExists(Object target, String methodName, Class<?> paramType, Object value) {
        try {
            Method m = target.getClass().getMethod(methodName, paramType);
            m.invoke(target, value);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            // Method not present or invocation failed; silently ignore to maintain compatibility
        }
    }

    private void invokeMethodIfExists(Object target, String methodName, Class<?> paramType, Object value) {
        try {
            Method m = target.getClass().getMethod(methodName, paramType);
            m.invoke(target, value);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            // ignore
        }
    }
}