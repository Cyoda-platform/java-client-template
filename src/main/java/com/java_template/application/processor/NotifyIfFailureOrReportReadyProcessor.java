package com.java_template.application.processor;

import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class NotifyIfFailureOrReportReadyProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyIfFailureOrReportReadyProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public NotifyIfFailureOrReportReadyProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing NotifyIfFailureOrReportReady for request: {}", request.getId());

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
            if (job.getFailureReason() != null) {
                // simulate notifying admins
                logger.warn("Job {} failed: notifying admins. Reason: {}", job.getJobId(), job.getFailureReason());
            } else {
                logger.info("Job {} completed successfully. Triggering report generation if configured.", job.getJobId());
                // In production, create Report entity via entityService.addItem if admin emails configured
            }
        } catch (Exception ex) {
            logger.error("Error in NotifyIfFailureOrReportReadyProcessor", ex);
            job.setFailureReason("notify error: " + ex.getMessage());
        }

        return job;
    }
}
