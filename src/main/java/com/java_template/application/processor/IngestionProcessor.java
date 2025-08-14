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
public class IngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public IngestionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job ingestion for request: {}", request.getId());

        // TODO: Implement ingestion logic to fetch data from API and trigger laureate creation
        // This example is a placeholder for actual ingestion logic

        return serializer.withRequest(request)
                .toEntity(Job.class)
                .validate(this::isValidEntity, "Invalid job entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.getJobName() != null && !entity.getJobName().isEmpty();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job entity = context.entity();
        // Implement the ingestion logic here
        // For example, transition status to INGESTING and start data fetch
        // The actual API call and data processing should be asynchronous and managed by the workflow engine
        logger.info("Starting ingestion for job: {}", entity.getJobName());
        // Placeholder for actual ingestion logic
        return entity;
    }
}
