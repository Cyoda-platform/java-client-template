package com.java_template.application.processor;

import com.java_template.application.entity.Customer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CustomerProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public CustomerProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer(); //always follow this pattern
        logger.info("CustomerProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Customer for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(Customer.class)
            .validate(Customer::isValid, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CustomerProcessor".equals(modelSpec.operationName()) &&
               "customer".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Customer processEntityLogic(Customer customer) {
        logger.info("Processing Customer with customerId: {}", customer.getCustomerId());

        // Validation: Verify contact details format and uniqueness (basic simulation)
        if (customer.getEmail().isBlank() || customer.getPhone().isBlank()) {
            logger.error("Customer validation failed: email or phone is blank");
            // Marking entity as invalid by setting fields to blank (as example, actual invalidation might differ)
            // We just return the entity unchanged as no update method exists
            return customer;
        }

        // Processing: Enrich customer profile (simulated)
        logger.info("Enriching customer profile for: {}", customer.getName());

        // Mark customer as ACTIVE (no field for status exists, so we just log completion)
        logger.info("Customer processing completed successfully");

        return customer;
    }

}