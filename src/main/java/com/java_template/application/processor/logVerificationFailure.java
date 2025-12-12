package com.java_template.application.processor;

import com.java_template.application.entity.customer.version_1.Customer;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Processor to log verification failure and prepare for retry
 * 
 * This processor is triggered during the verification_pending -> onboarding transition
 * when verification fails. It logs the failure details and prepares the customer
 * for another verification attempt.
 */
@Component
public class logVerificationFailure implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(logVerificationFailure.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public logVerificationFailure(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Customer.class)
                .validate(this::isValidEntityWithMetadata, "Invalid customer entity wrapper")
                .map(this::processVerificationFailure)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Customer> entityWithMetadata) {
        Customer customer = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return customer != null && customer.isValid() && technicalId != null;
    }

    /**
     * Main business logic for logging verification failure
     */
    private EntityWithMetadata<Customer> processVerificationFailure(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Customer> context) {

        EntityWithMetadata<Customer> entityWithMetadata = context.entityResponse();
        Customer customer = entityWithMetadata.entity();

        logger.warn("Logging verification failure for customer: {}", customer.getCustomerId());

        // Initialize verification info if not present
        if (customer.getVerification() == null) {
            customer.setVerification(new Customer.VerificationInfo());
        }

        Customer.VerificationInfo verification = customer.getVerification();
        
        // Update verification status to failed
        verification.setStatus("FAILED");
        verification.setCompletedAt(LocalDateTime.now());
        
        // Set failure reason (in real implementation, this would come from the provider)
        String failureReason = "Verification failed - insufficient documentation or identity mismatch";
        verification.setFailureReason(failureReason);
        
        // Update provider response with failure details
        Map<String, Object> providerResponse = verification.getProviderResponse();
        if (providerResponse == null) {
            providerResponse = new HashMap<>();
        }
        providerResponse.put("status", "failed");
        providerResponse.put("failureReason", failureReason);
        providerResponse.put("retryAllowed", true);
        providerResponse.put("failedAt", LocalDateTime.now().toString());
        verification.setProviderResponse(providerResponse);

        // Update timestamps
        customer.setUpdatedAt(LocalDateTime.now());

        logger.info("Verification failure logged for customer {} - reason: {}", 
                   customer.getCustomerId(), failureReason);

        return entityWithMetadata;
    }
}
