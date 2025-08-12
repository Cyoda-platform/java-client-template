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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FetchNobelLaureatesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchNobelLaureatesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FetchNobelLaureatesProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.getJobName() != null && !job.getJobName().isEmpty();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            // Step 3a: Update status to INGESTING and set startedAt timestamp
            job.setStatus("INGESTING");
            job.setStartedAt(Instant.now());

            // Step 3b: Fetch Nobel laureates data from OpenDataSoft API (simulate fetch here)
            // In real implementation, this would be an HTTP call using RestTemplate or WebClient
            // Simulate API response with dummy data or use an injected service

            // For demonstration, let's assume fetch succeeded
            boolean fetchSucceeded = true;
            List<Map<String, Object>> laureatesData = List.of(); // dummy empty list

            if (!fetchSucceeded) {
                job.setStatus("FAILED");
                job.setErrorMessage("Failed to fetch Nobel laureates data from API");
                job.setFinishedAt(Instant.now());
                return job;
            }

            // Step 3c: For each laureate record, create new immutable Laureate entity
            // Here we simulate calling the Laureate processing workflow
            // This is a placeholder to indicate where such logic would be implemented
            // TODO: Implement actual processing call for each laureate record

            // Step 4a: If all laureates ingested successfully
            job.setStatus("SUCCEEDED");
            job.setFinishedAt(Instant.now());

        } catch (Exception ex) {
            logger.error("Error processing Job ingestion", ex);
            job.setStatus("FAILED");
            job.setErrorMessage(ex.getMessage());
            job.setFinishedAt(Instant.now());
        }
        return job;
    }
}
