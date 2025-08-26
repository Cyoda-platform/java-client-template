package com.java_template.application.processor;
import com.java_template.application.entity.commentanalysisjob.version_1.CommentAnalysisJob;
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

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.regex.Pattern;

@Component
public class ValidateJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public ValidateJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CommentAnalysisJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(CommentAnalysisJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CommentAnalysisJob entity) {
        return entity != null && entity.isValid();
    }

    private CommentAnalysisJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CommentAnalysisJob> context) {
        CommentAnalysisJob entity = context.entity();

        // Use reflection to read/write fields to avoid depending on generated Lombok methods at compile time.
        // Set status to VALIDATING while checks occur
        setFieldValue(entity, "status", "VALIDATING");

        String postId = asString(getFieldValue(entity, "postId"));
        String recipientEmail = asString(getFieldValue(entity, "recipientEmail"));
        String technicalId = asString(getFieldValue(entity, "id"));

        // Basic checks: postId must be present and recipientEmail must be a valid-looking email
        boolean postIdValid = postId != null && !postId.isBlank();
        boolean emailValid = recipientEmail != null && !recipientEmail.isBlank() && EMAIL_PATTERN.matcher(recipientEmail).matches();

        if (!postIdValid || !emailValid) {
            logger.warn("Validation failed for CommentAnalysisJob technicalId={} postIdValid={} emailValid={}", technicalId, postIdValid, emailValid);
            setFieldValue(entity, "status", "FAILED");
            // mark completion time for failed jobs
            setFieldValue(entity, "completedAt", Instant.now().toString());
            return entity;
        }

        // Validation passed -> move to ingestion phase
        setFieldValue(entity, "status", "INGESTING");
        return entity;
    }

    // Helper: get field value via reflection (searching superclasses)
    private Object getFieldValue(Object target, String fieldName) {
        if (target == null) return null;
        try {
            Field f = findField(target.getClass(), fieldName);
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(target);
        } catch (IllegalAccessException ex) {
            logger.warn("Unable to access field '{}' on {}: {}", fieldName, target.getClass().getSimpleName(), ex.getMessage());
            return null;
        }
    }

    // Helper: set field value via reflection (searching superclasses)
    private void setFieldValue(Object target, String fieldName, Object value) {
        if (target == null) return;
        try {
            Field f = findField(target.getClass(), fieldName);
            if (f == null) {
                logger.debug("Field '{}' not found on {}", fieldName, target.getClass().getSimpleName());
                return;
            }
            f.setAccessible(true);
            f.set(target, value);
        } catch (IllegalAccessException ex) {
            logger.warn("Unable to set field '{}' on {}: {}", fieldName, target.getClass().getSimpleName(), ex.getMessage());
        }
    }

    // Recursively search for a field in class hierarchy
    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && !current.equals(Object.class)) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}