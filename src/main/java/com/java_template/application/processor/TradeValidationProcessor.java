package com.java_template.application.processor;

import com.java_template.application.entity.trade.version_1.Trade;
import com.java_template.common.dto.EntityWithMetadata;
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

/**
 * TradeValidationProcessor - Validates trade data and business rules
 */
@Component
public class TradeValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TradeValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public TradeValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Validating Trade for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Trade.class)
                .validate(this::isValidTrade, "Invalid trade")
                .map(ctx -> validateTrade(ctx.entityResponse()))
                .complete();
    }

    private EntityWithMetadata<Trade> validateTrade(EntityWithMetadata<Trade> tradeWithMetadata) {
        Trade trade = tradeWithMetadata.entity();

        // Validate trade data
        if (trade.getQuantity() <= 0) {
            throw new IllegalArgumentException("Trade quantity must be positive");
        }

        if (trade.getPrice() <= 0) {
            throw new IllegalArgumentException("Trade price must be positive");
        }

        logger.info("Trade validation passed for: {}", trade.getTradeId());
        return tradeWithMetadata;
    }

    private boolean isValidTrade(EntityWithMetadata<Trade> tradeWithMetadata) {
        Trade trade = tradeWithMetadata.entity();
        return trade.getTradeId() != null &&
               trade.getOrderId() != null &&
               trade.getQuantity() != null &&
               trade.getPrice() != null;
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}

