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

import java.time.DateTimeException;
import java.time.LocalDate;

@Component
public class LaureateEnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateEnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public LaureateEnrichmentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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

        // Derive age at award when possible:
        // - born is expected as ISO date string (yyyy-MM-dd) or at least begins with year
        // - year is expected as award year string (e.g., "2010")
        try {
            String born = entity.getBorn();
            String awardYearStr = entity.getYear();

            Integer derivedAge = null;
            if (born != null && !born.isBlank() && awardYearStr != null && !awardYearStr.isBlank()) {
                // extract year from born. born may be "1930-09-12" or just "1930"
                int bornYear = -1;
                try {
                    if (born.length() >= 4) {
                        String bornYearPart = born.substring(0, 4);
                        bornYear = Integer.parseInt(bornYearPart);
                    }
                } catch (NumberFormatException ignored) {
                    bornYear = -1;
                }

                int awardYear = -1;
                try {
                    awardYear = Integer.parseInt(awardYearStr.trim());
                } catch (NumberFormatException ignored) {
                    awardYear = -1;
                }

                if (bornYear > 0 && awardYear > 0) {
                    int age = awardYear - bornYear;
                    // only set sensible ages (e.g., between 0 and 150). Otherwise leave null.
                    if (age >= 0 && age <= 150) {
                        derivedAge = age;
                    }
                }
            }
            entity.setDerivedAgeAtAward(derivedAge);
        } catch (Exception ex) {
            // In case of unexpected error, do not throw — log and leave derivedAgeAtAward null
            logger.warn("Failed to compute derived age for laureate id={}: {}", entity.getId(), ex.getMessage());
            entity.setDerivedAgeAtAward(null);
        }

        // Normalize country code:
        try {
            String countryCode = entity.getBornCountryCode();
            if (countryCode != null) {
                String normalized = countryCode.trim().toUpperCase();
                if (normalized.isEmpty()) {
                    entity.setNormalizedCountryCode(null);
                } else if (normalized.length() == 2) {
                    entity.setNormalizedCountryCode(normalized);
                } else if (normalized.length() > 2) {
                    // If longer code provided, try to take first two characters as a fallback
                    entity.setNormalizedCountryCode(normalized.substring(0, 2));
                } else {
                    entity.setNormalizedCountryCode(null);
                }
            } else {
                entity.setNormalizedCountryCode(null);
            }
        } catch (Exception ex) {
            logger.warn("Failed to normalize country code for laureate id={}: {}", entity.getId(), ex.getMessage());
            entity.setNormalizedCountryCode(null);
        }

        // Additional light cleanup: ensure strings are trimmed where applicable (use existing setters/getters)
        try {
            if (entity.getFirstname() != null) entity.setFirstname(entity.getFirstname().trim());
            if (entity.getSurname() != null) entity.setSurname(entity.getSurname().trim());
            if (entity.getCategory() != null) entity.setCategory(entity.getCategory().trim());
            if (entity.getYear() != null) entity.setYear(entity.getYear().trim());
            if (entity.getBornCountry() != null) entity.setBornCountry(entity.getBornCountry().trim());
            if (entity.getAffiliationName() != null) entity.setAffiliationName(entity.getAffiliationName().trim());
            if (entity.getAffiliationCity() != null) entity.setAffiliationCity(entity.getAffiliationCity().trim());
            if (entity.getAffiliationCountry() != null) entity.setAffiliationCountry(entity.getAffiliationCountry().trim());
        } catch (Exception ex) {
            logger.debug("Minor cleanup failed for laureate id={}: {}", entity.getId(), ex.getMessage());
        }

        return entity;
    }
}