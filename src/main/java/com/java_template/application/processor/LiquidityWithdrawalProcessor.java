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

/**
 * LiquidityWithdrawalProcessor - Withdraws liquidity from pool
 * Closes pool and returns funds to provider
 */
@Component
public class LiquidityWithdrawalProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LiquidityWithdrawalProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public LiquidityWithdrawalProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Withdrawing liquidity from pool for request: {}", request.getId());

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

        logger.debug("Withdrawing liquidity from pool: {}", pool.getPoolId());

        // In production, this would:
        // 1. Close all open positions
        // 2. Settle pending trades
        // 3. Calculate final P&L
        // 4. Return funds to provider
        // 5. Create final settlement records

        pool.setStatus("CLOSED");
        pool.setBalanceBase(BigDecimal.ZERO);
        pool.setBalanceQuote(BigDecimal.ZERO);

        logger.info("Liquidity pool {} closed and funds withdrawn", pool.getPoolId());
        return entityWithMetadata;
    }
}

