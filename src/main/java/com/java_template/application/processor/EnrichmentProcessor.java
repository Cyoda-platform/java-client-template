package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

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
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate entity) {
        return entity != null && entity.isValid();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();

        // Enrichment logic:
        // 1. Compute ageAtAward from born (ISO date) and year (award year as string)
        try {
            String born = entity.getBorn();
            String yearStr = entity.getYear();
            if (born != null && !born.isBlank() && yearStr != null && !yearStr.isBlank()) {
                try {
                    LocalDate bornDate = LocalDate.parse(born);
                    int awardYear = Integer.parseInt(yearStr);
                    Integer age = awardYear - bornDate.getYear();
                    entity.setAgeAtAward(age);
                } catch (DateTimeParseException | NumberFormatException ex) {
                    logger.debug("Could not compute ageAtAward for laureate id {}: {}", entity.getId(), ex.getMessage());
                    entity.setAgeAtAward(null);
                }
            } else {
                entity.setAgeAtAward(null);
            }
        } catch (Exception ex) {
            logger.warn("Unexpected error computing ageAtAward for laureate id {}: {}", entity.getId(), ex.getMessage());
            entity.setAgeAtAward(null);
        }

        // 2. Normalize country code: prefer bornCountryCode if present otherwise try to resolve from bornCountry using Locale
        try {
            String bornCountryCode = entity.getBornCountryCode();
            if (bornCountryCode != null && !bornCountryCode.isBlank()) {
                entity.setNormalizedCountryCode(bornCountryCode.trim().toUpperCase());
            } else {
                String bornCountry = entity.getBornCountry();
                String resolved = null;
                if (bornCountry != null && !bornCountry.isBlank()) {
                    String countryTrim = bornCountry.trim();
                    for (String iso : Locale.getISOCountries()) {
                        Locale loc = new Locale("", iso);
                        if (loc.getDisplayCountry().equalsIgnoreCase(countryTrim)) {
                            resolved = iso;
                            break;
                        }
                    }
                }
                entity.setNormalizedCountryCode(resolved);
            }
        } catch (Exception ex) {
            logger.debug("Could not normalize country for laureate id {}: {}", entity.getId(), ex.getMessage());
            entity.setNormalizedCountryCode(null);
        }

        // 3. Update lastSeenAt to current timestamp
        try {
            entity.setLastSeenAt(Instant.now().toString());
        } catch (Exception ex) {
            logger.debug("Could not set lastSeenAt for laureate id {}: {}", entity.getId(), ex.getMessage());
        }

        return entity;
    }
}