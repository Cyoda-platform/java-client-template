package com.java_template.application.processor;

import com.java_template.application.entity.trade.version_1.Trade;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Processor for confirming trades
 * Validates trade details and prepares for settlement
 */
@Component
public class TradeConfirmationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TradeConfirmationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public TradeConfirmationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing TradeConfirmation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Trade.class)
                .validate(this::isValidEntityWithMetadata, "Invalid trade wrapper")
                .map(this::confirmTrade)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Trade> entityWithMetadata) {
        Trade entity = entityWithMetadata.entity();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) &&
               entityWithMetadata.metadata().getId() != null;
    }

    private EntityWithMetadata<Trade> confirmTrade(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Trade> context) {

        EntityWithMetadata<Trade> entityWithMetadata = context.entityResponse();
        Trade trade = entityWithMetadata.entity();

        logger.debug("Confirming trade: {} for {} shares at {}", 
                   trade.getTradeId(), trade.getQuantity(), trade.getExecutionPrice());

        // Validate trade details
        if (trade.getQuantity() == null || trade.getQuantity() <= 0) {
            logger.error("Invalid trade quantity: {}", trade.getQuantity());
            throw new IllegalArgumentException("Trade quantity must be positive");
        }

        if (trade.getExecutionPrice() == null || trade.getExecutionPrice() <= 0) {
            logger.error("Invalid execution price: {}", trade.getExecutionPrice());
            throw new IllegalArgumentException("Execution price must be positive");
        }

        // Validate trade value calculation
        Double expectedValue = trade.getQuantity() * trade.getExecutionPrice();
        if (Math.abs(trade.getTradeValue() - expectedValue) > 0.01) {
            logger.warn("Trade value mismatch: expected={}, actual={}", expectedValue, trade.getTradeValue());
        }

        // Update timestamp
        trade.setUpdatedAt(LocalDateTime.now());

        logger.info("Trade {} confirmed successfully", trade.getTradeId());
        return entityWithMetadata;
    }
}

