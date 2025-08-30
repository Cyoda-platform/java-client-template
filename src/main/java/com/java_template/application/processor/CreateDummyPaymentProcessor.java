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
import java.util.UUID;

@Component
public class CreateDummyPaymentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateDummyPaymentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CreateDummyPaymentProcessor(SerializerFactory serializerFactory) {
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
        // For creation we require at least a cartId and a non-negative amount.
        if (entity == null) return false;
        if (entity.getCartId() == null || entity.getCartId().isBlank()) return false;
        if (entity.getAmount() == null) return false;
        if (entity.getAmount() < 0) return false;
        return true;
    }

    private Payment processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment entity = context.entity();
        // Ensure provider is set to DUMMY
        if (entity.getProvider() == null || entity.getProvider().isBlank()) {
            entity.setProvider("DUMMY");
        } else {
            entity.setProvider(entity.getProvider().toUpperCase());
        }

        // Ensure status is INITIATED for a newly created dummy payment
        entity.setStatus("INITIATED");

        // Ensure paymentId exists
        if (entity.getPaymentId() == null || entity.getPaymentId().isBlank()) {
            entity.setPaymentId(UUID.randomUUID().toString());
        }

        // Ensure timestamps
        String now = Instant.now().toString();
        if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);

        // Amount already validated in isValidEntity; keep as-is.
        // Do NOT perform any add/update/delete operations on the triggering entity here.
        // The AutoMarkPaidProcessor (separate) is responsible for flipping status to PAID after ~3s.

        logger.info("Created dummy payment [{}] for cart [{}], amount={}", entity.getPaymentId(), entity.getCartId(), entity.getAmount());
        return entity;
    }
}