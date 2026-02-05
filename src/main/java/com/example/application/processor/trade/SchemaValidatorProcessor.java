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
 * SchemaValidatorProcessor - Validates incoming trade JSON against Trade JSON schema.
 * If valid, proceeds to next state; if invalid, transitions to 'rejected' with validation errors.
 * Execution Mode: SYNC
 */
@Component
public class SchemaValidatorProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SchemaValidatorProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SchemaValidatorProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("SchemaValidatorProcessor: Validating trade schema for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Trade.class)
                .validate(this::isValidSchema, "Trade schema validation failed")
                .map(this::validateTradeSchema)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Initial validation of entity wrapper
     */
    private boolean isValidSchema(EntityWithMetadata<Trade> entityWithMetadata) {
        Trade trade = entityWithMetadata.entity();
        return trade != null && entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Validate trade against schema requirements
     * TODO: Implement actual JSON schema validation against Trade.json schema
     */
    private EntityWithMetadata<Trade> validateTradeSchema(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Trade> context) {

        EntityWithMetadata<Trade> entityWithMetadata = context.entityResponse();
        Trade trade = entityWithMetadata.entity();

        logger.debug("Validating trade schema for trade ID: {}", trade.getId());

        // TODO: Implement schema validation logic
        // - Validate required fields presence
        // - Validate field types and formats
        // - Validate field value ranges
        // - Collect validation errors if any
        // - If validation fails, attach error messages to trade entity

        // Example validation checks:
        if (trade.getId() == null || trade.getId().isBlank()) {
            logger.error("Trade ID is required");
            // TODO: Attach error to entity and trigger rejection
        }

        if (trade.getInstrumentId() == null || trade.getInstrumentId().isBlank()) {
            logger.error("Instrument ID is required");
        }

        logger.info("Trade schema validation completed for trade ID: {}", trade.getId());
        return entityWithMetadata;
    }
}

