package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
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

import java.util.List;

@Component
public class LaureateIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public LaureateIngestionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Ingesting laureates for Job: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidJob, "Invalid Job entity state for ingestion")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidJob(Job job) {
        return job != null && job.getJobName() != null && !job.getJobName().isEmpty();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            // Update status to INGESTING and set startedAt timestamp
            job.setStatus("INGESTING");
            job.setStartedAt(java.time.Instant.now().toString());
            logger.info("Job status updated to INGESTING: {}", job.getJobName());

            // Simulate fetching laureates from external API
            // TODO: Replace with actual HTTP call and JSON parsing
            List<Laureate> laureates = fetchLaureatesFromApi();

            // Save each laureate (this triggers Laureate workflow)
            for (Laureate laureate : laureates) {
                // TODO: Implement saving of laureate entity
                logger.info("Saving Laureate: {} {}", laureate.getFirstname(), laureate.getSurname());
            }

        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setFinishedAt(java.time.Instant.now().toString());
            logger.error("Error ingesting laureates for Job {}: {}", job.getJobName(), e.getMessage());
        }
        return job;
    }

    private List<Laureate> fetchLaureatesFromApi() {
        // TODO: Implement API call to fetch laureates data from OpenDataSoft
        // Return dummy list for now
        return java.util.Collections.emptyList();
    }
}
