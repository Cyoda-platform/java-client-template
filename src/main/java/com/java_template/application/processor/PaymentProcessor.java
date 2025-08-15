package com.java_template.application.processor;

import com.java_template.application.entity.payment.version_1.PaymentRecord;
import com.java_template.application.entity.order.version_1.Order;
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
public class PaymentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PaymentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing payment for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid order entity for payment processing")
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
            // Simplified payment simulation: create a PaymentRecord and mark order paymentStatus
            PaymentRecord record = new PaymentRecord();
            record.setId(UUID.randomUUID());
            record.setOrderId(order.getId());
            record.setProvider("SIMULATED");
            BigDecimal amount = order.getTotal() == null ? BigDecimal.ZERO : order.getTotal();
            record.setAmount(amount);
            record.setCurrency(order.getCurrency());
            record.setStatus("CAPTURED");
            record.setTransactionId("sim-" + UUID.randomUUID().toString());

            // Attach payment info onto order if fields exist
            order.setPaymentStatus("CAPTURED");
            // In a real implementation we'd persist PaymentRecord. For now we log it.
            logger.info("Simulated payment captured for order {} amount={}", order.getId(), amount);

        } catch (Exception e) {
            logger.error("Error during payment processing for order {}: {}", order != null ? order.getId() : "<null>", e.getMessage());
            // mark as failed
            if (order != null) {
                order.setPaymentStatus("FAILED");
            }
        }
        return order;
    }
}
