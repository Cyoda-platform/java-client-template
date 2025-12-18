package com.example.application.processor;

import com.example.application.entity.payment_transaction.version_1.PaymentTransaction;
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
 * PaymentTransactionProcessor - Validates and processes payment transactions
 * Handles multi-currency conversion, tokenization validation, and audit logging
 */
@Component
public class PaymentTransactionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentTransactionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PaymentTransactionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PaymentTransaction for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(PaymentTransaction.class)
                .validate(this::isValidEntityWithMetadata, "Invalid payment transaction")
                .map(this::processPaymentLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<PaymentTransaction> entityWithMetadata) {
        PaymentTransaction entity = entityWithMetadata.entity();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) &&
               entityWithMetadata.metadata().getId() != null;
    }

    private EntityWithMetadata<PaymentTransaction> processPaymentLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<PaymentTransaction> context) {

        EntityWithMetadata<PaymentTransaction> entityWithMetadata = context.entityResponse();
        PaymentTransaction transaction = entityWithMetadata.entity();

        logger.debug("Processing payment transaction: {} for merchant: {}", 
                   transaction.getTransactionId(), transaction.getMerchantId());

        // Validate tokenization (PCI compliance)
        validateTokenization(transaction);

        // Calculate exchange rate if multi-currency
        if (transaction.getBaseCurrency() != null && !transaction.getBaseCurrency().equals(transaction.getCurrency())) {
            calculateExchangeRate(transaction);
        }

        // Set initial status
        if (transaction.getStatus() == null) {
            transaction.setStatus("PENDING");
        }

        // Update timestamps
        transaction.setUpdatedAt(LocalDateTime.now());
        if (transaction.getCreatedAt() == null) {
            transaction.setCreatedAt(LocalDateTime.now());
        }

        logger.info("PaymentTransaction {} processed successfully", transaction.getTransactionId());
        return entityWithMetadata;
    }

    private void validateTokenization(PaymentTransaction transaction) {
        // Verify card token exists (never store plaintext card data)
        if (transaction.getCardToken() == null || transaction.getCardToken().isBlank()) {
            logger.warn("Transaction {} missing card token", transaction.getTransactionId());
            throw new IllegalArgumentException("Card token is required for PCI compliance");
        }

        // Verify last 4 digits are present for display
        if (transaction.getCardLast4() == null || transaction.getCardLast4().isBlank()) {
            logger.warn("Transaction {} missing card last 4", transaction.getTransactionId());
            throw new IllegalArgumentException("Card last 4 digits required");
        }

        logger.debug("Tokenization validated for transaction: {}", transaction.getTransactionId());
    }

    private void calculateExchangeRate(PaymentTransaction transaction) {
        // Simplified exchange rate calculation
        // In production, this would call an external exchange rate service
        if (transaction.getExchangeRate() == null) {
            // Default rates (simplified for demo)
            transaction.setExchangeRate(java.math.BigDecimal.ONE);
        }

        if (transaction.getBaseAmount() == null) {
            transaction.setBaseAmount(transaction.getAmount().multiply(transaction.getExchangeRate()));
        }

        logger.debug("Exchange rate calculated: {} {} = {} {}", 
                   transaction.getAmount(), transaction.getCurrency(),
                   transaction.getBaseAmount(), transaction.getBaseCurrency());
    }
}

