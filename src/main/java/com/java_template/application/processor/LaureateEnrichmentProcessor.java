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
import java.util.Collections;

@Component
public class LaureateEnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateEnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final Map<String, String> COUNTRY_NAME_TO_CODE_MAP;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("japan", "JP");
        m.put("united states", "US");
        m.put("united states of america", "US");
        m.put("usa", "US");
        m.put("uk", "GB");
        m.put("united kingdom", "GB");
        m.put("germany", "DE");
        m.put("france", "FR");
        m.put("sweden", "SE");
        m.put("switzerland", "CH");
        m.put("russia", "RU");
        m.put("poland", "PL");
        m.put("netherlands", "NL");
        m.put("canada", "CA");
        m.put("australia", "AU");
        m.put("china", "CN");
        m.put("italy", "IT");
        m.put("spain", "ES");
        m.put("denmark", "DK");
        m.put("norway", "NO");
        COUNTRY_NAME_TO_CODE_MAP = Collections.unmodifiableMap(m);
    }

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

        // The existing serializer usage chain:
        // - toEntity: map the incoming payload to Laureate
        // - validate: ensure entity meets basic contract (uses entity.isValid())
        // - map: perform enrichment logic
        // - complete: produce response
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

        if (entity == null) return null;

        // Compute age at award year if possible
        String born = null;
        String yearStr = null;
        try {
            born = entity.getBorn();
            yearStr = entity.getYear();
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
                if (normalized.length() == 2 && normalized.matches("[A-Z]{2}")) {
                    entity.setBorncountrycode(normalized);
                } else if (normalized.length() > 2) {
                    // fallback: take first two alphabetic characters
                    String two = normalized.replaceAll("[^A-Z]", "");
                    if (two.length() >= 2) {
                        entity.setBorncountrycode(two.substring(0,2));
                    } else {
                        entity.setBorncountrycode(normalized);
                    }
                } else {
                    entity.setBorncountrycode(normalized);
                }
            } else {
                // Derive from borncountry if possible using mapping
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

        // Return enriched entity. Cyoda persistence will handle saving the updated entity.
        return entity;
    }

    // Simple mapping for common countries; can be expanded later.
    private String deriveCountryCodeFromName(String countryName) {
        if (countryName == null) return null;
        String key = countryName.trim().toLowerCase();
        String found = COUNTRY_NAME_TO_CODE_MAP.get(key);
        if (found != null) return found;

        // Try simple heuristics: if countryName looks like a two-letter code already
        String upper = countryName.toUpperCase();
        if (upper.length() == 2 && upper.matches("[A-Z]{2}")) {
            return upper;
        }

        // Try to normalize common variants (remove punctuation/extra words)
        String simplified = key.replaceAll("[^a-z ]", "").trim();
        found = COUNTRY_NAME_TO_CODE_MAP.get(simplified);
        if (found != null) return found;

        return null;
    }
}