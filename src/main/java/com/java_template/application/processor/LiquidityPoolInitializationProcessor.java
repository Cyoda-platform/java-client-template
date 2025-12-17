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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LiquidityPoolInitializationProcessor - Initializes a liquidity pool
 * Sets up initial balances and strategy parameters
 */
@Component
public class LiquidityPoolInitializationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LiquidityPoolInitializationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public LiquidityPoolInitializationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Initializing liquidity pool for request: {}", request.getId());

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

        logger.debug("Initializing liquidity pool: {} for market: {}", pool.getPoolId(), pool.getMarketId());

        // Initialize pool metrics
        pool.setTotalVolume(BigDecimal.ZERO);
        pool.setTotalFees(BigDecimal.ZERO);
        pool.setStatus("ACTIVE");
        pool.setCreatedAt(LocalDateTime.now());
        pool.setUpdatedAt(LocalDateTime.now());
        pool.setLastRebalanceAt(LocalDateTime.now());

        logger.info("Liquidity pool {} initialized with base balance: {}, quote balance: {}", 
            pool.getPoolId(), pool.getBalanceBase(), pool.getBalanceQuote());

        return entityWithMetadata;
    }
}

