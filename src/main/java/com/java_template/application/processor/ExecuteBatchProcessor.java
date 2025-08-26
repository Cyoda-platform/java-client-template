package com.java_template.application.processor;

import com.java_template.application.entity.batchjob.version_1.BatchJob;
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

@Component
public class ExecuteBatchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteBatchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ExecuteBatchProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing BatchJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(BatchJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(BatchJob entity) {
        return entity != null && entity.isValid();
    }

    private BatchJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<BatchJob> context) {
        BatchJob entity = context.entity();

        // Basic state machine implementation following functional requirements.
        // Use only existing getters/setters on BatchJob.

        String status = entity.getStatus();
        if (status == null) {
            // Defensive: treat null as PENDING
            status = "PENDING";
            entity.setStatus(status);
        }

        switch (status.toUpperCase()) {
            case "PENDING":
                // Validate job parameters: scheduleCron non-empty and runMonth format YYYY-MM
                StringBuilder validationErrors = new StringBuilder();
                if (entity.getScheduleCron() == null || entity.getScheduleCron().isBlank()) {
                    validationErrors.append("scheduleCron is required. ");
                }
                if (entity.getRunMonth() == null || !entity.getRunMonth().matches("^\\d{4}-\\d{2}$")) {
                    validationErrors.append("runMonth must be in format YYYY-MM. ");
                }
                if (validationErrors.length() > 0) {
                    entity.setStatus("FAILED");
                    entity.setSummary(validationErrors.toString().trim());
                    entity.setFinishedAt(Instant.now().toString());
                    logger.warn("BatchJob validation failed: {}", entity.getSummary());
                } else {
                    entity.setStatus("VALIDATING");
                    entity.setSummary("Validation passed");
                    logger.info("BatchJob validated, moving to VALIDATING");
                }
                break;

            case "VALIDATING":
                // Job is ready to start running
                entity.setStartedAt(Instant.now().toString());
                entity.setStatus("RUNNING");
                entity.setSummary("Job started");
                logger.info("BatchJob moved to RUNNING at {}", entity.getStartedAt());
                break;

            case "RUNNING":
                // Simulate execution completion step here. Actual ingestion and user creation
                // would be performed by dedicated processors; we update the job state to indicate progress.
                entity.setStatus("GENERATING_REPORT");
                entity.setSummary((entity.getSummary() == null ? "" : entity.getSummary() + "; ") + "Ingestion completed, generating report");
                logger.info("BatchJob ingestion completed, moving to GENERATING_REPORT");
                break;

            case "GENERATING_REPORT":
                // Mark report generation complete and finish job
                entity.setStatus("COMPLETED");
                entity.setFinishedAt(Instant.now().toString());
                entity.setSummary((entity.getSummary() == null ? "" : entity.getSummary() + "; ") + "Report generated");
                logger.info("BatchJob report generated, job COMPLETED at {}", entity.getFinishedAt());
                break;

            case "COMPLETED":
            case "FAILED":
                // No-op for terminal states
                logger.info("BatchJob in terminal state: {}", entity.getStatus());
                break;

            default:
                // Unknown status - mark as FAILED
                entity.setStatus("FAILED");
                entity.setSummary("Unknown status: " + status);
                entity.setFinishedAt(Instant.now().toString());
                logger.warn("BatchJob had unknown status '{}', marking as FAILED", status);
                break;
        }

        return entity;
    }
}