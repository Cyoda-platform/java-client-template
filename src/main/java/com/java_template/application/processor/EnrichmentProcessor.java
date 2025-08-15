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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Component
public class EnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichmentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate enrichment for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid laureate entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate laureate) {
        return laureate != null && laureate.getId() != null;
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate laureate = context.entity();

        // Only run enrichment if validation passed
        if (laureate.getDataValidated() == null || !laureate.getDataValidated()) {
            logger.info("Skipping enrichment for laureate {} because dataValidated is false", laureate.getId());
            laureate.setDataEnriched(false);
            laureate.setEnrichmentErrors("Skipped due to failed validation");
            return laureate;
        }

        try {
            // Compute ageAtAward if born and year are available
            if (laureate.getBorn() != null && !laureate.getBorn().isEmpty() && laureate.getYear() != null && !laureate.getYear().isEmpty()) {
                try {
                    LocalDate bornDate = LocalDate.parse(laureate.getBorn(), DateTimeFormatter.ISO_DATE);
                    int awardYear = Integer.parseInt(laureate.getYear());
                    LocalDate awardDate = LocalDate.of(awardYear, 1, 1);
                    long age = ChronoUnit.YEARS.between(bornDate, awardDate);
                    laureate.setAgeAtAward((int) age);
                } catch (Exception e) {
                    logger.warn("Unable to compute ageAtAward for laureate {}: {}", laureate.getId(), e.getMessage());
                    laureate.setAgeAtAward(null);
                }
            }

            // Normalize country code: prefer provided borncountrycode, otherwise try to derive from borncountry
            if (laureate.getBorncountrycode() != null && !laureate.getBorncountrycode().isEmpty()) {
                laureate.setNormalizedCountryCode(laureate.getBorncountrycode().toUpperCase());
            } else if (laureate.getBorncountry() != null && !laureate.getBorncountry().isEmpty()) {
                // Very naive normalization: take first two letters uppercased
                String c = laureate.getBorncountry();
                if (c.length() >= 2) {
                    laureate.setNormalizedCountryCode(c.substring(0,2).toUpperCase());
                }
            }

            laureate.setDataEnriched(true);
            laureate.setEnrichmentErrors(null);
            logger.info("Enrichment completed for laureate {}", laureate.getId());

        } catch (Exception e) {
            logger.error("Enrichment failed for laureate {}: {}", laureate.getId(), e.getMessage(), e);
            laureate.setDataEnriched(false);
            laureate.setEnrichmentErrors(e.toString());
        }

        return laureate;
    }
}
