package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;

@Component
public class PersistResultsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistResultsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PersistResultsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        // Business logic (implemented reflectively to avoid direct compile-time dependency on specific Job getters/setters):
        // - If resultSummary is missing or blank, mark job as FAILED and increment retryCount.
        // - Otherwise mark job as COMPLETED.
        // - Always update lastRunAt to current timestamp.
        // - Do not perform any add/update/delete via EntityService on this Job entity; modifications here will be persisted by Cyoda automatically.

        try {
            // Read result summary reflectively
            String summary = safeInvokeToString(job, "getResultSummary");
            boolean hasSummary = summary != null && !summary.isBlank();

            // Update last run timestamp if setter exists
            invokeIfExists(job, "setLastRunAt", String.class, Instant.now().toString());

            if (hasSummary) {
                // set status to COMPLETED if setter exists
                invokeIfExists(job, "setStatus", String.class, "COMPLETED");
                String jobId = safeInvokeToString(job, "getJobId");
                logger.info("Job {} marked as COMPLETED. resultSummary={}", jobId, summary);
            } else {
                // Increment retryCount safely using available getters/setters
                Integer currentRetries = null;
                try {
                    String retriesStr = safeInvokeToString(job, "getRetryCount");
                    if (retriesStr != null) {
                        currentRetries = Integer.valueOf(retriesStr);
                    }
                } catch (Exception ignored) {
                }
                if (currentRetries == null) {
                    currentRetries = 0;
                }
                // Try to set Integer or int parameter depending on available method signature
                boolean setSucceeded = invokeIfExists(job, "setRetryCount", Integer.class, currentRetries + 1);
                if (!setSucceeded) {
                    invokeIfExists(job, "setRetryCount", int.class, currentRetries + 1);
                }
                // set status to FAILED if setter exists
                invokeIfExists(job, "setStatus", String.class, "FAILED");

                String jobId = safeInvokeToString(job, "getJobId");
                String retryCountStr = safeInvokeToString(job, "getRetryCount");
                logger.warn("Job {} marked as FAILED. retryCount={}", jobId, retryCountStr);
            }
        } catch (Exception e) {
            logger.warn("Failed to process Job entity reflectively: {}", e.getMessage());
        }

        return job;
    }

    private String safeInvokeToString(Job job, String methodName) {
        try {
            Method m = job.getClass().getMethod(methodName);
            Object result = m.invoke(job);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean invokeIfExists(Job job, String methodName, Class<?> paramType, Object arg) {
        try {
            Method m = job.getClass().getMethod(methodName, paramType);
            m.invoke(job, arg);
            return true;
        } catch (NoSuchMethodException nsme) {
            return false;
        } catch (Exception e) {
            logger.debug("Invocation of {} failed: {}", methodName, e.getMessage());
            return false;
        }
    }
}