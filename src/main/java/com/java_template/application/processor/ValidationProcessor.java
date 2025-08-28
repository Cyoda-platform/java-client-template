package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
public class ValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Laureate.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
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

        // Basic structural validation provided by entity
        if (!entity.isValid()) return false;

        // Validate born date if present (expecting ISO local date yyyy-MM-dd)
        String born = entity.getBorn();
        if (born != null && !born.isBlank()) {
            if (!isParsableLocalDate(born)) return false;
        }

        // Validate died date if present (expecting ISO local date yyyy-MM-dd)
        String died = entity.getDied();
        if (died != null && !died.isBlank()) {
            if (!isParsableLocalDate(died)) return false;
        }

        // Validate year (award year) is a 4-digit year and logical relative to born date if present
        String yearStr = entity.getYear();
        if (yearStr == null || yearStr.isBlank()) return false;
        int awardYear;
        try {
            awardYear = Integer.parseInt(yearStr);
            if (awardYear < 1000 || awardYear > 9999) return false;
        } catch (NumberFormatException ex) {
            return false;
        }

        if (born != null && !born.isBlank()) {
            try {
                LocalDate bornDate = LocalDate.parse(born);
                if (bornDate.getYear() > awardYear) {
                    // Award year cannot be before birth year
                    return false;
                }
            } catch (DateTimeParseException ex) {
                return false;
            }
        }

        if (died != null && !died.isBlank() && born != null && !born.isBlank()) {
            try {
                LocalDate bornDate = LocalDate.parse(born);
                LocalDate diedDate = LocalDate.parse(died);
                if (diedDate.isBefore(bornDate)) {
                    // Died date cannot be before birth date
                    return false;
                }
            } catch (DateTimeParseException ex) {
                return false;
            }
        }

        // All validation checks passed
        return true;
    }

    private boolean isParsableLocalDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();

        // Update lastUpdatedAt to current timestamp
        try {
            entity.setLastUpdatedAt(Instant.now().toString());
        } catch (Exception ex) {
            logger.warn("Failed to set lastUpdatedAt for Laureate id={}: {}", entity.getId(), ex.getMessage());
        }

        // Normalize bornCountryCode into normalizedCountryCode if present
        try {
            String bornCountryCode = entity.getBornCountryCode();
            if (bornCountryCode != null && !bornCountryCode.isBlank()) {
                entity.setNormalizedCountryCode(bornCountryCode.trim().toUpperCase());
            }
        } catch (Exception ex) {
            logger.warn("Failed to normalize country code for Laureate id={}: {}", entity.getId(), ex.getMessage());
        }

        // If ageAtAward not provided, attempt to compute from born year and award year
        try {
            if (entity.getAgeAtAward() == null) {
                String born = entity.getBorn();
                String yearStr = entity.getYear();
                if (born != null && !born.isBlank() && yearStr != null && !yearStr.isBlank()) {
                    LocalDate bornDate = LocalDate.parse(born);
                    int awardYear = Integer.parseInt(yearStr);
                    int computedAge = awardYear - bornDate.getYear();
                    if (computedAge >= 0) {
                        entity.setAgeAtAward(computedAge);
                    }
                }
            }
        } catch (DateTimeException | NumberFormatException ex) {
            logger.debug("Could not compute ageAtAward for Laureate id={}: {}", entity.getId(), ex.getMessage());
        }

        // Leave further enrichment and persistence decisions to downstream processors
        return entity;
    }
}