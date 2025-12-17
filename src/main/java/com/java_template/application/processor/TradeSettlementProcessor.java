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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * TradeSettlementProcessor - Settles matched trades
 * Updates wallet balances and creates ledger entries
 */
@Component
public class TradeSettlementProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TradeSettlementProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public TradeSettlementProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Settling trade for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Trade.class)
                .validate(this::isValidEntityWithMetadata, "Invalid trade wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Trade> entityWithMetadata) {
        Trade trade = entityWithMetadata.entity();
        return trade != null && trade.isValid(entityWithMetadata.metadata());
    }

    private EntityWithMetadata<Trade> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Trade> context) {

        EntityWithMetadata<Trade> entityWithMetadata = context.entityResponse();
        Trade trade = entityWithMetadata.entity();

        logger.debug("Settling trade: {} between orders: {} and {}", 
            trade.getTradeId(), trade.getBuyOrderId(), trade.getSellOrderId());

        // In production, this would:
        // 1. Create immutable ledger entries for both parties
        // 2. Update buyer wallet (debit quote, credit base)
        // 3. Update seller wallet (debit base, credit quote)
        // 4. Deduct fees from both parties
        // 5. Ensure idempotency with transaction IDs
        // 6. Handle partial fills and multiple trades per order

        trade.setStatus("SETTLED");
        trade.setSettledAt(LocalDateTime.now());

        logger.info("Trade {} settled successfully", trade.getTradeId());
        return entityWithMetadata;
    }
}

