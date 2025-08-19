package com.java_template.application.processor;

import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class OnAggregationErrorProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OnAggregationErrorProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OnAggregationErrorProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("OnAggregationErrorProcessor invoked for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ReportJob.class)
            .validate(this::isValidEntity, "Invalid ReportJob for error handling")
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
        try {
            // Mark job as failed and set completedAt
            job.setStatus("FAILED");
            job.setCompletedAt(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            // Attach a generic error detail if none present
            if (job.getErrorDetails() == null || job.getErrorDetails().isEmpty()) {
                job.setErrorDetails("Aggregation failed due to an internal error. Check logs for details.");
            }

            logger.error("ReportJob {} marked as FAILED: {}", job.getTechnicalId(), job.getErrorDetails());

            // Optionally notify or create alerts - omitted here. Could interact with EntityService or notification systems.

        } catch (Exception e) {
            logger.error("Error handling aggregation failure for job {}: {}", job == null ? "<null>" : job.getTechnicalId(), e.getMessage());
        }
        return job;
    }
}
