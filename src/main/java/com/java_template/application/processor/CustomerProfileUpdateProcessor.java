package com.java_template.application.processor;

import com.java_template.application.entity.CustomerProfileUpdate;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CustomerProfileUpdateProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CustomerProfileUpdateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CustomerProfileUpdate for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CustomerProfileUpdate.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CustomerProfileUpdate entity) {
        return entity != null && entity.isValid();
    }

    private CustomerProfileUpdate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CustomerProfileUpdate> context) {
        CustomerProfileUpdate entity = context.entity();
        String technicalId = context.request().getEntityId();
        logger.info("Processing CustomerProfileUpdate: {}", technicalId);

        if (entity.getUpdatedFields() == null || entity.getUpdatedFields().isEmpty()) {
            logger.error("Updated fields are empty for CustomerProfileUpdate: {}", technicalId);
            return entity;
        }

        // Apply changes as immutable event (logging here)
        logger.info("Applied profile updates for customerId: {}", entity.getCustomerId());

        // Confirmation logic can be added here if needed

        return entity;
    }
}
