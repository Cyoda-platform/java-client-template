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
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Component
public class ValidationEnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidationEnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidationEnrichmentProcessor(SerializerFactory serializerFactory) {
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

        if (entity == null) return null;

        // Validation: check date formats for born and died (ISO date yyyy-MM-dd)
        // and normalize/compute enrichment fields (age, normalizedCountryCode, gender normalization)
        // Year format check (simple 4-digit)
        // These operations modify the entity state; persistence is handled by Cyoda.

        // Normalize gender to lowercase if present
        try {
            if (entity.getGender() != null && !entity.getGender().isBlank()) {
                entity.setGender(entity.getGender().trim().toLowerCase());
            }
        } catch (Exception e) {
            logger.warn("Failed to normalize gender for laureate id {}: {}", entity.getId(), e.getMessage());
        }

        // Validate and compute age from born/died
        Integer computedAge = null;
        String bornStr = entity.getBorn();
        if (bornStr != null && !bornStr.isBlank()) {
            try {
                LocalDate born = LocalDate.parse(bornStr);
                LocalDate toDate = null;
                if (entity.getDied() != null && !entity.getDied().isBlank()) {
                    try {
                        toDate = LocalDate.parse(entity.getDied());
                    } catch (DateTimeParseException de) {
                        logger.warn("Invalid died date format for laureate id {}: {}", entity.getId(), entity.getDied());
                        toDate = null;
                    }
                }
                if (toDate == null) {
                    toDate = LocalDate.now(ZoneOffset.UTC);
                }
                if (!toDate.isBefore(born)) {
                    computedAge = Period.between(born, toDate).getYears();
                    entity.setAge(computedAge);
                } else {
                    logger.warn("Died date is before born date for laureate id {}: born={}, died={}", entity.getId(), entity.getBorn(), entity.getDied());
                }
            } catch (DateTimeParseException e) {
                logger.warn("Invalid born date format for laureate id {}: {}", entity.getId(), entity.getBorn());
            }
        } else {
            // born missing or blank; age cannot be computed
            entity.setAge(null);
        }

        // Normalize / derive country code
        String normalized = null;
        String countryCode = entity.getBornCountryCode();
        if (countryCode != null && !countryCode.isBlank()) {
            normalized = countryCode.trim().toUpperCase();
            // If longer than 3 chars, attempt to take first 2 (best-effort) - but keep full code otherwise
            if (normalized.length() > 3) {
                normalized = normalized.substring(0, Math.min(3, normalized.length()));
            }
        } else {
            // Try to derive from bornCountry using Locale lookups (best-effort)
            String bornCountry = entity.getBornCountry();
            if (bornCountry != null && !bornCountry.isBlank()) {
                String target = bornCountry.trim().toLowerCase();
                String found = null;
                for (String iso : Locale.getISOCountries()) {
                    Locale l = new Locale("", iso);
                    String display = l.getDisplayCountry(Locale.ENGLISH);
                    if (display == null) continue;
                    String dispLower = display.toLowerCase();
                    if (dispLower.equals(target) || dispLower.contains(target) || target.contains(dispLower)) {
                        found = iso;
                        break;
                    }
                }
                if (found != null) {
                    normalized = found.toUpperCase();
                }
            }
        }
        if (normalized != null) {
            entity.setNormalizedCountryCode(normalized);
        } else {
            // leave as null if cannot determine
            entity.setNormalizedCountryCode(null);
        }

        // Year format validation (expecting 4-digit year)
        try {
            if (entity.getYear() != null && !entity.getYear().isBlank()) {
                String y = entity.getYear().trim();
                if (!y.matches("^\\d{4}$")) {
                    logger.warn("Laureate id {} has non-standard year format: {}", entity.getId(), entity.getYear());
                    // Do not alter the year field, but log for downstream processors/criteria
                } else {
                    entity.setYear(y);
                }
            }
        } catch (Exception e) {
            logger.warn("Error validating year for laureate id {}: {}", entity.getId(), e.getMessage());
        }

        // Ensure affiliation fields are trimmed if present
        try {
            if (entity.getAffiliationName() != null) entity.setAffiliationName(entity.getAffiliationName().trim());
            if (entity.getAffiliationCity() != null) entity.setAffiliationCity(entity.getAffiliationCity().trim());
            if (entity.getAffiliationCountry() != null) entity.setAffiliationCountry(entity.getAffiliationCountry().trim());
        } catch (Exception e) {
            logger.debug("Error trimming affiliation fields for laureate id {}: {}", entity.getId(), e.getMessage());
        }

        // Additional minor enrichment: trim textual fields commonly used
        try {
            if (entity.getFirstname() != null) entity.setFirstname(entity.getFirstname().trim());
            if (entity.getSurname() != null) entity.setSurname(entity.getSurname().trim());
            if (entity.getMotivation() != null) entity.setMotivation(entity.getMotivation().trim());
            if (entity.getBornCity() != null) entity.setBornCity(entity.getBornCity().trim());
            if (entity.getBornCountry() != null) entity.setBornCountry(entity.getBornCountry().trim());
        } catch (Exception e) {
            logger.debug("Error trimming textual fields for laureate id {}: {}", entity.getId(), e.getMessage());
        }

        return entity;
    }
}