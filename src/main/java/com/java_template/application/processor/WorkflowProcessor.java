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
public class WorkflowProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public WorkflowProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("WorkflowProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Customer for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
                .toEntity(Customer.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "WorkflowProcessor".equals(modelSpec.operationName()) &&
                "customer".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(Customer customer) {
        return customer.isValid();
    }

    private Customer processEntityLogic(Customer customer) {
        try {
            logger.info("Processing Customer with customerId: {}", customer.getCustomerId());
            if (!customer.getEmail().contains("@")) {
                logger.error("Customer email format invalid: {}", customer.getEmail());
                return customer;
            }
            customer.setName(customer.getName().trim());
            customer.setPhone(customer.getPhone().trim());
            logger.info("Customer profile enriched for customerId: {}", customer.getCustomerId());
        } catch (Exception e) {
            logger.error("Error processing customer", e);
        }
        return customer;
    }
}
