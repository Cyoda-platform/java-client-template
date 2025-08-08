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

    private boolean isValidEntity(Laureate entity) {
        return entity != null && entity.getBorncountrycode() != null && !entity.getBorncountrycode().isEmpty();
    }

    private Laureate processEntityLogic(Laureate entity) {
        // Normalize country code to uppercase
        String countryCode = entity.getBorncountrycode();
        if (countryCode != null) {
            entity.setBorncountrycode(countryCode.toUpperCase());
        }

        // Calculate age at award if born and year are present
        try {
            if (entity.getBorn() != null && entity.getYear() != null && !entity.getYear().isEmpty()) {
                LocalDate bornDate = LocalDate.parse(entity.getBorn());
                int awardYear = Integer.parseInt(entity.getYear());
                int ageAtAward = awardYear - bornDate.getYear();
                // Assuming Laureate has a setAgeAtAward property (not in original, so skip storing)
                logger.info("Calculated age at award for {} {}: {}", entity.getFirstname(), entity.getSurname(), ageAtAward);
            }
        } catch (Exception e) {
            logger.warn("Failed to calculate age at award for laureate {} {}", entity.getFirstname(), entity.getSurname());
        }

        return entity;
    }
}
