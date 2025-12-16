package com.java_template.application.processor;

import com.java_template.application.entity.instrument.version_1.Instrument;
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
 * Processor for updating instrument data
 * Handles price updates, Greeks calculations, and status changes
 */
@Component
public class InstrumentUpdateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(InstrumentUpdateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public InstrumentUpdateProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing InstrumentUpdate for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Instrument.class)
                .validate(this::isValidEntityWithMetadata, "Invalid instrument wrapper")
                .map(this::processInstrumentUpdate)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Instrument> entityWithMetadata) {
        Instrument entity = entityWithMetadata.entity();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) &&
               entityWithMetadata.metadata().getId() != null;
    }

    private EntityWithMetadata<Instrument> processInstrumentUpdate(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Instrument> context) {

        EntityWithMetadata<Instrument> entityWithMetadata = context.entityResponse();
        Instrument instrument = entityWithMetadata.entity();

        logger.debug("Updating instrument: {} with current price: {}", 
                   instrument.getSymbol(), instrument.getCurrentPrice());

        // Update timestamp
        instrument.setUpdatedAt(LocalDateTime.now());

        // Validate bid-ask spread
        if (instrument.getBidPrice() != null && instrument.getAskPrice() != null) {
            if (instrument.getBidPrice() > instrument.getAskPrice()) {
                logger.warn("Invalid bid-ask spread for {}: bid={}, ask={}", 
                           instrument.getSymbol(), instrument.getBidPrice(), instrument.getAskPrice());
            }
        }

        // Update last trade price if provided
        if (instrument.getLastTradePrice() != null) {
            instrument.setCurrentPrice(instrument.getLastTradePrice());
        }

        logger.info("Instrument {} updated successfully", instrument.getSymbol());
        return entityWithMetadata;
    }
}

