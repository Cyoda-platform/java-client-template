package com.java_template.application.processor;

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

/**
 * ValidatePayload Processor - KYC Onboarding Workflow
 * 
 * Validates the KYC payload structure and required fields.
 * Ensures customer data is complete and properly formatted before proceeding.
 */
@Component
public class ValidatePayload implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidatePayload.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidatePayload(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Validating KYC payload for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Customer.class)
                .validate(this::isValidEntityWithMetadata, "Invalid KYC payload structure")
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

        logger.debug("Validating customer: {} with legal name: {}", customer.getId(), customer.getLegalName());

        // Validate required fields
        validateRequiredFields(customer);

        // Validate email format
        if (customer.getPrimaryEmail() != null && !customer.getPrimaryEmail().isEmpty()) {
            if (!isValidEmail(customer.getPrimaryEmail())) {
                logger.warn("Invalid email format for customer: {}", customer.getId());
            }
        }

        // Validate addresses if present
        if (customer.getAddresses() != null && !customer.getAddresses().isEmpty()) {
            customer.getAddresses().forEach(this::validateAddress);
        }

        logger.info("KYC payload validation completed for customer: {}", customer.getId());
        return entityWithMetadata;
    }

    private void validateRequiredFields(Customer customer) {
        if (customer.getId() == null || customer.getId().isBlank()) {
            logger.error("Customer ID is required");
        }
        if (customer.getLegalName() == null || customer.getLegalName().isBlank()) {
            logger.error("Legal name is required");
        }
        if (customer.getCustomerType() == null) {
            logger.error("Customer type is required");
        }
    }

    private void validateAddress(Customer.Address address) {
        if (address.getCountry() == null || address.getCountry().isBlank()) {
            logger.warn("Address country is missing");
        }
        if (address.getCity() == null || address.getCity().isBlank()) {
            logger.warn("Address city is missing");
        }
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }
}

