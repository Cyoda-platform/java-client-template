package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Component
public class LaureateEnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateEnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public LaureateEnrichmentProcessor(SerializerFactory serializerFactory,
                                       EntityService entityService,
                                       ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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
        if (entity == null) return null;

        // 1) Enrichment: compute ageAtAward from born (yyyy-MM-dd) and year (yyyy)
        try {
            String born = entity.getBorn();
            String awardYearStr = entity.getYear();
            if (born != null && !born.isBlank() && awardYearStr != null && !awardYearStr.isBlank()) {
                try {
                    LocalDate birthDate = LocalDate.parse(born, DateTimeFormatter.ISO_LOCAL_DATE);
                    int awardYear = Integer.parseInt(awardYearStr.trim());
                    int age = awardYear - birthDate.getYear();
                    // Only set if non-negative (basic sanity check)
                    if (age >= 0) {
                        entity.setAgeAtAward(age);
                    } else {
                        logger.warn("Computed negative age for laureate id {}: birthYear={}, awardYear={}", entity.getId(), birthDate.getYear(), awardYear);
                        entity.setAgeAtAward(null);
                    }
                } catch (DateTimeParseException | NumberFormatException e) {
                    logger.warn("Unable to compute ageAtAward for laureate id {}: born='{}', year='{}'. Error: {}", entity.getId(), born, awardYearStr, e.getMessage());
                    entity.setAgeAtAward(null);
                }
            } else {
                entity.setAgeAtAward(null);
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while computing ageAtAward for laureate id {}: {}", entity.getId(), ex.getMessage(), ex);
            entity.setAgeAtAward(null);
        }

        // 2) Enrichment: normalize country code
        try {
            // Prefer explicit bornCountryCode when available
            String countryCode = entity.getBornCountryCode();
            if (countryCode != null && !countryCode.isBlank()) {
                entity.setNormalizedCountryCode(countryCode.trim().toUpperCase(Locale.ROOT));
            } else {
                // Fallback: attempt basic normalization from bornCountry name
                String bornCountry = entity.getBornCountry();
                if (bornCountry != null && !bornCountry.isBlank()) {
                    String normalized = normalizeCountryNameToIso(bornCountry.trim());
                    if (normalized != null) {
                        entity.setNormalizedCountryCode(normalized);
                    } else {
                        // unknown mapping -> set null to indicate unknown normalization
                        entity.setNormalizedCountryCode(null);
                        logger.debug("No normalization mapping found for country '{}', laureate id {}", bornCountry, entity.getId());
                    }
                } else {
                    entity.setNormalizedCountryCode(null);
                }
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while normalizing country for laureate id {}: {}", entity.getId(), ex.getMessage(), ex);
            entity.setNormalizedCountryCode(null);
        }

        // Note: do not persist via EntityService for the triggering entity; Cyoda will persist the entity state automatically.
        return entity;
    }

    // Basic heuristic mapping for common country names to ISO-2 codes.
    private String normalizeCountryNameToIso(String countryName) {
        if (countryName == null) return null;
        String key = countryName.trim().toLowerCase(Locale.ROOT);
        switch (key) {
            case "japan":
                return "JP";
            case "united states":
            case "united states of america":
            case "usa":
            case "u.s.":
            case "u.s.a.":
                return "US";
            case "united kingdom":
            case "uk":
            case "great britain":
            case "britain":
                return "GB";
            case "sweden":
                return "SE";
            case "france":
                return "FR";
            case "germany":
            case "federal republic of germany":
                return "DE";
            case "norway":
                return "NO";
            case "denmark":
                return "DK";
            case "china":
            case "people's republic of china":
                return "CN";
            case "canada":
                return "CA";
            case "australia":
                return "AU";
            case "russia":
            case "russian federation":
                return "RU";
            case "netherlands":
            case "holland":
                return "NL";
            case "switzerland":
                return "CH";
            case "italy":
                return "IT";
            default:
                return null;
        }
    }
}