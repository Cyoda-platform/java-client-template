package com.java_template.application.processor;

import com.java_template.application.entity.ingestjob.version_1.IngestJob;
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

        // Only notify when job reached COMPLETED state. If client id is present, simulate notification.
        String currentStatus = entity.getStatus();
        if (currentStatus != null && "COMPLETED".equalsIgnoreCase(currentStatus)) {
            String clientId = entity.getClientId();
            String storedItemId = entity.getStoredItemTechnicalId();

            if (clientId != null && !clientId.isBlank()) {
                if (storedItemId != null && !storedItemId.isBlank()) {
                    // In a real implementation this would enqueue/send a notification to the client.
                    logger.info("NotifyClientProcessor: Notifying client '{}' about stored item '{}'", clientId, storedItemId);
                } else {
                    logger.warn("NotifyClientProcessor: Job '{}' has no storedItemTechnicalId to notify client '{}'", entity.getTechnicalId(), clientId);
                }
            } else {
                logger.info("NotifyClientProcessor: No clientId present for job '{}', skipping external notification", entity.getTechnicalId());
            }

            // Transition job to NOTIFIED state so clients can see it was handled.
            entity.setStatus("NOTIFIED");
        } else {
            logger.info("NotifyClientProcessor: Job '{}' not in COMPLETED state (current='{}'), skipping notify step", entity.getTechnicalId(), currentStatus);
        }

        return entity;
    }
}