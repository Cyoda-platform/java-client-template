package com.java_template.application.processor;

import com.java_template.application.entity.SnapshotJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SnapshotJobFailureProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SnapshotJobFailureProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SnapshotJob failure for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(SnapshotJob.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::setFailureReason)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(SnapshotJob entity) {
        return entity != null;
    }

    private SnapshotJob setFailureReason(ProcessorSerializer.ProcessorEntityExecutionContext<SnapshotJob> context) {
        SnapshotJob job = context.entity();
        String technicalId = context.request().getEntityId();

        logger.info("Setting failure reason for SnapshotJob id: {}", technicalId);

        // Determine the specific failure reason based on validation
        String failureReason = determineFailureReason(job);

        job.setStatus("FAILED");
        job.setFailReason(failureReason);

        logger.info("SnapshotJob marked as FAILED with reason: {}", failureReason);
        return job;
    }

    private String determineFailureReason(SnapshotJob job) {
        // Check for various failure conditions and return appropriate reason
        if (job.getSeason() == null || !job.getSeason().matches("\\d{4}")) {
            return "Invalid season format: season must be a 4-digit year";
        }

        if (job.getDateRangeStart() == null || job.getDateRangeStart().isBlank()) {
            return "Missing dateRangeStart: dateRangeStart is required";
        }

        if (job.getDateRangeEnd() == null || job.getDateRangeEnd().isBlank()) {
            return "Missing dateRangeEnd: dateRangeEnd is required";
        }

        try {
            if (job.getDateRangeStart().compareTo(job.getDateRangeEnd()) >= 0) {
                return "Invalid date range: dateRangeStart (" + job.getDateRangeStart() + ") must be before dateRangeEnd (" + job.getDateRangeEnd() + ")";
            }
        } catch (Exception e) {
            return "Invalid date format: unable to parse dateRangeStart or dateRangeEnd";
        }

        // Default failure reason if no specific condition is met
        return "SnapshotJob validation failed: unknown reason";
    }
}
