package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.trade.version_1.Trade;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Processor for executing orders and creating trades
 * Handles order fills and creates corresponding trade records
 */
@Component
public class OrderExecutionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderExecutionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderExecutionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing OrderExecution for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order wrapper")
                .map(this::executeOrder)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order entity = entityWithMetadata.entity();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) &&
               entityWithMetadata.metadata().getId() != null;
    }

    private EntityWithMetadata<Order> executeOrder(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Executing order: {} for {} shares", order.getOrderId(), order.getQuantity());

        // Simulate execution price (in production, this comes from exchange)
        Double executionPrice = order.getLimitPrice() != null ? order.getLimitPrice() : 100.0;
        
        // Update order with execution details
        order.setQuantityFilled(order.getQuantity());
        order.setAverageExecutionPrice(executionPrice);
        order.setUpdatedAt(LocalDateTime.now());

        // Create trade record
        Trade trade = createTradeFromOrder(order, executionPrice);
        
        try {
            // Save trade to database
            EntityWithMetadata<Trade> tradeResponse = entityService.create(trade);
            logger.info("Trade created: {} for order: {}", tradeResponse.metadata().getId(), order.getOrderId());
        } catch (Exception e) {
            logger.error("Failed to create trade for order: {}", order.getOrderId(), e);
            throw new RuntimeException("Trade creation failed", e);
        }

        logger.info("Order {} executed successfully", order.getOrderId());
        return entityWithMetadata;
    }

    private Trade createTradeFromOrder(Order order, Double executionPrice) {
        Trade trade = new Trade();
        trade.setTradeId("TRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        trade.setOrderId(order.getOrderId());
        trade.setPortfolioId(order.getPortfolioId());
        trade.setInstrumentSymbol(order.getInstrumentSymbol());
        trade.setSide(order.getSide());
        trade.setQuantity(order.getQuantity());
        trade.setExecutionPrice(executionPrice);
        trade.setTradeValue(order.getQuantity() * executionPrice);
        trade.setCommission(trade.getTradeValue() * 0.001); // 0.1% commission
        trade.setNetValue(trade.getTradeValue() - trade.getCommission());
        trade.setExecutionTime(LocalDateTime.now());
        trade.setSettlementDate(LocalDateTime.now().plusDays(2)); // T+2 settlement
        trade.setCounterparty(order.getRoutingDestination());
        trade.setExternalTradeId("EXT-TRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        trade.setSettlementStatus("PENDING");
        trade.setCreatedAt(LocalDateTime.now());
        trade.setUpdatedAt(LocalDateTime.now());
        return trade;
    }
}

