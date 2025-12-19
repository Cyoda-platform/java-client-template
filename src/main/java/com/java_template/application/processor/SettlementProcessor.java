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

import java.time.LocalDateTime;

/**
 * SettlementProcessor - Handles trade settlement and cash/position updates
 */
@Component
public class SettlementProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SettlementProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SettlementProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Settling Trade for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Trade.class)
                .validate(this::isValidTrade, "Invalid trade")
                .map(this::settleTrade)
                .complete();
    }

    private EntityWithMetadata<Trade> settleTrade(EntityWithMetadata<Trade> tradeWithMetadata) {
        Trade trade = tradeWithMetadata.entity();

        // Update settlement status
        trade.setSettlementStatus("SETTLED");
        trade.setSettlementDate(LocalDateTime.now());

        logger.info("Trade settled: {} for {} shares at {}",
            trade.getTradeId(), trade.getQuantity(), trade.getPrice());

        return tradeWithMetadata;
    }

    private boolean isValidTrade(EntityWithMetadata<Trade> tradeWithMetadata) {
        Trade trade = tradeWithMetadata.entity();
        return trade.getTradeId() != null &&
               trade.getSettlementStatus() != null &&
               "PENDING".equals(trade.getSettlementStatus());
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}

