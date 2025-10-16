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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * ABOUTME: Processor that handles customer account deletion,
 * marking customer accounts for deletion and anonymizing personal data.
 */
@Component
public class CustomerDeletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CustomerDeletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CustomerDeletionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing customer deletion for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Customer.class)
                .validate(this::isValidEntityWithMetadata, "Invalid customer entity wrapper")
                .map(this::processEntityWithMetadataLogic)
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
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();
        return customer != null && customer.isValid(entityWithMetadata.metadata()) && technicalId != null && 
               ("active".equals(currentState) || "inactive".equals(currentState) || "suspended".equals(currentState));
    }

    /**
     * Main business logic for customer deletion
     */
    private EntityWithMetadata<Customer> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Customer> context) {

        EntityWithMetadata<Customer> entityWithMetadata = context.entityResponse();
        Customer customer = entityWithMetadata.entity();

        logger.debug("Deleting customer: {} with ID: {}", customer.getUsername(), customer.getCustomerId());

        // Update deletion timestamp
        customer.setUpdatedAt(LocalDateTime.now());

        // Anonymize personal data for privacy compliance
        customer.setFirstName("DELETED");
        customer.setLastName("DELETED");
        customer.setEmail("deleted@example.com");
        customer.setPhone(null);
        customer.setAddress(null);
        customer.setDateOfBirth(null);
        customer.setPassword(null);

        logger.info("Customer {} deleted successfully with ID: {}", customer.getUsername(), customer.getCustomerId());

        return entityWithMetadata;
    }
}
