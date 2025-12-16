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
 * Processor for settling trades
 * Finalizes trade settlement and updates portfolio
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
        logger.info("Processing TradeSettlement for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Trade.class)
                .validate(this::isValidEntityWithMetadata, "Invalid trade wrapper")
                .map(this::settleTrade)
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

    private EntityWithMetadata<Trade> settleTrade(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Trade> context) {

        EntityWithMetadata<Trade> entityWithMetadata = context.entityResponse();
        Trade trade = entityWithMetadata.entity();

        logger.debug("Settling trade: {} scheduled for {}", 
                   trade.getTradeId(), trade.getSettlementDate());

        // Check if settlement date has passed
        if (trade.getSettlementDate() != null && trade.getSettlementDate().isAfter(LocalDateTime.now())) {
            logger.warn("Trade {} settlement date is in the future", trade.getTradeId());
        }

        // Update settlement status
        trade.setSettlementStatus("SETTLED");

        // Update timestamp
        trade.setUpdatedAt(LocalDateTime.now());

        logger.info("Trade {} settled successfully", trade.getTradeId());
        return entityWithMetadata;
    }
}

