package com.java_template.application.processor;

import com.java_template.application.entity.liquiditypool.version_1.LiquidityPool;
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
 * LiquidityRebalancingProcessor - Rebalances liquidity pool
 * Monitors spreads and inventory, adjusts positions
 */
@Component
public class LiquidityRebalancingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LiquidityRebalancingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public LiquidityRebalancingProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Rebalancing liquidity pool for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(LiquidityPool.class)
                .validate(this::isValidEntityWithMetadata, "Invalid liquidity pool wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<LiquidityPool> entityWithMetadata) {
        LiquidityPool pool = entityWithMetadata.entity();
        return pool != null && pool.isValid(entityWithMetadata.metadata());
    }

    private EntityWithMetadata<LiquidityPool> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<LiquidityPool> context) {

        EntityWithMetadata<LiquidityPool> entityWithMetadata = context.entityResponse();
        LiquidityPool pool = entityWithMetadata.entity();

        logger.debug("Rebalancing liquidity pool: {} for market: {}", pool.getPoolId(), pool.getMarketId());

        // In production, this would:
        // 1. Calculate current inventory imbalance
        // 2. Evaluate spread efficiency
        // 3. Determine rebalancing actions
        // 4. Execute trades to rebalance
        // 5. Update spreads based on market conditions
        // 6. Route to external providers if needed

        pool.setLastRebalanceAt(LocalDateTime.now());

        logger.info("Liquidity pool {} rebalanced. Base: {}, Quote: {}", 
            pool.getPoolId(), pool.getBalanceBase(), pool.getBalanceQuote());

        return entityWithMetadata;
    }
}

