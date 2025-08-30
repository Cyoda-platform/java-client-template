package com.java_template.application.processor;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Component
public class AutoMarkPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AutoMarkPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AutoMarkPaidProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Payment.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Payment entity) {
        return entity != null && entity.isValid();
    }

    private Payment processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment entity = context.entity();

        // Business logic: wait ~3 seconds and auto-mark INITIATED payments to PAID if still in INITIATED
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            logger.warn("Interrupted while waiting to auto-mark payment: {}", entity != null ? entity.getPaymentId() : "unknown");
            Thread.currentThread().interrupt();
            return entity;
        }

        if (entity != null) {
            String status = entity.getStatus();
            if (status != null && status.equalsIgnoreCase("INITIATED")) {
                logger.info("Auto-marking payment {} as PAID", entity.getPaymentId());
                entity.setStatus("PAID");
                entity.setUpdatedAt(Instant.now().toString());
            } else {
                logger.info("Payment {} not in INITIATED state (current: {}), skipping auto-mark", entity.getPaymentId(), status);
            }
        } else {
            logger.warn("Received null payment entity in AutoMarkPaidProcessor");
        }

        return entity;
    }
}