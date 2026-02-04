package com.example.application.processor;

import com.example.application.entity.customer.version_1.Customer;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Customer Verification Processor
 * Handles customer verification workflow transitions
 */
@Slf4j
@Component
public class CustomerVerificationProcessor implements CyodaProcessor {

    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public CustomerVerificationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        log.info("Processing customer verification for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Customer.class)
                .validate(this::isValidEntityWithMetadata, "Invalid customer entity")
                .map(this::processVerification)
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

    private EntityWithMetadata<Customer> processVerification(EntityWithMetadata<Customer> entityWithMetadata) {
        Customer customer = entityWithMetadata.entity();
        
        log.debug("Verifying customer: {} with email: {}", customer.getCustomerId(), customer.getEmail());
        
        // Update customer status to VERIFIED
        customer.setStatus(Customer.CustomerStatus.VERIFIED);
        
        // Update KYC status to VERIFIED
        if (customer.getKyc() != null) {
            customer.getKyc().setKycStatus(Customer.KYCStatus.VERIFIED);
        }
        
        // Update timestamp
        customer.setUpdatedAt(LocalDateTime.now());
        
        log.info("Customer {} verified successfully", customer.getCustomerId());
        
        return entityWithMetadata;
    }
}

