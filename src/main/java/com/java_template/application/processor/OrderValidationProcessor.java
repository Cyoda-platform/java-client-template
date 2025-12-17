package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * OrderValidationProcessor - Validates order before placement
 * Checks balance, limits, and market conditions
 */
@Component
public class OrderValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderValidationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Validating order for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        return order != null && order.isValid(entityWithMetadata.metadata());
    }

    private EntityWithMetadata<Order> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Validating order: {} for account: {}", order.getOrderId(), order.getAccountId());

        // Validate order quantity
        if (order.getQuantity() == null || order.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            logger.error("Invalid order quantity: {}", order.getQuantity());
            throw new IllegalArgumentException("Order quantity must be greater than zero");
        }

        // Validate price for limit orders
        if ("LIMIT".equals(order.getType())) {
            if (order.getPrice() == null || order.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                logger.error("Invalid limit price: {}", order.getPrice());
                throw new IllegalArgumentException("Limit price must be greater than zero");
            }
        }

        // Validate side
        if (!"BUY".equals(order.getSide()) && !"SELL".equals(order.getSide())) {
            logger.error("Invalid order side: {}", order.getSide());
            throw new IllegalArgumentException("Order side must be BUY or SELL");
        }

        // Initialize filled and remaining
        order.setFilled(BigDecimal.ZERO);
        order.setRemaining(order.getQuantity());

        logger.info("Order {} validated successfully", order.getOrderId());
        return entityWithMetadata;
    }
}

