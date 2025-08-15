package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.PaymentRecord;
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

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PaymentCaptureProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentCaptureProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PaymentCaptureProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing payment capture for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid order entity for payment capture")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order order) {
        return order != null && order.getId() != null;
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        try {
            String status = order.getPaymentStatus();
            if (status == null) {
                logger.warn("Order {} has no paymentStatus set. Cannot capture.", order.getId());
                order.setPaymentStatus("FAILED");
                return order;
            }
            if ("AUTHORIZED".equalsIgnoreCase(status)) {
                // Simulate capture
                PaymentRecord record = new PaymentRecord();
                record.setId(UUID.randomUUID());
                record.setOrderId(order.getId());
                record.setProvider("SIMULATED");
                BigDecimal amount = order.getTotal() == null ? BigDecimal.ZERO : order.getTotal();
                record.setAmount(amount);
                record.setCurrency(order.getCurrency());
                record.setStatus("CAPTURED");
                record.setTransactionId("cap-" + UUID.randomUUID().toString());

                order.setPaymentStatus("CAPTURED");
                order.setStatus("PAID");
                logger.info("Captured payment for order {} amount={}", order.getId(), amount);
            } else {
                logger.warn("Order {} paymentStatus is {}. Capture skipped.", order.getId(), status);
            }
        } catch (Exception e) {
            logger.error("Error during payment capture for order {}: {}", order != null ? order.getId() : "<null>", e.getMessage());
            if (order != null) {
                order.setPaymentStatus("FAILED");
            }
        }
        return order;
    }
}
