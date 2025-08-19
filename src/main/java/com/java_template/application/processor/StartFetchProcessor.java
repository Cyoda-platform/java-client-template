package com.java_template.application.processor;

import com.java_template.application.entity.reportjob.version_1.ReportJob;
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
public class StartFetchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartFetchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StartFetchProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Starting fetch for ReportJob request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ReportJob.class)
            .validate(this::isValidEntity, "Invalid ReportJob for starting fetch")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ReportJob job) {
        return job != null && job.getTechnicalId() != null && !job.getTechnicalId().isEmpty();
    }

    private ReportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ReportJob> context) {
        ReportJob job = context.entity();
        // Transition job to IN_PROGRESS per functional requirements
        try {
            // Only move to IN_PROGRESS if not already failed or completed
            if (job.getStatus() == null || job.getStatus().equalsIgnoreCase("PENDING") || job.getStatus().equalsIgnoreCase("VALIDATING")) {
                job.setStatus("IN_PROGRESS");
                if (job.getCreatedAt() == null || job.getCreatedAt().isEmpty()) {
                    // ensure createdAt remains set by caller; do not override if present
                    job.setCreatedAt(job.getCreatedAt() == null ? java.time.OffsetDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME) : job.getCreatedAt());
                }
                logger.info("ReportJob {} moved to IN_PROGRESS", job.getTechnicalId());
            } else {
                logger.info("ReportJob {} not transitioned to IN_PROGRESS (current status={})", job.getTechnicalId(), job.getStatus());
            }
        } catch (Exception e) {
            logger.error("Error transitioning ReportJob {} to IN_PROGRESS: {}", job == null ? "<null>" : job.getTechnicalId(), e.getMessage());
            // do not change status here; allow OnAggregationErrorProcessor to mark failures
        }
        // In a real implementation we would enqueue FetchBookingsProcessor with job filters
        return job;
    }
}
