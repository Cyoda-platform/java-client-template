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

import java.util.Map;

@Component
public class PrepareJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PrepareJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private static final int DEFAULT_MAX_RETRIES = 2;

    public PrepareJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PrepareJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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
        try {
            if (job.getRetriesPolicy() == null) {
                job.setRetriesPolicy(Map.of("maxRetries", DEFAULT_MAX_RETRIES));
                logger.info("Set default retriesPolicy for Job {} to {}", job.getId(), DEFAULT_MAX_RETRIES);
            }
            // basic parameter validation
            if (job.getJobType() == null || job.getJobType().isEmpty()) {
                job.setStatus("FAILED");
                logger.warn("Job {} missing jobType, marking failed", job.getId());
            }
        } catch (Exception ex) {
            logger.error("Error preparing Job {}: {}", job.getId(), ex.getMessage(), ex);
        }
        return job;
    }
}
