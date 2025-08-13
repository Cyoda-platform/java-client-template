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

@Component
public class IngestionResultProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IngestionResultProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public IngestionResultProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job ingestion result for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job state for ingestion result processing")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        // Validate job entity state before processing ingestion result
        return entity != null && entity.getStatus() != null;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job entity = context.entity();
        // Implement logic to handle ingestion outcome
        // Example: update completedAt timestamp if ingestion finished
        if ("succeeded".equalsIgnoreCase(entity.getStatus()) || "failed".equalsIgnoreCase(entity.getStatus())) {
            if (entity.getCompletedAt() == null) {
                // Set completedAt to current timestamp (assuming setter available)
                // No setter in spec, so possibly log or handle accordingly
                logger.info("Job {} ingestion completed at timestamp needs to be set", entity.getJobName());
            }
        }
        return entity;
    }
}
