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
 * Processor to send verification request to external verification provider
 * 
 * This processor is triggered during the onboarding -> verification_pending transition.
 * It simulates sending a verification request to an external identity verification service
 * and updates the customer's verification information.
 */
@Component
public class sendVerificationRequest implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(sendVerificationRequest.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public sendVerificationRequest(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Customer.class)
                .validate(this::isValidEntityWithMetadata, "Invalid customer entity wrapper")
                .map(this::processVerificationRequest)
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
     * Main business logic for sending verification request
     */
    private EntityWithMetadata<Customer> processVerificationRequest(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Customer> context) {

        EntityWithMetadata<Customer> entityWithMetadata = context.entityResponse();
        Customer customer = entityWithMetadata.entity();

        logger.info("Sending verification request for customer: {}", customer.getCustomerId());

        // Initialize verification info if not present
        if (customer.getVerification() == null) {
            customer.setVerification(new Customer.VerificationInfo());
        }

        Customer.VerificationInfo verification = customer.getVerification();
        
        // Simulate sending request to external verification provider
        String requestId = "VER-" + UUID.randomUUID().toString().substring(0, 8);
        
        verification.setStatus("PENDING");
        verification.setProvider("identity_verification_service");
        verification.setRequestId(requestId);
        verification.setRequestedAt(LocalDateTime.now());
        verification.setFailureReason(null);
        
        // Simulate provider response (in real implementation, this would be async)
        Map<String, Object> providerResponse = new HashMap<>();
        providerResponse.put("requestId", requestId);
        providerResponse.put("status", "submitted");
        providerResponse.put("estimatedCompletionTime", "2-5 minutes");
        verification.setProviderResponse(providerResponse);

        // Update timestamps
        customer.setUpdatedAt(LocalDateTime.now());

        logger.info("Verification request sent for customer {} with request ID: {}", 
                   customer.getCustomerId(), requestId);

        return entityWithMetadata;
    }
}
