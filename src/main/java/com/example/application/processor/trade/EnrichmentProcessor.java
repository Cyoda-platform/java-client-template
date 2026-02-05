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
 * EnrichmentProcessor - Enriches trade with instrument and counterparty data.
 * Calls repository/services to fetch reference data.
 * On enrichment failure, transitions to 'failed'.
 * Execution Mode: ASYNC_NEW_TX
 * Retry Policy: FIXED
 * Response Timeout: 5000ms
 */
@Component
public class EnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichmentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("EnrichmentProcessor: Enriching trade for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Trade.class)
                .validate(this::isValidEntity, "Invalid trade entity")
                .map(this::enrichTrade)
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
     * Enrich trade with instrument and counterparty data
     * TODO: Implement calls to repository/services for reference data
     */
    private EntityWithMetadata<Trade> enrichTrade(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Trade> context) {

        EntityWithMetadata<Trade> entityWithMetadata = context.entityResponse();
        Trade trade = entityWithMetadata.entity();

        logger.debug("Enriching trade ID: {}", trade.getId());

        try {
            // TODO: Fetch instrument details from repository
            // - Call InstrumentRepository or InstrumentService
            // - Validate instrument exists and is active
            // - Enrich trade with instrument details (name, type, etc.)
            enrichInstrumentData(trade);

            // TODO: Fetch counterparty details from repository
            // - Call CounterpartyRepository or CounterpartyService
            // - Validate counterparty exists and is active
            // - Enrich trade with counterparty details (name, credit rating, etc.)
            enrichCounterpartyData(trade);

            logger.info("Trade enrichment completed successfully for trade ID: {}", trade.getId());
        } catch (Exception e) {
            logger.error("Trade enrichment failed for trade ID: {}", trade.getId(), e);
            // TODO: Trigger transition to 'failed' state
            throw new RuntimeException("Enrichment failed: " + e.getMessage(), e);
        }

        return entityWithMetadata;
    }

    /**
     * Enrich trade with instrument data
     * TODO: Implement actual repository/service calls
     */
    private void enrichInstrumentData(Trade trade) {
        logger.debug("Enriching instrument data for instrument ID: {}", trade.getInstrumentId());
        // TODO: Call InstrumentRepository.findById(trade.getInstrumentId())
        // TODO: Validate instrument exists
        // TODO: Add instrument details to trade (e.g., instrumentName, instrumentType, etc.)
    }

    /**
     * Enrich trade with counterparty data
     * TODO: Implement actual repository/service calls
     */
    private void enrichCounterpartyData(Trade trade) {
        logger.debug("Enriching counterparty data for counterparty ID: {}", trade.getCounterpartyId());
        // TODO: Call CounterpartyRepository.findById(trade.getCounterpartyId())
        // TODO: Validate counterparty exists and is active
        // TODO: Add counterparty details to trade (e.g., counterpartyName, creditRating, etc.)
    }
}

