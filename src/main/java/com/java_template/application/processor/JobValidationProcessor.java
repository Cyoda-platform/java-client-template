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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class JobValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public JobValidationProcessor(SerializerFactory serializerFactory) {
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

        // Use reflection to read and write fields to avoid compile-time dependency on Lombok-generated accessors
        try {
            String sourceUrl = getStringField(entity, "sourceUrl");
            String jobId = getStringField(entity, "jobId");

            if (sourceUrl == null || sourceUrl.isBlank()) {
                setStringField(entity, "status", "FAILED");
                setStringField(entity, "error", "sourceUrl is blank");
                logger.warn("Job {} failed validation: sourceUrl is blank", jobId != null ? jobId : "unknown");
                return entity;
            }

            URI uri;
            try {
                uri = URI.create(sourceUrl);
                String scheme = uri.getScheme();
                if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                    setStringField(entity, "status", "FAILED");
                    setStringField(entity, "error", "sourceUrl must use http or https");
                    logger.warn("Job {} failed validation: invalid scheme in sourceUrl {}", jobId != null ? jobId : "unknown", sourceUrl);
                    return entity;
                }
            } catch (Exception e) {
                setStringField(entity, "status", "FAILED");
                setStringField(entity, "error", "sourceUrl is not a valid URI: " + e.getMessage());
                logger.warn("Job {} failed validation: invalid URI {} - {}", jobId != null ? jobId : "unknown", sourceUrl, e.getMessage());
                return entity;
            }

            // Reachability check: lightweight GET request with timeout
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest httpRequest;
            try {
                httpRequest = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
            } catch (Exception e) {
                setStringField(entity, "status", "FAILED");
                setStringField(entity, "error", "Failed to build request for sourceUrl: " + e.getMessage());
                logger.warn("Job {} failed to build request for {}: {}", jobId != null ? jobId : "unknown", sourceUrl, e.getMessage());
                return entity;
            }

            HttpResponse<Void> response;
            try {
                response = client.send(httpRequest, HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                setStringField(entity, "status", "FAILED");
                setStringField(entity, "error", "Failed to reach sourceUrl: " + e.getMessage());
                logger.warn("Job {} cannot reach {}: {}", jobId != null ? jobId : "unknown", sourceUrl, e.getMessage());
                return entity;
            }

            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                // Validation passed: mark job ready for fetching
                setStringField(entity, "status", "FETCHING");
                setStringField(entity, "error", null);

                Integer fetched = getIntegerField(entity, "fetchedCount");
                Integer created = getIntegerField(entity, "createdCount");
                if (fetched == null) setIntegerField(entity, "fetchedCount", 0);
                if (created == null) setIntegerField(entity, "createdCount", 0);

                logger.info("Job {} validated successfully against {}. Status set to FETCHING", jobId != null ? jobId : "unknown", sourceUrl);
            } else {
                setStringField(entity, "status", "FAILED");
                setStringField(entity, "error", "Source URL returned non-success status: " + statusCode);
                logger.warn("Job {} failed validation: {} returned status {}", jobId != null ? jobId : "unknown", sourceUrl, statusCode);
            }
        } catch (Exception ex) {
            // Catch-all to ensure processor doesn't crash; mark job failed with error details
            try {
                setStringField(entity, "status", "FAILED");
                String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                setStringField(entity, "error", "Unexpected validation error: " + msg);
                logger.error("Unexpected error validating job {}: {}", entity != null ? getStringField(entity, "jobId") : "unknown", msg, ex);
            } catch (Exception inner) {
                logger.error("Failed to set failure status on entity after validation exception: {}", inner.getMessage(), inner);
            }
        }

        return entity;
    }

    // Reflection helpers
    private String getStringField(Object target, String fieldName) {
        Object val = getFieldValue(target, fieldName);
        return val != null ? String.valueOf(val) : null;
    }

    private Integer getIntegerField(Object target, String fieldName) {
        Object val = getFieldValue(target, fieldName);
        if (val == null) return null;
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return Integer.valueOf(String.valueOf(val));
        } catch (Exception e) {
            return null;
        }
    }

    private void setStringField(Object target, String fieldName, String value) {
        setFieldValue(target, fieldName, value);
    }

    private void setIntegerField(Object target, String fieldName, Integer value) {
        setFieldValue(target, fieldName, value);
    }

    private Object getFieldValue(Object target, String fieldName) {
        if (target == null) return null;
        try {
            Field f = findField(target.getClass(), fieldName);
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) {
            logger.debug("Reflection get failed for field '{}' on {}: {}", fieldName, target.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

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
        } catch (Exception e) {
            logger.warn("Reflection set failed for field '{}' on {}: {}", fieldName, target.getClass().getSimpleName(), e.getMessage());
        }
    }

    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> cur = clazz;
        while (cur != null) {
            try {
                Field f = cur.getDeclaredField(fieldName);
                return f;
            } catch (NoSuchFieldException nsf) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }
}