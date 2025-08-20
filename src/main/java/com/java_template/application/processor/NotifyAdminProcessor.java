package com.java_template.application.processor;

import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
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
public class NotifyAdminProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyAdminProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NotifyAdminProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing NotifyAdmin for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(IngestionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestionJob entity) {
        return entity != null;
    }

    private IngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestionJob> context) {
        IngestionJob job = context.entity();
        try {
            job.setLastProcessedAt(Instant.now());
            if ("FAILED".equals(job.getStatus()) || (job.getErrorCount() != null && job.getFetchedCount() != null && job.getErrorCount() > job.getFetchedCount() * 0.05)) {
                // For prototype: just log notification creation
                logger.warn("Notify admins: job {} finished with status {} and summary {}", job.getTechnicalId(), job.getStatus(), job.getErrorSummary());
            } else {
                logger.info("Job {} completed successfully; no admin notification required", job.getTechnicalId());
            }
            return job;
        } catch (Exception ex) {
            logger.error("Unexpected error in NotifyAdminProcessor for {}: {}", job == null ? "?" : job.getTechnicalId(), ex.getMessage(), ex);
            return job;
        }
    }
}
