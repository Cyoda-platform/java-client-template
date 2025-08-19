package com.java_template.application.processor;

import com.java_template.application.entity.inventoryreportjob.version_1.InventoryReportJob;
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
public class NotifyUsersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyUsersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NotifyUsersProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing NotifyUsers for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(InventoryReportJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(InventoryReportJob entity) {
        return entity != null && entity.isValid();
    }

    private InventoryReportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<InventoryReportJob> context) {
        InventoryReportJob job = context.entity();
        try {
            // In a real system we would enqueue a notification. Here we mark status to NOTIFYING.
            if (job.getRequestedBy() != null && !job.getRequestedBy().trim().isEmpty()) {
                logger.info("Would notify user {} about report {}", job.getRequestedBy(), job.getReportRef());
            }
            job.setStatus("NOTIFYING");
        } catch (Exception e) {
            logger.error("Error notifying users for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
            // Notification failures should not mark report as failed per requirements
            job.setStatus("NOTIFYING");
        }
        return job;
    }
}
