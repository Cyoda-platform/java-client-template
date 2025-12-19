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
 * TradeCreationProcessor - Creates trade record from execution
 */
@Component
public class TradeCreationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TradeCreationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public TradeCreationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Creating Trade for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Trade.class)
                .validate(this::isValidTrade, "Invalid trade")
                .map(ctx -> createTrade(ctx.entityResponse()))
                .complete();
    }

    private EntityWithMetadata<Trade> createTrade(EntityWithMetadata<Trade> tradeWithMetadata) {
        Trade trade = tradeWithMetadata.entity();

        // Initialize trade
        trade.setSettlementStatus("PENDING");
        trade.setExecutedAt(LocalDateTime.now());
        trade.setCreatedAt(LocalDateTime.now());
        trade.setCreatedBy("SYSTEM");

        // Calculate total value
        Double totalValue = trade.getQuantity() * trade.getPrice();
        trade.setTotalValue(totalValue);

        logger.info("Trade created: {} for {} shares at {}",
            trade.getTradeId(), trade.getQuantity(), trade.getPrice());

        return tradeWithMetadata;
    }

    private boolean isValidTrade(EntityWithMetadata<Trade> tradeWithMetadata) {
        Trade trade = tradeWithMetadata.entity();
        return trade.getTradeId() != null && !trade.getTradeId().isBlank() &&
               trade.getOrderId() != null && !trade.getOrderId().isBlank() &&
               trade.getQuantity() != null && trade.getQuantity() > 0 &&
               trade.getPrice() != null && trade.getPrice() > 0;
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}

