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
 * CallKYCProvider Processor - KYC Onboarding Workflow
 * 
 * Calls third-party KYC provider API to verify customer identity.
 * Handles API integration, response parsing, and verification status updates.
 */
@Component
public class CallKYCProvider implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CallKYCProvider.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CallKYCProvider(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Calling KYC provider for request: {}", request.getId());

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

        logger.debug("Calling KYC provider for customer: {}", customer.getId());

        // Call external KYC provider API
        Map<String, Object> kycResponse = callExternalKYCProvider(customer);

        // Update customer with KYC verification results
        if (customer.getMetadata() == null) {
            customer.setMetadata(new HashMap<>());
        }
        customer.getMetadata().putAll(kycResponse);

        // Update KYC level based on verification
        if ((Boolean) kycResponse.getOrDefault("verified", false)) {
            customer.setKycLevel(Customer.KycLevel.MEDIUM);
            customer.setStatus(Customer.Status.VERIFIED);
            logger.info("Customer {} verified by KYC provider", customer.getId());
        } else {
            customer.setStatus(Customer.Status.PENDING);
            logger.warn("Customer {} failed KYC verification", customer.getId());
        }

        return entityWithMetadata;
    }

    private Map<String, Object> callExternalKYCProvider(Customer customer) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Simulate KYC provider API call
            // In production, this would call actual third-party API with retry logic
            boolean verified = simulateKYCVerification(customer);
            
            response.put("verified", verified);
            response.put("verificationProvider", "ThirdPartyKYCProvider");
            response.put("verificationTimestamp", System.currentTimeMillis());
            response.put("verificationScore", verified ? 0.95 : 0.45);
            response.put("matchedFields", java.util.List.of("name", "dob", "address"));
            
            logger.info("KYC provider response received for customer: {}", customer.getId());
        } catch (Exception e) {
            logger.error("Error calling KYC provider for customer: {}", customer.getId(), e);
            response.put("verified", false);
            response.put("error", e.getMessage());
        }
        
        return response;
    }

    private boolean simulateKYCVerification(Customer customer) {
        // Simulate verification logic - in production, call actual API
        return customer.getLegalName() != null && !customer.getLegalName().isBlank() &&
               customer.getDob() != null;
    }
}

