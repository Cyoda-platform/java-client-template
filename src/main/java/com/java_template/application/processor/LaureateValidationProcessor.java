package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

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
        return entity != null && entity.isValid();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();

        // Trim name fields to normalize minor formatting issues
        if (entity.getFirstname() != null) {
            entity.setFirstname(entity.getFirstname().trim());
        }
        if (entity.getSurname() != null) {
            entity.setSurname(entity.getSurname().trim());
        }

        // Validate born date format and compute age at award year if possible
        String born = entity.getBorn();
        if (born != null && !born.isBlank()) {
            try {
                LocalDate bornDate = LocalDate.parse(born);
                // If year is present, compute age
                String yearStr = entity.getYear();
                if (yearStr != null && !yearStr.isBlank()) {
                    try {
                        int awardYear = Integer.parseInt(yearStr.trim());
                        int age = awardYear - bornDate.getYear();
                        if (age >= 0) {
                            entity.setComputedAge(age);
                        } else {
                            // Negative age: treat as invalid birth/year combination
                            logger.warn("Computed negative age for laureate id={}, born={}, year={}. Clearing computedAge.",
                                    entity.getId(), born, yearStr);
                            entity.setComputedAge(null);
                            // Fail validation by throwing to mark entity invalid in workflow
                            throw new IllegalArgumentException("INVALID_BORN_YEAR_COMBINATION");
                        }
                    } catch (NumberFormatException nfe) {
                        logger.warn("Invalid year format for laureate id={}: {}", entity.getId(), yearStr);
                        // Year format invalid -> cannot compute age, but keep entity (year is required and should have been validated earlier)
                        entity.setComputedAge(null);
                        throw new IllegalArgumentException("INVALID_YEAR_FORMAT");
                    }
                }
            } catch (DateTimeParseException dtpe) {
                logger.warn("Invalid born date for laureate id={}: {}", entity.getId(), born);
                // Born date invalid -> mark entity invalid by throwing exception
                throw new IllegalArgumentException("INVALID_BORN_DATE");
            }
        } else {
            // born missing is allowed by entity.isValid(), but we cannot compute age
            entity.setComputedAge(null);
        }

        // Normalize country code if missing but country present
        if ((entity.getBorncountrycode() == null || entity.getBorncountrycode().isBlank())
                && entity.getBorncountry() != null && !entity.getBorncountry().isBlank()) {
            String country = entity.getBorncountry().trim();
            String mapped = mapCountryToIso(country);
            if (mapped != null) {
                entity.setBorncountrycode(mapped);
            }
        }

        // All checks passed; return mutated entity (will be persisted by workflow)
        return entity;
    }

    // Basic heuristic mapping for a few common country names -> ISO codes.
    // Keep conservative: return null when unknown to avoid overwriting valid codes.
    private String mapCountryToIso(String country) {
        if (country == null) return null;
        String c = country.trim().toLowerCase();
        switch (c) {
            case "japan":
                return "JP";
            case "united states":
            case "united states of america":
            case "usa":
            case "us":
                return "US";
            case "united kingdom":
            case "uk":
            case "great britain":
            case "britain":
                return "GB";
            case "germany":
                return "DE";
            case "sweden":
                return "SE";
            case "france":
                return "FR";
            case "norway":
                return "NO";
            case "denmark":
                return "DK";
            case "switzerland":
                return "CH";
            case "russia":
            case "russian federation":
                return "RU";
            case "china":
            case "people's republic of china":
                return "CN";
            default:
                return null;
        }
    }
}