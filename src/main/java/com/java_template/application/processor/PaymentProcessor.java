package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.application.payment.PaymentGatewayClient;
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
import java.time.Instant;
import java.util.UUID;

@Component
public class PaymentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    // Note: In a full implementation PaymentGatewayClient would be an external integration. Here we simulate it.
    private final PaymentGatewayClient paymentGatewayClient = new PaymentGatewayClient();

    public PaymentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid cart for payment")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart cart) {
        return cart != null && cart.getId() != null && cart.getReservations() != null && !cart.getReservations().isEmpty();
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();

        // Calculate total amount if missing
        if (cart.getTotalAmount() == null) {
            cart.setTotalAmount(BigDecimal.ZERO);
        }

        // For the demo, call into a mocked payment gateway client
        String idempotencyKey = cart.getCheckoutAttemptId() != null ? cart.getCheckoutAttemptId() : UUID.randomUUID().toString();
        boolean success = paymentGatewayClient.charge(idempotencyKey, cart.getTotalAmount());

        if (success) {
            cart.setStatus("CHECKED_OUT");
            cart.setUpdatedAt(Instant.now().toString());
            // mark reservations consumed
            if (cart.getReservations() != null) {
                cart.getReservations().forEach(r -> r.setStatus("CONSUMED"));
            }
            logger.info("Payment succeeded for cart {}", cart.getId());
        } else {
            cart.setStatus("OPEN");
            cart.setUpdatedAt(Instant.now().toString());
            // release reservations
            if (cart.getReservations() != null) {
                cart.getReservations().forEach(r -> r.setStatus("RELEASED"));
            }
            logger.warn("Payment failed for cart {}", cart.getId());
        }

        return cart;
    }
}
