package com.java_template.application.processor;
import com.java_template.application.entity.importjob.version_1.ImportJob;
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
public class NotifyRequesterProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyRequesterProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NotifyRequesterProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing NotifyRequester for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportJob entity) {
        return entity != null && !Boolean.TRUE.equals(entity.getNotificationSent());
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();
        if (job == null) return null;
        try {
            // Simulate notification send. In real implementation send email/webhook.
            logger.info("Sending notification to requester {} for job {}: status={}, importedCount={}, errorMessage={}",
                job.getRequestedBy(), job.getTechnicalId(), job.getStatus(), job.getImportedCount(), job.getErrorMessage());
            job.setNotificationSent(true);
            job.setUpdatedAt(Instant.now().toString());
        } catch (Exception e) {
            logger.error("Error notifying requester for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
            job.setErrorMessage((job.getErrorMessage() == null ? "" : job.getErrorMessage() + "\n") + e.getMessage());
            job.setUpdatedAt(Instant.now().toString());
        }
        return job;
    }
}
