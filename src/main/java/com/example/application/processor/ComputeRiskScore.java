package com.example.application.processor;

import com.example.application.entity.customer.version_1.Customer;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
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
 * ComputeRiskScore Processor - KYC Onboarding Workflow
 * 
 * Computes customer risk score based on KYC verification results and customer attributes.
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

        return serializer.withRequest(request)
                .toEntityWithMetadata(Customer.class)
                .validate(this::isValidEntityWithMetadata, "Invalid customer entity")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Customer> entityWithMetadata) {
        Customer entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    private EntityWithMetadata<Customer> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Customer> context) {

        EntityWithMetadata<Customer> entityWithMetadata = context.entityResponse();
        Customer customer = entityWithMetadata.entity();

        logger.debug("Computing risk score for customer: {}", customer.getId());

        // Calculate risk score based on multiple factors
        double riskScore = calculateRiskScore(customer);

        // Store risk score in metadata
        if (customer.getMetadata() == null) {
            customer.setMetadata(new HashMap<>());
        }
        customer.getMetadata().put("riskScore", riskScore);
        customer.getMetadata().put("riskLevel", getRiskLevel(riskScore));
        customer.getMetadata().put("riskFactors", identifyRiskFactors(customer));

        logger.info("Risk score computed for customer {}: {}", customer.getId(), riskScore);
        return entityWithMetadata;
    }

    private double calculateRiskScore(Customer customer) {
        double score = 0.0;

        // Factor 1: KYC verification status (0-30 points)
        if (customer.getStatus() == Customer.Status.VERIFIED) {
            score += 10;
        } else if (customer.getStatus() == Customer.Status.PENDING) {
            score += 25;
        } else if (customer.getStatus() == Customer.Status.SUSPENDED) {
            score += 30;
        }

        // Factor 2: KYC level (0-20 points)
        if (customer.getKycLevel() == Customer.KycLevel.HIGH) {
            score += 5;
        } else if (customer.getKycLevel() == Customer.KycLevel.MEDIUM) {
            score += 15;
        } else if (customer.getKycLevel() == Customer.KycLevel.LOW) {
            score += 20;
        }

        // Factor 3: Customer type (0-15 points)
        if (customer.getCustomerType() == Customer.CustomerType.BUSINESS) {
            score += 10;
        } else {
            score += 5;
        }

        // Factor 4: Address validation (0-20 points)
        if (customer.getAddresses() == null || customer.getAddresses().isEmpty()) {
            score += 20;
        } else {
            score += 5;
        }

        // Factor 5: Metadata risk indicators (0-15 points)
        if (customer.getMetadata() != null) {
            if (customer.getMetadata().containsKey("verificationScore")) {
                double verificationScore = (Double) customer.getMetadata().get("verificationScore");
                score += (1 - verificationScore) * 15;
            }
        }

        return Math.min(score, 100.0);
    }

    private String getRiskLevel(double score) {
        if (score >= 70) return "HIGH";
        if (score >= 40) return "MEDIUM";
        return "LOW";
    }

    private java.util.List<String> identifyRiskFactors(Customer customer) {
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
}

