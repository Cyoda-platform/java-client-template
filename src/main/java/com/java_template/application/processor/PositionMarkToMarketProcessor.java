package com.java_template.application.processor;

import com.java_template.application.entity.position.version_1.Position;
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
 * Processor for mark-to-market position valuation
 * Updates position value based on current market prices
 */
@Component
public class PositionMarkToMarketProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PositionMarkToMarketProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PositionMarkToMarketProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PositionMarkToMarket for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Position.class)
                .validate(this::isValidEntityWithMetadata, "Invalid position wrapper")
                .map(this::markToMarket)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Position> entityWithMetadata) {
        Position entity = entityWithMetadata.entity();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) &&
               entityWithMetadata.metadata().getId() != null;
    }

    private EntityWithMetadata<Position> markToMarket(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Position> context) {

        EntityWithMetadata<Position> entityWithMetadata = context.entityResponse();
        Position position = entityWithMetadata.entity();

        logger.debug("Marking position {} to market at price {}", 
                   position.getPositionId(), position.getCurrentPrice());

        // Recalculate market value
        if (position.getQuantity() != null && position.getCurrentPrice() != null) {
            position.setMarketValue(position.getQuantity() * position.getCurrentPrice());
        }

        // Recalculate unrealized P&L
        if (position.getMarketValue() != null && position.getCostBasis() != null) {
            position.setUnrealizedPnl(position.getMarketValue() - position.getCostBasis());
            
            // Calculate P&L percentage
            if (position.getCostBasis() > 0) {
                position.setUnrealizedPnlPercent((position.getUnrealizedPnl() / position.getCostBasis()) * 100);
            }
        }

        // Update timestamp
        position.setLastMarkToMarketTime(LocalDateTime.now());
        position.setUpdatedAt(LocalDateTime.now());

        logger.info("Position {} marked to market: market value={}, unrealized P&L={}", 
                   position.getPositionId(), position.getMarketValue(), position.getUnrealizedPnl());

        return entityWithMetadata;
    }
}

