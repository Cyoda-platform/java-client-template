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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class IngestNobelLaureatesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IngestNobelLaureatesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public IngestNobelLaureatesProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.getJobName() != null && entity.getStatus() != null;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // Business logic for ingestion process
        logger.info("Starting ingestion process for job: {}", job.getJobName());

        try {
            // Simulate setting status to INGESTING
            job.setStatus("INGESTING");
            logger.info("Job status set to INGESTING");

            // TODO: Fetch Nobel laureate data from OpenDataSoft API
            // For demonstration, simulate fetching data
            List<Object> laureateDataList = new ArrayList<>(); // Placeholder for fetched laureate data

            // TODO: For each laureate record, save a new Laureate entity
            // This would involve integration with persistence layer
            // For now, log the action
            logger.info("Simulating ingesting laureate records: {} records", laureateDataList.size());

            // Simulate setting status to SUCCEEDED
            job.setStatus("SUCCEEDED");
            logger.info("Job ingestion succeeded");
        } catch (Exception e) {
            logger.error("Error during ingestion process", e);
            job.setStatus("FAILED");
        }

        return job;
    }
}
