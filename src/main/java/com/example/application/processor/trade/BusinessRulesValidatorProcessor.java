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

import java.math.BigDecimal;

/**
 * BusinessRulesValidatorProcessor - Validates domain/business rules.
 * Checks required fields, quantity > 0, supported currency, etc.
 * On failure, marks rejected with reason.
 * Execution Mode: SYNC
 */
@Component
public class BusinessRulesValidatorProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BusinessRulesValidatorProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public BusinessRulesValidatorProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("BusinessRulesValidatorProcessor: Validating business rules for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Trade.class)
                .validate(this::isValidEntity, "Invalid trade entity")
                .map(this::validateBusinessRules)
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
     * Validate business rules for the trade
     * TODO: Implement configurable rule set via processor config
     */
    private EntityWithMetadata<Trade> validateBusinessRules(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Trade> context) {

        EntityWithMetadata<Trade> entityWithMetadata = context.entityResponse();
        Trade trade = entityWithMetadata.entity();

        logger.debug("Validating business rules for trade ID: {}", trade.getId());

        // TODO: Implement business rule validation
        // - Validate quantity > 0
        // - Validate price > 0
        // - Validate supported currencies (configurable list)
        // - Validate trade type is valid
        // - Validate counterparty exists and is active
        // - Validate instrument is tradeable
        // - Collect validation errors

        // Example business rule checks:
        if (trade.getQuantity() == null || trade.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            logger.error("Trade quantity must be greater than 0");
            // TODO: Attach error and trigger rejection
        }

        if (trade.getPrice() == null || trade.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            logger.error("Trade price must be greater than 0");
        }

        if (trade.getCurrency() == null || trade.getCurrency().isBlank()) {
            logger.error("Currency is required");
        } else {
            // TODO: Validate currency is in supported list (configurable)
            validateSupportedCurrency(trade.getCurrency());
        }

        if (trade.getTradeType() == null) {
            logger.error("Trade type is required");
        }

        logger.info("Business rules validation completed for trade ID: {}", trade.getId());
        return entityWithMetadata;
    }

    /**
     * Validate if currency is supported
     * TODO: Load from configuration
     */
    private void validateSupportedCurrency(String currency) {
        // TODO: Check against configurable list of supported currencies
        logger.debug("Validating currency: {}", currency);
    }
}

