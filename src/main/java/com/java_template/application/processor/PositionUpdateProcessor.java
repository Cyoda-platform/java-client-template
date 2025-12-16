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
 * Processor for updating position data
 * Handles quantity and cost basis updates from trades
 */
@Component
public class PositionUpdateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PositionUpdateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PositionUpdateProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PositionUpdate for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Position.class)
                .validate(this::isValidEntityWithMetadata, "Invalid position wrapper")
                .map(this::updatePosition)
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

    private EntityWithMetadata<Position> updatePosition(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Position> context) {

        EntityWithMetadata<Position> entityWithMetadata = context.entityResponse();
        Position position = entityWithMetadata.entity();

        logger.debug("Updating position: {} for {} shares of {}", 
                   position.getPositionId(), position.getQuantity(), position.getInstrumentSymbol());

        // Recalculate cost basis
        if (position.getQuantity() != null && position.getAverageCost() != null) {
            position.setCostBasis(position.getQuantity() * position.getAverageCost());
        }

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
        position.setUpdatedAt(LocalDateTime.now());

        logger.info("Position {} updated: quantity={}, market value={}, unrealized P&L={}", 
                   position.getPositionId(), position.getQuantity(), position.getMarketValue(), position.getUnrealizedPnl());

        return entityWithMetadata;
    }
}

