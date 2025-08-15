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
public class PaymentInitiationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentInitiationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PaymentInitiationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing payment initiation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid order entity for payment initiation")
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
            BigDecimal amount = order.getTotal() == null ? BigDecimal.ZERO : order.getTotal();
            PaymentRecord record = new PaymentRecord();
            record.setId(UUID.randomUUID());
            record.setOrderId(order.getId());
            record.setProvider("SIMULATED");
            record.setAmount(amount);
            record.setCurrency(order.getCurrency());

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                record.setStatus("FAILED");
                order.setPaymentStatus("FAILED");
                logger.warn("Payment initiation failed for order {} due to zero amount", order.getId());
            } else {
                // Simulate authorization success
                record.setStatus("AUTHORIZED");
                order.setPaymentStatus("AUTHORIZED");
                record.setTransactionId("auth-" + UUID.randomUUID().toString());
                logger.info("Payment authorized for order {} amount={}", order.getId(), amount);
            }
            // Real implementation would persist PaymentRecord. Here we only log it.
        } catch (Exception e) {
            logger.error("Error during payment initiation for order {}: {}", order != null ? order.getId() : "<null>", e.getMessage());
            if (order != null) {
                order.setPaymentStatus("FAILED");
            }
        }
        return order;
    }
}
