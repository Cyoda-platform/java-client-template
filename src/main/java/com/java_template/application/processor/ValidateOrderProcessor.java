package com.java_template.application.processor;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class ValidateOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateOrderProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order entity) {
        return entity != null; // basic null check; deeper validation happens in processEntityLogic
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        if (order == null) return null;

        logger.info("ValidateOrderProcessor: current status={}", order.getStatus());

        // Idempotent: if already in a terminal or validated state, do nothing
        String status = order.getStatus();
        if ("VALIDATED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
            logger.info("Order already in terminal/validated state: {}", status);
            return order;
        }

        // Mark validating
        try {
            order.setStatus("VALIDATING");
        } catch (Exception e) {
            logger.warn("Unable to set status to VALIDATING: {}", e.getMessage());
        }

        boolean valid = true;

        // items not empty
        try {
            List<?> items = order.getItems();
            if (items == null || items.isEmpty()) {
                logger.warn("Order {} has no items", order.getOrderId());
                valid = false;
            }
        } catch (Exception e) {
            logger.warn("Unable to inspect items: {}", e.getMessage());
            valid = false;
        }

        // totals match
        try {
            BigDecimal total = order.getTotalAmount() == null ? null : new BigDecimal(order.getTotalAmount().toString());
            BigDecimal computed = BigDecimal.ZERO;
            List<?> items = order.getItems();
            if (items != null) {
                for (Object o : items) {
                    if (o instanceof Map) {
                        Map<?,?> m = (Map<?,?>) o;
                        Object q = m.get("quantity");
                        Object p = m.get("price");
                        BigDecimal qty = q == null ? BigDecimal.ZERO : new BigDecimal(q.toString());
                        BigDecimal price = p == null ? BigDecimal.ZERO : new BigDecimal(p.toString());
                        computed = computed.add(price.multiply(qty));
                    }
                }
            }
            if (total == null || computed.compareTo(total) != 0) {
                logger.warn("Order total mismatch. expected={} computed={}", total, computed);
                valid = false;
            }
        } catch (Exception e) {
            logger.warn("Unable to verify totals: {}", e.getMessage());
            valid = false;
        }

        // shipping address basic validation
        try {
            Object shippingAddress = order.getShippingAddress();
            if (shippingAddress == null) {
                logger.warn("Order {} missing shipping address", order.getOrderId());
                valid = false;
            } else if (shippingAddress instanceof Map) {
                Map<?,?> addr = (Map<?,?>) shippingAddress;
                if (addr.get("line1") == null || addr.get("city") == null || addr.get("postalCode") == null || addr.get("country") == null) {
                    logger.warn("Order {} has incomplete shipping address", order.getOrderId());
                    valid = false;
                }
            }
        } catch (Exception e) {
            logger.warn("Unable to inspect shipping address: {}", e.getMessage());
            valid = false;
        }

        if (!valid) {
            logger.info("Validation failed for order {}. Marking CANCELLED.", order.getOrderId());
            order.setStatus("CANCELLED");
            // Add reason metadata if possible
            try {
                Object meta = order.getMetadata();
                if (meta == null || !(meta instanceof java.util.Map)) {
                    java.util.Map<String,Object> mm = new java.util.HashMap<>();
                    mm.put("validationFailure", "basic-validation-failed");
                    order.setMetadata(mm);
                } else {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String,Object> mm = (java.util.Map<String,Object>) meta;
                    mm.put("validationFailure", "basic-validation-failed");
                    order.setMetadata(mm);
                }
            } catch (Exception ex) {
                // ignore metadata set errors
            }
        } else {
            logger.info("Order {} validated successfully", order.getOrderId());
            order.setStatus("VALIDATED");
        }

        return order;
    }
}
