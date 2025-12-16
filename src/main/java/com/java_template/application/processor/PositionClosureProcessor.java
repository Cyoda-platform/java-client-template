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
 * Processor for closing positions
 * Finalizes position and records realized P&L
 */
@Component
public class PositionClosureProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PositionClosureProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PositionClosureProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PositionClosure for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Position.class)
                .validate(this::isValidEntityWithMetadata, "Invalid position wrapper")
                .map(this::closePosition)
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

    private EntityWithMetadata<Position> closePosition(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Position> context) {

        EntityWithMetadata<Position> entityWithMetadata = context.entityResponse();
        Position position = entityWithMetadata.entity();

        logger.debug("Closing position: {} for {} shares of {}", 
                   position.getPositionId(), position.getQuantity(), position.getInstrumentSymbol());

        // Record realized P&L
        if (position.getUnrealizedPnl() != null) {
            Double realizedPnl = position.getRealizedPnl() != null ? position.getRealizedPnl() : 0.0;
            position.setRealizedPnl(realizedPnl + position.getUnrealizedPnl());
        }

        // Set quantity to zero
        position.setQuantity(0L);
        position.setMarketValue(0.0);
        position.setUnrealizedPnl(0.0);
        position.setUnrealizedPnlPercent(0.0);

        // Update timestamp
        position.setUpdatedAt(LocalDateTime.now());

        logger.info("Position {} closed with realized P&L: {}", 
                   position.getPositionId(), position.getRealizedPnl());

        return entityWithMetadata;
    }
}

