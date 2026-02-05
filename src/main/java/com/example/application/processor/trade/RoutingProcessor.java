package com.example.application.processor.trade;

import com.example.application.entity.trade.version_1.Trade;
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
 * RoutingProcessor - Routes validated & enriched trades to downstream systems.
 * Routes to clearing, settlements, risk systems.
 * On success, marks routed; on failure, marks failed.
 * Execution Mode: ASYNC_SAME_TX
 */
@Component
public class RoutingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RoutingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RoutingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("RoutingProcessor: Routing trade for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Trade.class)
                .validate(this::isValidEntity, "Invalid trade entity")
                .map(this::routeTrade)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Initial validation of entity wrapper
     */
    private boolean isValidEntity(EntityWithMetadata<Trade> entityWithMetadata) {
        Trade trade = entityWithMetadata.entity();
        return trade != null && entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Route trade to downstream systems
     * TODO: Implement routing logic to clearing, settlements, risk systems
     */
    private EntityWithMetadata<Trade> routeTrade(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Trade> context) {

        EntityWithMetadata<Trade> entityWithMetadata = context.entityResponse();
        Trade trade = entityWithMetadata.entity();

        logger.debug("Routing trade ID: {}", trade.getId());

        try {
            // TODO: Route to clearing system
            routeToClearing(trade);

            // TODO: Route to settlements system
            routeToSettlements(trade);

            // TODO: Route to risk system
            routeToRisk(trade);

            logger.info("Trade routed successfully for trade ID: {}", trade.getId());
        } catch (Exception e) {
            logger.error("Trade routing failed for trade ID: {}", trade.getId(), e);
            // TODO: Trigger transition to 'failed' state
            throw new RuntimeException("Routing failed: " + e.getMessage(), e);
        }

        return entityWithMetadata;
    }

    /**
     * Route trade to clearing system
     * TODO: Implement actual downstream client call
     */
    private void routeToClearing(Trade trade) {
        logger.debug("Routing trade {} to clearing system", trade.getId());
        // TODO: Call ClearingClient or ClearingService
        // TODO: Handle response and errors
    }

    /**
     * Route trade to settlements system
     * TODO: Implement actual downstream client call
     */
    private void routeToSettlements(Trade trade) {
        logger.debug("Routing trade {} to settlements system", trade.getId());
        // TODO: Call SettlementsClient or SettlementsService
        // TODO: Handle response and errors
    }

    /**
     * Route trade to risk system
     * TODO: Implement actual downstream client call
     */
    private void routeToRisk(Trade trade) {
        logger.debug("Routing trade {} to risk system", trade.getId());
        // TODO: Call RiskClient or RiskService
        // TODO: Handle response and errors
    }
}

