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

@Component
public class ValidateJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

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
        Job entity = context.entity();

        if (entity == null) {
            logger.warn("Received null Job entity in execution context");
            return null;
        }

        // Basic validation rules from functional requirements:
        // - If sourceEndpoint is empty or scheduleSpec is invalid -> mark FAILED
        // - Otherwise mark as VALIDATED
        String sourceEndpoint = entity.getSourceEndpoint();
        String scheduleSpec = entity.getScheduleSpec();

        boolean missingSource = (sourceEndpoint == null || sourceEndpoint.isBlank());
        boolean missingSchedule = (scheduleSpec == null || scheduleSpec.isBlank());

        if (missingSource || missingSchedule) {
            entity.setStatus("FAILED");
            StringBuilder summary = new StringBuilder("Validation failed:");
            if (missingSource) summary.append(" missing sourceEndpoint");
            if (missingSchedule) {
                if (missingSource) summary.append(";");
                summary.append(" missing or invalid scheduleSpec");
            }
            entity.setLastResultSummary(summary.toString());
            logger.warn("Job validation failed for id={} summary={}", entity.getId(), entity.getLastResultSummary());
        } else {
            entity.setStatus("VALIDATED");
            entity.setLastResultSummary("Validation passed");
            logger.info("Job validated successfully for id={}", entity.getId());
        }

        return entity;
    }
}