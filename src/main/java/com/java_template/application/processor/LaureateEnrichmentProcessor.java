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
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;

@Component
public class LaureateEnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateEnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
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

        // Compute age at award year if possible
        try {
            String born = entity.getBorn();
            String yearStr = entity.getYear();
            if (born != null && !born.isBlank() && yearStr != null && !yearStr.isBlank()) {
                try {
                    int awardYear = Integer.parseInt(yearStr.trim());
                    try {
                        LocalDate birthDate = LocalDate.parse(born, DateTimeFormatter.ISO_DATE);
                        // Use end of award year (Dec 31) to compute age during award year
                        LocalDate awardDate = LocalDate.of(awardYear, 12, 31);
                        int age = Period.between(birthDate, awardDate).getYears();
                        if (age >= 0) {
                            entity.setComputedAge(age);
                        } else {
                            logger.warn("Computed negative age for laureate id={}, born={}, year={}", entity.getId(), born, yearStr);
                            entity.setComputedAge(null);
                        }
                    } catch (DateTimeParseException dtpe) {
                        logger.warn("Unable to parse born date '{}' for laureate id={}: {}", born, entity.getId(), dtpe.getMessage());
                        // leave computedAge as-is (null or existing)
                    }
                } catch (NumberFormatException nfe) {
                    logger.warn("Unable to parse award year '{}' for laureate id={}: {}", yearStr, entity.getId(), nfe.getMessage());
                }
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while computing age for laureate id={}: {}", entity != null ? entity.getId() : null, ex.getMessage(), ex);
        }

        // Normalize / derive borncountrycode
        try {
            String code = entity.getBorncountrycode();
            if (code != null && !code.isBlank()) {
                // Normalize to upper-case ISO-2 if looks valid
                String normalized = code.trim().toUpperCase();
                if (normalized.length() == 2) {
                    entity.setBorncountrycode(normalized);
                } else {
                    // If not 2 letters, attempt to take first two letters uppercased as fallback
                    if (normalized.length() > 2) {
                        entity.setBorncountrycode(normalized.substring(0,2));
                    } else {
                        entity.setBorncountrycode(normalized);
                    }
                }
            } else {
                // Derive from borncountry if possible using a small mapping
                String country = entity.getBorncountry();
                if (country != null && !country.isBlank()) {
                    String derived = deriveCountryCodeFromName(country.trim());
                    if (derived != null) {
                        entity.setBorncountrycode(derived);
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while normalizing country code for laureate id={}: {}", entity != null ? entity.getId() : null, ex.getMessage(), ex);
        }

        // All enrichment complete; return the entity. Cyoda will persist changes automatically.
        return entity;
    }

    // Simple mapping for common countries; can be expanded later.
    private String deriveCountryCodeFromName(String countryName) {
        if (countryName == null) return null;
        String key = countryName.trim().toLowerCase();
        Map<String, String> map = new HashMap<>();
        map.put("japan", "JP");
        map.put("united states", "US");
        map.put("united states of america", "US");
        map.put("usa", "US");
        map.put("uk", "GB");
        map.put("united kingdom", "GB");
        map.put("germany", "DE");
        map.put("france", "FR");
        map.put("sweden", "SE");
        map.put("switzerland", "CH");
        map.put("russia", "RU");
        map.put("poland", "PL");
        map.put("netherlands", "NL");
        map.put("canada", "CA");
        map.put("australia", "AU");
        map.put("china", "CN");
        map.put("italy", "IT");
        map.put("spain", "ES");
        map.put("denmark", "DK");
        map.put("norway", "NO");

        String found = map.get(key);
        if (found != null) return found;

        // Try simple heuristics: if countryName looks like a two-letter code already
        String upper = countryName.toUpperCase();
        if (upper.length() == 2 && upper.matches("[A-Z]{2}")) {
            return upper;
        }
        return null;
    }
}