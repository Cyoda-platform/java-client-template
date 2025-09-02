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
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class PaymentCreateDummyProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentCreateDummyProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @Autowired
    private EntityService entityService;

    public PaymentCreateDummyProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment creation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Payment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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
        Payment payment = context.entity();

        try {
            // Generate unique paymentId if not present
            if (payment.getPaymentId() == null || payment.getPaymentId().trim().isEmpty()) {
                payment.setPaymentId("pay-" + UUID.randomUUID().toString());
            }

            // Get input data from request payload
            String cartId = (String) context.getInputData().get("cartId");
            Double amount = (Double) context.getInputData().get("amount");

            if (cartId == null || amount == null || amount <= 0) {
                throw new IllegalArgumentException("Invalid input: cartId and positive amount are required");
            }

            payment.setCartId(cartId);
            payment.setAmount(amount);
            payment.setProvider("DUMMY");

            // Set timestamps
            Instant now = Instant.now();
            payment.setCreatedAt(now);
            payment.setUpdatedAt(now);

            // Schedule auto-payment after 3 seconds
            scheduleAutoPayment(payment.getPaymentId());

            logger.info("Created dummy payment {} for cart {} with amount ${}",
                payment.getPaymentId(), cartId, amount);

            return payment;

        } catch (Exception e) {
            logger.error("Error processing payment creation: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create payment: " + e.getMessage(), e);
        }
    }

    private void scheduleAutoPayment(String paymentId) {
        scheduler.schedule(() -> {
            try {
                logger.info("Auto-marking payment {} as paid", paymentId);
                // This would trigger the PaymentAutoMarkPaidProcessor
                // In a real implementation, this would be done through the workflow engine
                // For now, we'll just log it
                logger.info("Payment {} should be auto-marked as paid now", paymentId);
            } catch (Exception e) {
                logger.error("Error in auto-payment for {}: {}", paymentId, e.getMessage(), e);
            }
        }, 3, TimeUnit.SECONDS);
    }
}