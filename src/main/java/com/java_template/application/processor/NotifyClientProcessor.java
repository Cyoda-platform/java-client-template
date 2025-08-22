package com.java_template.application.processor;

import com.java_template.application.entity.ingestjob.version_1.IngestJob;
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
public class NotifyClientProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyClientProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NotifyClientProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing IngestJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(IngestJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestJob entity) {
        return entity != null && entity.isValid();
    }

    private IngestJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestJob> context) {
        IngestJob entity = context.entity();
        try {
            logger.info("NotifyClientProcessor started for job technicalId={}", entity.getTechnicalId());

            String clientId = entity.getClientId();
            String storedItemId = entity.getStoredItemTechnicalId();

            if (clientId == null || clientId.isBlank()) {
                logger.info("No clientId present for job {}. No notification will be sent.", entity.getTechnicalId());
                // No client to notify; leave job state as-is.
                return entity;
            }

            // Ensure we have a stored item id to notify about
            if (storedItemId == null || storedItemId.isBlank()) {
                String msg = "Missing storedItemTechnicalId: unable to notify client " + clientId;
                logger.error(msg + " for job {}", entity.getTechnicalId());
                // Mark job as failed to reflect notification failure
                entity.setStatus("FAILED");
                entity.setErrorMessage(msg);
                return entity;
            }

            // Simulate notification (actual external notification systems are out of scope)
            logger.info("Notifying client '{}' about stored item '{}'", clientId, storedItemId);

            // Mark job as NOTIFIED to indicate completion of notification step
            entity.setStatus("NOTIFIED");
            entity.setErrorMessage(null);

            return entity;
        } catch (Exception ex) {
            logger.error("Exception while notifying client for job {}: {}", entity.getTechnicalId(), ex.getMessage(), ex);
            entity.setStatus("FAILED");
            entity.setErrorMessage("Notification error: " + ex.getMessage());
            return entity;
        }
    }
}