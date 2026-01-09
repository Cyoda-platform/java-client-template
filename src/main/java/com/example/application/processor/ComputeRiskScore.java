package com.example.application.processor;

import com.example.application.entity.customer.version_1.Customer;
import com.example.application.entity.transaction.version_1.Transaction;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * ComputeRiskScore Processor - KYC Onboarding & Transaction Monitoring Workflows
 *
 * Computes risk score for both customers and transactions.
 * Evaluates multiple risk factors to determine overall compliance risk level.
 */
@Component
public class ComputeRiskScore implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ComputeRiskScore.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ComputeRiskScore(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Computing risk score for request: {}", request.getId());

        // Try to process as Customer first, then as Transaction
        try {
            return serializer.withRequest(request)
                    .toEntityWithMetadata(Customer.class)
                    .validate(this::isValidCustomer, "Invalid customer entity")
                    .map(this::processCustomerRiskScore)
                    .complete();
        } catch (Exception e) {
            logger.debug("Not a Customer entity, trying Transaction");
            return serializer.withRequest(request)
                    .toEntityWithMetadata(Transaction.class)
                    .validate(this::isValidTransaction, "Invalid transaction entity")
                    .map(this::processTransactionRiskScore)
                    .complete();
        }
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidCustomer(EntityWithMetadata<Customer> entityWithMetadata) {
        Customer entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    private boolean isValidTransaction(EntityWithMetadata<Transaction> entityWithMetadata) {
        Transaction entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    private EntityWithMetadata<Customer> processCustomerRiskScore(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Customer> context) {

        EntityWithMetadata<Customer> entityWithMetadata = context.entityResponse();
        Customer customer = entityWithMetadata.entity();

        logger.debug("Computing risk score for customer: {}", customer.getId());
        double riskScore = calculateCustomerRiskScore(customer);

        if (customer.getMetadata() == null) {
            customer.setMetadata(new HashMap<>());
        }
        customer.getMetadata().put("riskScore", riskScore);
        customer.getMetadata().put("riskLevel", getRiskLevel(riskScore));
        customer.getMetadata().put("riskFactors", identifyCustomerRiskFactors(customer));

        logger.info("Risk score computed for customer {}: {}", customer.getId(), riskScore);
        return entityWithMetadata;
    }

    private EntityWithMetadata<Transaction> processTransactionRiskScore(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Transaction> context) {

        EntityWithMetadata<Transaction> entityWithMetadata = context.entityResponse();
        Transaction transaction = entityWithMetadata.entity();

        logger.debug("Computing risk score for transaction: {}", transaction.getId());
        double riskScore = calculateTransactionRiskScore(transaction);

        if (transaction.getMetadata() == null) {
            transaction.setMetadata(new HashMap<>());
        }
        transaction.setScore(riskScore);
        transaction.getMetadata().put("riskScore", riskScore);
        transaction.getMetadata().put("riskLevel", getRiskLevel(riskScore));

        logger.info("Risk score computed for transaction {}: {}", transaction.getId(), riskScore);
        return entityWithMetadata;
    }

    private double calculateCustomerRiskScore(Customer customer) {
        double score = 0.0;

        if (customer.getStatus() == Customer.Status.VERIFIED) {
            score += 10;
        } else if (customer.getStatus() == Customer.Status.PENDING) {
            score += 25;
        } else if (customer.getStatus() == Customer.Status.SUSPENDED) {
            score += 30;
        }

        if (customer.getKycLevel() == Customer.KycLevel.HIGH) {
            score += 5;
        } else if (customer.getKycLevel() == Customer.KycLevel.MEDIUM) {
            score += 15;
        } else if (customer.getKycLevel() == Customer.KycLevel.LOW) {
            score += 20;
        }

        if (customer.getCustomerType() == Customer.CustomerType.BUSINESS) {
            score += 10;
        } else {
            score += 5;
        }

        if (customer.getAddresses() == null || customer.getAddresses().isEmpty()) {
            score += 20;
        } else {
            score += 5;
        }

        return Math.min(score, 100.0);
    }

    private double calculateTransactionRiskScore(Transaction transaction) {
        double score = 0.0;

        // Factor 1: Flags (each flag adds points)
        if (transaction.getFlags() != null) {
            score += transaction.getFlags().size() * 15;
        }

        // Factor 2: Watchlist matches
        if (transaction.getMatchedWatchlistIds() != null && !transaction.getMatchedWatchlistIds().isEmpty()) {
            score += transaction.getMatchedWatchlistIds().size() * 25;
        }

        // Factor 3: Transaction amount
        if (transaction.getAmount() != null && transaction.getAmount().doubleValue() > 10000) {
            score += 20;
        }

        // Factor 4: High-risk country
        if (transaction.getCountry() != null && isHighRiskCountry(transaction.getCountry())) {
            score += 25;
        }

        return Math.min(score, 100.0);
    }

    private String getRiskLevel(double score) {
        if (score >= 70) return "HIGH";
        if (score >= 40) return "MEDIUM";
        return "LOW";
    }

    private java.util.List<String> identifyCustomerRiskFactors(Customer customer) {
        java.util.List<String> factors = new java.util.ArrayList<>();

        if (customer.getStatus() != Customer.Status.VERIFIED) {
            factors.add("UNVERIFIED_STATUS");
        }
        if (customer.getKycLevel() == Customer.KycLevel.LOW) {
            factors.add("LOW_KYC_LEVEL");
        }
        if (customer.getAddresses() == null || customer.getAddresses().isEmpty()) {
            factors.add("NO_ADDRESS_PROVIDED");
        }

        return factors;
    }

    private boolean isHighRiskCountry(String country) {
        java.util.Set<String> highRiskCountries = java.util.Set.of("KP", "IR", "SY", "CU");
        return country != null && highRiskCountries.contains(country.toUpperCase());
    }
}

