package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
public class PersistLaureateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistLaureateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PersistLaureateProcessor(SerializerFactory serializerFactory) {
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

        // Trim string fields to remove accidental whitespace
        if (entity.getFirstname() != null) {
            entity.setFirstname(entity.getFirstname().trim());
        }
        if (entity.getSurname() != null) {
            entity.setSurname(entity.getSurname().trim());
        }
        if (entity.getCategory() != null) {
            entity.setCategory(entity.getCategory().trim());
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
            entity.setBornCountryCode(entity.getBornCountryCode().trim());
        }
        if (entity.getGender() != null) {
            entity.setGender(entity.getGender().trim().toLowerCase());
        }
        if (entity.getYear() != null) {
            entity.setYear(entity.getYear().trim());
        }
        if (entity.getDied() != null) {
            entity.setDied(entity.getDied().trim());
        }

        // Compute derivedAgeAtAward if possible and not already set
        if (entity.getDerivedAgeAtAward() == null) {
            String born = entity.getBorn();
            String yearStr = entity.getYear();
            if (born != null && !born.isBlank() && yearStr != null && !yearStr.isBlank()) {
                try {
                    LocalDate birthDate = LocalDate.parse(born);
                    int awardYear = Integer.parseInt(yearStr.trim());
                    int age = awardYear - birthDate.getYear();
                    if (age >= 0) {
                        entity.setDerivedAgeAtAward(age);
                    } else {
                        logger.debug("Computed negative age for laureate id {}: birthYear={} awardYear={}", entity.getId(), birthDate.getYear(), awardYear);
                    }
                } catch (DateTimeParseException | NumberFormatException e) {
                    logger.debug("Unable to compute derived age for laureate id {}: born='{}' year='{}' - {}", entity.getId(), born, yearStr, e.getMessage());
                }
            }
        }

        // Normalize country code: prefer bornCountryCode if provided; ensure uppercase
        if ((entity.getNormalizedCountryCode() == null || entity.getNormalizedCountryCode().isBlank())
                && entity.getBornCountryCode() != null && !entity.getBornCountryCode().isBlank()) {
            entity.setNormalizedCountryCode(entity.getBornCountryCode().trim().toUpperCase());
        } else if (entity.getNormalizedCountryCode() != null) {
            entity.setNormalizedCountryCode(entity.getNormalizedCountryCode().trim().toUpperCase());
        }

        // Ensure ingestJobId, if present, is trimmed (no validation here)
        if (entity.getIngestJobId() != null) {
            entity.setIngestJobId(entity.getIngestJobId().trim());
        }

        // Final logging for traceability
        logger.info("PersistLaureateProcessor completed processing laureate id={}; derivedAgeAtAward={}, normalizedCountryCode={}",
                entity.getId(), entity.getDerivedAgeAtAward(), entity.getNormalizedCountryCode());

        return entity;
    }
}