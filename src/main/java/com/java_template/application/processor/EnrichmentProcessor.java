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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

@Component
public class EnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Map<String, String> COUNTRY_NAME_TO_CODE = new HashMap<>();

    static {
        // basic mappings - extendable
        COUNTRY_NAME_TO_CODE.put("japan", "JP");
        COUNTRY_NAME_TO_CODE.put("united states", "US");
        COUNTRY_NAME_TO_CODE.put("united states of america", "US");
        COUNTRY_NAME_TO_CODE.put("sweden", "SE");
        COUNTRY_NAME_TO_CODE.put("germany", "DE");
        COUNTRY_NAME_TO_CODE.put("france", "FR");
        COUNTRY_NAME_TO_CODE.put("united kingdom", "GB");
        COUNTRY_NAME_TO_CODE.put("uk", "GB");
        COUNTRY_NAME_TO_CODE.put("russia", "RU");
        COUNTRY_NAME_TO_CODE.put("china", "CN");
        COUNTRY_NAME_TO_CODE.put("norway", "NO");
        COUNTRY_NAME_TO_CODE.put("denmark", "DK");
        COUNTRY_NAME_TO_CODE.put("italy", "IT");
        COUNTRY_NAME_TO_CODE.put("spain", "ES");
    }

    public EnrichmentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
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
        if (entity == null) {
            logger.warn("Received null Laureate entity in enrichment processor");
            return null;
        }

        // 1. Compute ageAtAward from born (yyyy-MM-dd) and year
        try {
            String born = entity.getBorn();
            String yearStr = entity.getYear();
            if (born != null && !born.isBlank() && yearStr != null && !yearStr.isBlank()) {
                try {
                    LocalDate birthDate = LocalDate.parse(born, ISO_DATE);
                    int awardYear = extractYear(yearStr);
                    if (awardYear >= birthDate.getYear()) {
                        int computedAge = awardYear - birthDate.getYear();
                        // Basic sanity check
                        if (computedAge >= 0 && computedAge <= 150) {
                            entity.setAgeAtAward(computedAge);
                        } else {
                            logger.debug("Computed age is out of expected range (0-150): {} for entity id {}", computedAge, entity.getId());
                            entity.setAgeAtAward(null);
                        }
                    } else {
                        logger.debug("Award year {} is before birth year {} for entity id {}", awardYear, birthDate.getYear(), entity.getId());
                        entity.setAgeAtAward(null);
                    }
                } catch (DateTimeParseException dte) {
                    logger.debug("Failed to parse birth date '{}' for entity id {}: {}", entity.getBorn(), entity.getId(), dte.getMessage());
                    entity.setAgeAtAward(null);
                }
            } else {
                // missing data - leave ageAtAward as is (null)
                entity.setAgeAtAward(null);
            }
        } catch (Exception e) {
            logger.error("Unexpected error computing ageAtAward for laureate id {}: {}", entity.getId(), e.getMessage(), e);
            entity.setAgeAtAward(null);
        }

        // 2. Normalize country code
        try {
            String bornCountryCode = entity.getBornCountryCode();
            if (bornCountryCode != null && !bornCountryCode.isBlank()) {
                entity.setNormalizedCountryCode(bornCountryCode.trim().toUpperCase());
            } else {
                String bornCountry = entity.getBornCountry();
                if (bornCountry != null && !bornCountry.isBlank()) {
                    String key = bornCountry.trim().toLowerCase();
                    String mapped = COUNTRY_NAME_TO_CODE.get(key);
                    if (mapped != null) {
                        entity.setNormalizedCountryCode(mapped);
                    } else {
                        // fallback: try to infer by taking first two letters uppercase (best-effort)
                        String fallback = inferFallbackCountryCode(bornCountry);
                        entity.setNormalizedCountryCode(fallback);
                    }
                } else {
                    entity.setNormalizedCountryCode(null);
                }
            }
        } catch (Exception e) {
            logger.error("Unexpected error normalizing country code for laureate id {}: {}", entity.getId(), e.getMessage(), e);
            entity.setNormalizedCountryCode(null);
        }

        // No other entity modifications here. Persisting of this entity is handled by workflow.
        return entity;
    }

    private int extractYear(String yearStr) {
        // Attempts to extract a 4-digit year at start of the string
        if (yearStr == null) throw new IllegalArgumentException("yearStr is null");
        String trimmed = yearStr.trim();
        if (trimmed.length() >= 4) {
            String candidate = trimmed.substring(0, 4);
            try {
                return Integer.parseInt(candidate);
            } catch (NumberFormatException ignored) {
                // fallback: try to parse full string
            }
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ex) {
            // As a last resort, extract digits
            String digits = trimmed.replaceAll("\\D+", "");
            if (digits.length() >= 4) {
                return Integer.parseInt(digits.substring(0, 4));
            }
            throw new IllegalArgumentException("Cannot extract year from: " + yearStr);
        }
    }

    private String inferFallbackCountryCode(String countryName) {
        if (countryName == null || countryName.isBlank()) return null;
        String cleaned = countryName.trim();
        // Use first two letters as a best-effort fallback, uppercase
        String onlyLetters = cleaned.replaceAll("[^A-Za-z]", "");
        if (onlyLetters.length() >= 2) {
            return onlyLetters.substring(0, 2).toUpperCase();
        } else if (onlyLetters.length() == 1) {
            return (onlyLetters.substring(0,1) + "X").toUpperCase();
        } else {
            return null;
        }
    }
}