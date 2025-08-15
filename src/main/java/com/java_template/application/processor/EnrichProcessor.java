package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

@Component
public class EnrichProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate enrichment for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid laureate state for enrichment")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate laureate) {
        return laureate != null && laureate.isValid();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate laureate = context.entity();
        Map<String,String> validations = laureate.getValidations();
        if (validations == null) validations = new HashMap<>();

        try {
            // Compute ageAtAward if born present and year is numeric
            if (laureate.getBorn() != null && !laureate.getBorn().isBlank() && laureate.getYear() != null && !laureate.getYear().isBlank()) {
                try {
                    LocalDate born = LocalDate.parse(laureate.getBorn());
                    int awardYear = Integer.parseInt(laureate.getYear());
                    int age = awardYear - born.getYear();
                    laureate.setAgeAtAward(age);
                } catch (DateTimeParseException | NumberFormatException e) {
                    logger.debug("Unable to compute ageAtAward for laureate id={}: {}", laureate.getId(), e.getMessage());
                    validations.put("ageAtAward", "Unable to compute age: " + e.getMessage());
                }
            }

            // Normalize country code to upper-case if present
            if (laureate.getBorncountrycode() != null && !laureate.getBorncountrycode().isBlank()) {
                laureate.setNormalizedCountryCode(laureate.getBorncountrycode().toUpperCase());
            }

            // Status progression if currently VALIDATED
            if (laureate.getStatus() != null && "VALIDATED".equalsIgnoreCase(laureate.getStatus())) {
                laureate.setStatus("ENRICHED");
            }

        } catch (Exception e) {
            logger.error("Unexpected error during enrichment for laureate id={}: {}", laureate.getId(), e.getMessage(), e);
            validations.put("enrichment", e.getMessage());
            // Do not invent fields on Laureate; store validation message and preserve status
        }

        laureate.setValidations(validations);
        return laureate;
    }
}
