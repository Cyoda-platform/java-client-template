package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
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

import java.time.Instant;
import java.util.UUID;

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
        logger.info("Processing JobValidationProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.getTechnicalId() != null && !job.getTechnicalId().isEmpty();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // Basic validation rules derived from functional requirements
        boolean valid = true;

        // schedule validation: if job is not manual it must have a schedule
        try {
            if (!Boolean.TRUE.equals(job.getManual()) && (job.getSchedule() == null || job.getSchedule().trim().isEmpty())) {
                logger.warn("Job {} is non-manual but has no schedule", job.getTechnicalId());
                valid = false;
            }
        } catch (Exception e) {
            logger.debug("Schedule check failed: {}", e.getMessage());
            valid = false;
        }

        // transformRules must be present
        if (job.getTransformRules() == null) {
            logger.warn("Job {} missing transformRules", job.getTechnicalId());
            valid = false;
        }

        // basic sourceUrl validation
        if (job.getSourceUrl() == null || (!job.getSourceUrl().startsWith("http://") && !job.getSourceUrl().startsWith("https://"))) {
            logger.warn("Job {} has invalid sourceUrl: {}", job.getTechnicalId(), job.getSourceUrl());
            valid = false;
        }

        if (!valid) {
            job.setStatus("FAILED");
            // keep existing retry counts etc. Do not generate runId for failed validation
            return job;
        }

        // Passed validation: initialize run metadata and move to FETCHING
        job.setRunId(UUID.randomUUID().toString());
        job.setLastRunAt(Instant.now().toString());
        job.setStatus("FETCHING");
        logger.info("Job {} validation passed, runId={}", job.getTechnicalId(), job.getRunId());

        return job;
    }
}
