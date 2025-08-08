package com.java_template.application.processor;

import com.java_template.application.entity.Laureate;
import com.cyoda.plugins.mapping.entity.CyodaEntity;
import com.cyoda.plugins.mapping.entity.EntityProcessorCalculationRequest;
import com.cyoda.plugins.mapping.entity.EntityProcessorCalculationResponse;
import com.cyoda.plugins.mapping.entity.CyodaEventContext;
import com.cyoda.plugins.mapping.entity.OperationSpecification;
import com.cyoda.plugins.mapping.entity.CyodaProcessor;
import com.cyoda.plugins.mapping.entity.ProcessorSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class LaureateValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateValidationProcessor.class);

    @Autowired
    private ProcessorSerializer serializer;

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid Laureate entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "LaureateValidationProcessor".equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate laureate) {
        if (laureate == null) {
            return false;
        }
        if (laureate.getLaureateId() == null || laureate.getLaureateId() <= 0) {
            return false;
        }
        if (laureate.getFirstname() == null || laureate.getFirstname().trim().isEmpty()) {
            return false;
        }
        if (laureate.getSurname() == null || laureate.getSurname().trim().isEmpty()) {
            return false;
        }
        if (laureate.getYear() == null || laureate.getYear().trim().isEmpty()) {
            return false;
        }
        if (laureate.getCategory() == null || laureate.getCategory().trim().isEmpty()) {
            return false;
        }
        return true;
    }

    private Laureate processEntityLogic(Laureate laureate) {
        // No state change, just validation
        return laureate;
    }
}
