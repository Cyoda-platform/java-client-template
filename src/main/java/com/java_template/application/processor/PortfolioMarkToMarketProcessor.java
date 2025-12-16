package com.java_template.application.processor;

import com.java_template.application.entity.portfolio.version_1.Portfolio;
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
 * Processor for mark-to-market portfolio valuation
 * Updates portfolio value based on current market prices
 */
@Component
public class PortfolioMarkToMarketProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioMarkToMarketProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PortfolioMarkToMarketProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PortfolioMarkToMarket for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Portfolio.class)
                .validate(this::isValidEntityWithMetadata, "Invalid portfolio wrapper")
                .map(this::markToMarket)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Portfolio> entityWithMetadata) {
        Portfolio entity = entityWithMetadata.entity();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) &&
               entityWithMetadata.metadata().getId() != null;
    }

    private EntityWithMetadata<Portfolio> markToMarket(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Portfolio> context) {

        EntityWithMetadata<Portfolio> entityWithMetadata = context.entityResponse();
        Portfolio portfolio = entityWithMetadata.entity();

        logger.debug("Marking portfolio {} to market", portfolio.getPortfolioId());

        // Calculate total position value (simplified - in production would fetch actual positions)
        Double positionValue = 0.0;
        if (portfolio.getPositionIds() != null && !portfolio.getPositionIds().isEmpty()) {
            // In production, would fetch each position and sum their market values
            positionValue = 50000.0; // Placeholder
        }

        // Calculate total portfolio value
        Double totalValue = portfolio.getCashBalance() + positionValue;
        portfolio.setTotalValue(totalValue);

        // Calculate unrealized P&L (simplified)
        Double costBasis = 45000.0; // Placeholder
        Double unrealizedPnl = positionValue - costBasis;
        portfolio.setUnrealizedPnl(unrealizedPnl);

        // Update timestamp
        portfolio.setLastMarkToMarketTime(LocalDateTime.now());
        portfolio.setUpdatedAt(LocalDateTime.now());

        logger.info("Portfolio {} marked to market: total value = {}, unrealized P&L = {}", 
                   portfolio.getPortfolioId(), totalValue, unrealizedPnl);

        return entityWithMetadata;
    }
}

