package com.java_template.application.processor;

import com.java_template.application.entity.market_data_tick.version_1.MarketDataTick;
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
 * Processor for normalizing market data ticks
 * Converts multi-exchange feeds to canonical format
 */
@Component
public class MarketDataNormalizationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataNormalizationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public MarketDataNormalizationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing MarketDataNormalization for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(MarketDataTick.class)
                .validate(this::isValidEntityWithMetadata, "Invalid market data tick wrapper")
                .map(this::normalizeTick)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<MarketDataTick> entityWithMetadata) {
        MarketDataTick entity = entityWithMetadata.entity();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) &&
               entityWithMetadata.metadata().getId() != null;
    }

    private EntityWithMetadata<MarketDataTick> normalizeTick(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<MarketDataTick> context) {

        EntityWithMetadata<MarketDataTick> entityWithMetadata = context.entityResponse();
        MarketDataTick tick = entityWithMetadata.entity();

        logger.debug("Normalizing market data tick for {} from {}", 
                   tick.getInstrumentSymbol(), tick.getExchange());

        // Validate bid-ask spread
        if (tick.getBidPrice() != null && tick.getAskPrice() != null) {
            if (tick.getBidPrice() > tick.getAskPrice()) {
                logger.warn("Invalid bid-ask spread for {}: bid={}, ask={}", 
                           tick.getInstrumentSymbol(), tick.getBidPrice(), tick.getAskPrice());
                // Swap them
                Double temp = tick.getBidPrice();
                tick.setBidPrice(tick.getAskPrice());
                tick.setAskPrice(temp);
            }
        }

        // Calculate mid price if not provided
        if (tick.getLastPrice() == null && tick.getBidPrice() != null && tick.getAskPrice() != null) {
            tick.setLastPrice((tick.getBidPrice() + tick.getAskPrice()) / 2.0);
        }

        // Validate volume
        if (tick.getVolume() != null && tick.getVolume() < 0) {
            logger.warn("Negative volume for {}: {}", tick.getInstrumentSymbol(), tick.getVolume());
            tick.setVolume(0L);
        }

        // Update creation timestamp
        tick.setCreatedAt(LocalDateTime.now());

        logger.info("Market data tick normalized for {} at {}", 
                   tick.getInstrumentSymbol(), tick.getTimestamp());

        return entityWithMetadata;
    }
}

