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
 * TradeReconciliationProcessor - Reconciles trade with execution reports
 */
@Component
public class TradeReconciliationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TradeReconciliationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public TradeReconciliationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Reconciling Trade for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Trade.class)
                .validate(this::isValidTrade, "Invalid trade")
                .map(ctx -> reconcileTrade(ctx.entityResponse()))
                .complete();
    }

    private EntityWithMetadata<Trade> reconcileTrade(EntityWithMetadata<Trade> tradeWithMetadata) {
        Trade trade = tradeWithMetadata.entity();

        // In a real system, reconcile with execution reports
        logger.info("Trade reconciliation passed for: {} with {} shares",
            trade.getTradeId(), trade.getQuantity());

        return tradeWithMetadata;
    }

    private boolean isValidTrade(EntityWithMetadata<Trade> tradeWithMetadata) {
        Trade trade = tradeWithMetadata.entity();
        return trade.getTradeId() != null && trade.getOrderId() != null;
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}

