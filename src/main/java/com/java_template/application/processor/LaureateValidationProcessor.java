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
import java.util.Objects;

@Component
public class LaureateValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public LaureateValidationProcessor(SerializerFactory serializerFactory) {
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
        if (entity == null) return false;

        // Basic required fields check from entity implementation
        if (!entity.isValid()) return false;

        // id must be positive
        if (entity.getId() == null || entity.getId() <= 0) return false;

        // year must be a 4-digit integer in a reasonable range
        String yearStr = entity.getYear();
        if (yearStr == null) return false;
        try {
            int y = Integer.parseInt(yearStr.trim());
            if (y < 1800 || y > 2100) return false;
        } catch (NumberFormatException ex) {
            return false;
        }

        // If born is present, it must be a valid ISO date
        String born = entity.getBorn();
        if (born != null && !born.isBlank()) {
            try {
                LocalDate.parse(born.trim());
            } catch (DateTimeException ex) {
                return false;
            }
        }

        // If bornCountryCode present, expect 2-letter country code (alpha)
        String cc = entity.getBornCountryCode();
        if (cc != null && !cc.isBlank()) {
            String trimmed = cc.trim();
            if (trimmed.length() != 2) return false;
            if (!trimmed.chars().allMatch(Character::isLetter)) return false;
        }

        return true;
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();

        if (entity == null) {
            return null;
        }

        // Trim common string fields to normalize input
        if (entity.getFirstname() != null) {
            entity.setFirstname(entity.getFirstname().trim());
        }
        if (entity.getSurname() != null) {
            entity.setSurname(entity.getSurname().trim());
        }
        if (entity.getCategory() != null) {
            entity.setCategory(entity.getCategory().trim());
        }
        if (entity.getYear() != null) {
            entity.setYear(entity.getYear().trim());
        }
        if (entity.getMotivation() != null) {
            entity.setMotivation(entity.getMotivation().trim());
        }
        if (entity.getAffiliationName() != null) {
            entity.setAffiliationName(entity.getAffiliationName().trim());
        }
        if (entity.getAffiliationCity() != null) {
            entity.setAffiliationCity(entity.getAffiliationCity().trim());
        }
        if (entity.getAffiliationCountry() != null) {
            entity.setAffiliationCountry(entity.getAffiliationCountry().trim());
        }
        if (entity.getBorn() != null) {
            entity.setBorn(entity.getBorn().trim());
        }
        if (entity.getBornCity() != null) {
            entity.setBornCity(entity.getBornCity().trim());
        }
        if (entity.getBornCountry() != null) {
            entity.setBornCountry(entity.getBornCountry().trim());
        }
        if (entity.getBornCountryCode() != null) {
            entity.setBornCountryCode(entity.getBornCountryCode().trim().toUpperCase());
        }
        if (entity.getDied() != null) {
            entity.setDied(entity.getDied().trim());
        }
        if (entity.getGender() != null) {
            entity.setGender(entity.getGender().trim());
        }
        if (entity.getNormalizedCountryCode() != null) {
            entity.setNormalizedCountryCode(entity.getNormalizedCountryCode().trim());
        }
        if (entity.getIngestJobId() != null) {
            entity.setIngestJobId(entity.getIngestJobId().trim());
        }

        // Minimal enrichment helpful for downstream processors:
        // If born and year are present and parseable, compute derivedAgeAtAward
        try {
            if (entity.getBorn() != null && !entity.getBorn().isBlank() && entity.getYear() != null && !entity.getYear().isBlank()) {
                LocalDate bornDate = LocalDate.parse(entity.getBorn());
                int awardYear = Integer.parseInt(entity.getYear());
                int age = awardYear - bornDate.getYear();
                // Guard against negative or unreasonable ages
                if (age >= 0 && age <= 200) {
                    entity.setDerivedAgeAtAward(age);
                } else {
                    // if unreasonable, clear derived value to allow enrichment to decide later
                    entity.setDerivedAgeAtAward(null);
                }
            }
        } catch (Exception ex) {
            // If parsing fails, do not set derivedAgeAtAward here; leave for enrichment
            entity.setDerivedAgeAtAward(null);
            logger.debug("Could not compute derivedAgeAtAward for laureate id {}: {}", entity.getId(), ex.getMessage());
        }

        // Ensure normalized country code follows 2-letter uppercase if bornCountryCode is valid
        if (entity.getBornCountryCode() != null && entity.getBornCountryCode().length() == 2) {
            entity.setNormalizedCountryCode(entity.getBornCountryCode().toUpperCase());
        } else {
            // leave normalizedCountryCode untouched if no good code present
            if (entity.getNormalizedCountryCode() == null) {
                entity.setNormalizedCountryCode(null);
            }
        }

        // Validation processor should not perform persistence operations on this entity;
        // modifications here will be persisted by Cyoda automatically as part of the workflow.

        return entity;
    }
}