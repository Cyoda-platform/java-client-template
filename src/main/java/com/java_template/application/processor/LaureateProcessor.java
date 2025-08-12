package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class LaureateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateProcessor.class);
    private final ObjectMapper objectMapper;
    private final String className = this.getClass().getSimpleName();

    public LaureateProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        Laureate laureate = context.getEntity(Laureate.class);
        if (laureate == null) {
            logger.error("Laureate entity is null");
            return EntityProcessorCalculationResponse.failure("Laureate entity is null");
        }

        // Validate laureate
        boolean isValid = validateLaureate(laureate);
        if (!isValid) {
            logger.error("Laureate validation failed for id: {}", laureate.getLaureateId());
            return EntityProcessorCalculationResponse.failure("Laureate validation failed");
        }

        // Enrich laureate data
        enrichLaureate(laureate);

        // Normally here you would persist or update the laureate entity state
        logger.info("Laureate processed successfully: id {}", laureate.getLaureateId());

        return EntityProcessorCalculationResponse.success();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean validateLaureate(Laureate laureate) {
        // Basic validation according to functional requirements
        if (laureate.getLaureateId() == null || laureate.getLaureateId().isBlank()) return false;
        if (laureate.getFirstname() == null || laureate.getFirstname().isBlank()) return false;
        if (laureate.getSurname() == null || laureate.getSurname().isBlank()) return false;
        if (laureate.getBorn() == null || laureate.getBorn().isBlank()) return false;
        if (laureate.getBorncountry() == null || laureate.getBorncountry().isBlank()) return false;
        if (laureate.getBorncountrycode() == null || laureate.getBorncountrycode().isBlank()) return false;
        if (laureate.getBorncity() == null || laureate.getBorncity().isBlank()) return false;
        if (laureate.getGender() == null || laureate.getGender().isBlank()) return false;
        if (laureate.getYear() == null || laureate.getYear().isBlank()) return false;
        if (laureate.getCategory() == null || laureate.getCategory().isBlank()) return false;
        if (laureate.getMotivation() == null || laureate.getMotivation().isBlank()) return false;
        if (laureate.getName() == null || laureate.getName().isBlank()) return false;
        if (laureate.getCity() == null || laureate.getCity().isBlank()) return false;
        if (laureate.getCountry() == null || laureate.getCountry().isBlank()) return false;
        return true;
    }

    private void enrichLaureate(Laureate laureate) {
        // Normalize country code to uppercase
        if (laureate.getBorncountrycode() != null) {
            laureate.setBorncountrycode(laureate.getBorncountrycode().toUpperCase(Locale.ROOT));
        }
        // Additional enrichment can be added here
    }
}
