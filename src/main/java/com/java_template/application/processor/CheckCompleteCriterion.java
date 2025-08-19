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
public class CheckCompleteCriterion implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CheckCompleteCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CheckCompleteCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CheckCompleteCriterion for request: {}", request.getId());

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
            Map<String, Integer> summary = job.getResultSummary();
            if (summary == null) {
                logger.debug("Job {} has no resultSummary; nothing to evaluate", job.getId());
                return job;
            }

            int failed = summary.getOrDefault("failed", 0);
            int processed = summary.getOrDefault("processed", 0);

            if (failed > 0) {
                job.setStatus("FAILED");
                logger.info("Job {} marked FAILED by CheckCompleteCriterion (failed={})", job.getId(), failed);
            } else if (processed > 0) {
                job.setStatus("COMPLETED");
                logger.info("Job {} marked COMPLETED by CheckCompleteCriterion (processed={})", job.getId(), processed);
            } else {
                logger.debug("Job {} has no processed items and no failures; leaving status as {}", job.getId(), job.getStatus());
            }
        } catch (Exception ex) {
            logger.error("Error while evaluating completion for Job {}: {}", job.getId(), ex.getMessage(), ex);
        }
        return job;
    }
}
