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

    private boolean isValidEntity(Laureate entity) {
        // Required fields: laureateId, firstname, surname, year, category
        if (entity == null) return false;
        if (entity.getLaureateId() == null) return false;
        if (entity.getFirstname() == null || entity.getFirstname().isEmpty()) return false;
        if (entity.getSurname() == null || entity.getSurname().isEmpty()) return false;
        if (entity.getYear() == null || entity.getYear().isEmpty()) return false;
        if (entity.getCategory() == null || entity.getCategory().isEmpty()) return false;
        return true;
    }

    private Laureate processEntityLogic(Laureate entity) {
        // Additional validation or preprocessing can be done here
        logger.info("Validated Laureate: {} {}", entity.getFirstname(), entity.getSurname());
        return entity;
    }
}
