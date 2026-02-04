package com.java_template.application.processor;

import com.example.application.entity.customer.version_1.Customer;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import lombok.extern.slf4j.Slf4j;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Customer Suspension Processor
 * Handles customer suspension workflow transitions
 */
@Slf4j
@Component
public class CustomerSuspensionProcessor implements CyodaProcessor {

    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public CustomerSuspensionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        log.info("Processing customer suspension for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Customer.class)
                .validate(this::isValidEntityWithMetadata, "Invalid customer entity")
                .map(this::processSuspension)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Customer> entityWithMetadata) {
        Customer customer = entityWithMetadata.entity();
        return customer != null &&
               customer.getCustomerId() != null &&
               customer.getEmail() != null && !customer.getEmail().isBlank();
    }

    private EntityWithMetadata<Customer> processSuspension(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Customer> context) {
        EntityWithMetadata<Customer> entityWithMetadata = context.entityResponse();
        Customer customer = entityWithMetadata.entity();

        log.debug("Suspending customer: {} with email: {}", customer.getCustomerId(), customer.getEmail());

        // Update customer status to SUSPENDED
        customer.setStatus(Customer.CustomerStatus.SUSPENDED);

        // Mark as soft deleted
        customer.setSoftDeleted(true);

        // Update timestamp
        customer.setUpdatedAt(LocalDateTime.now());

        log.info("Customer {} suspended successfully", customer.getCustomerId());

        return entityWithMetadata;
    }
}

