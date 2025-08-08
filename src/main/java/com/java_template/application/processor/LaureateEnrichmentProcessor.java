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
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;

@Component
public class LaureateEnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateEnrichmentProcessor.class);

    @Autowired
    private ProcessorSerializer serializer;

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate enrichment for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid Laureate entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "LaureateEnrichmentProcessor".equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate laureate) {
        return laureate != null;
    }

    private Laureate processEntityLogic(Laureate laureate) {
        // Normalize country code to uppercase
        if (laureate.getBorncountrycode() != null) {
            String normalizedCode = laureate.getBorncountrycode().toUpperCase();
            laureate.setBorncountrycode(normalizedCode);
            logger.info("Normalized borncountrycode to: {}", normalizedCode);
        }
        // Calculate age at award if born and year are valid
        try {
            if (laureate.getBorn() != null && laureate.getYear() != null && !laureate.getYear().trim().isEmpty()) {
                int birthYear = laureate.getBorn().getYear();
                int awardYear = Integer.parseInt(laureate.getYear());
                int ageAtAward = awardYear - birthYear;
                // Store or log age if needed; assuming no field to store, just logging
                logger.info("Calculated age at award: {}", ageAtAward);
            }
        } catch (Exception e) {
            logger.warn("Failed to calculate age at award: {}", e.getMessage());
        }
        return laureate;
    }
}
