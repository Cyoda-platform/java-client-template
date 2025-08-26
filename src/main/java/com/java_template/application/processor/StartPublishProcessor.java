package com.java_template.application.processor;

import com.java_template.application.entity.monthlyreport.version_1.MonthlyReport;
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
public class StartPublishProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartPublishProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StartPublishProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing MonthlyReport for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(MonthlyReport.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(MonthlyReport entity) {
        return entity != null && entity.isValid();
    }

    private MonthlyReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<MonthlyReport> context) {
        MonthlyReport entity = context.entity();

        // Ensure we have an entity to operate on
        if (entity == null) {
            logger.warn("MonthlyReport entity is null in execution context");
            return null;
        }

        logger.info("Start publish logic for MonthlyReport month={}, currentStatus={}, deliveryAttempts={}",
                entity.getMonth(), entity.getPublishedStatus(), entity.getDeliveryAttempts());

        // If there are no admin recipients configured, mark as FAILED and abort publish attempt
        if (entity.getAdminRecipients() == null || entity.getAdminRecipients().isEmpty()) {
            logger.error("No admin recipients configured for MonthlyReport month={}. Marking as FAILED.", entity.getMonth());
            entity.setPublishedStatus("FAILED");
            // Ensure deliveryAttempts is initialized when failing
            if (entity.getDeliveryAttempts() == null) {
                entity.setDeliveryAttempts(0);
            }
            return entity;
        }

        // Increment delivery attempts for this publish start
        Integer attempts = entity.getDeliveryAttempts();
        if (attempts == null) {
            entity.setDeliveryAttempts(1);
        } else {
            entity.setDeliveryAttempts(attempts + 1);
        }

        // Transition report into publishing state
        entity.setPublishedStatus("PUBLISHING");

        logger.info("MonthlyReport month={} transitioned to PUBLISHING (attempts={})",
                entity.getMonth(), entity.getDeliveryAttempts());

        return entity;
    }
}