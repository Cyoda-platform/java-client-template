package com.java_template.application.processor;

import com.java_template.application.entity.address.version_1.Address;
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

@Component
public class AddressValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AddressValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AddressValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AddressValidation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Address.class)
            .validate(this::isValidEntity, "Invalid address payload")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Address address) {
        return address != null && address.isValid();
    }

    private Address processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Address> context) {
        Address address = context.entity();
        // Additional address normalization could go here (e.g., uppercase country)
        if (address.getCountry() != null) {
            address.setCountry(address.getCountry().toUpperCase());
        }
        logger.info("Address {} validated for user {}", address.getAddressId(), address.getUserId());
        return address;
    }
}
