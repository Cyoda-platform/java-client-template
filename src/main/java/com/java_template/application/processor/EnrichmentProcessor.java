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

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Component
public class EnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public EnrichmentProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        if (entity == null) {
            return null;
        }

        // Compute derived_ageAtAward if possible
        try {
            String born = entity.getBorn();
            String awardYearStr = entity.getYear();
            if (born != null && !born.isBlank() && awardYearStr != null && !awardYearStr.isBlank()) {
                try {
                    int awardYear = Integer.parseInt(awardYearStr.trim());
                    // Attempt to parse born as ISO date (yyyy-MM-dd). Fallback: extract leading 4-digit year.
                    Integer birthYear = null;
                    try {
                        LocalDate bornDate = LocalDate.parse(born.trim());
                        birthYear = bornDate.getYear();
                    } catch (DateTimeParseException dte) {
                        // fallback: extract first 4 digits if possible
                        String maybeYear = born.trim();
                        if (maybeYear.length() >= 4) {
                            String yearPart = maybeYear.substring(0, 4);
                            try {
                                birthYear = Integer.parseInt(yearPart);
                            } catch (NumberFormatException nfe) {
                                birthYear = null;
                            }
                        }
                    }
                    if (birthYear != null) {
                        int ageAtAward = awardYear - birthYear;
                        if (ageAtAward >= 0) {
                            entity.setDerived_ageAtAward(ageAtAward);
                        } else {
                            // negative age not sensible; leave null
                            entity.setDerived_ageAtAward(null);
                            logger.warn("Computed negative ageAtAward for laureate id {} (awardYear={} birthYear={})", entity.getId(), awardYear, birthYear);
                        }
                    } else {
                        logger.debug("Could not determine birth year for laureate id {} from born='{}'", entity.getId(), born);
                    }
                } catch (NumberFormatException nfe) {
                    logger.debug("Invalid award year '{}' for laureate id {}", awardYearStr, entity.getId());
                }
            }
        } catch (Exception ex) {
            logger.error("Error computing derived_ageAtAward for laureate id {}: {}", entity.getId(), ex.getMessage(), ex);
        }

        // Normalize country code: prefer borncountrycode, otherwise try to resolve from borncountry
        try {
            String countryCode = entity.getBorncountrycode();
            if (countryCode != null && !countryCode.isBlank()) {
                entity.setNormalizedCountryCode(countryCode.trim().toUpperCase());
            } else {
                String countryName = entity.getBorncountry();
                if (countryName != null && !countryName.isBlank()) {
                    String normalized = null;
                    String candidate = countryName.trim();
                    // Try direct match against ISO country display names (English)
                    for (String iso : Locale.getISOCountries()) {
                        Locale loc = new Locale("", iso);
                        String display = loc.getDisplayCountry(Locale.ENGLISH);
                        if (display != null && !display.isBlank()) {
                            if (display.equalsIgnoreCase(candidate) || display.replaceAll("\\s+", "").equalsIgnoreCase(candidate.replaceAll("\\s+", ""))) {
                                normalized = iso.toUpperCase();
                                break;
                            }
                        }
                    }
                    if (normalized == null) {
                        // Try matching by common names: some countries might be given in native language or short forms.
                        // Basic heuristics: compare startsWith/contains
                        for (String iso : Locale.getISOCountries()) {
                            Locale loc = new Locale("", iso);
                            String display = loc.getDisplayCountry(Locale.ENGLISH);
                            if (display != null && !display.isBlank()) {
                                if (display.toLowerCase().contains(candidate.toLowerCase()) || candidate.toLowerCase().contains(display.toLowerCase())) {
                                    normalized = iso.toUpperCase();
                                    break;
                                }
                            }
                        }
                    }
                    if (normalized != null) {
                        entity.setNormalizedCountryCode(normalized);
                    } else {
                        // If unable to resolve, leave null and log for diagnostics
                        entity.setNormalizedCountryCode(null);
                        logger.debug("Unable to normalize country code for laureate id {} from borncountry='{}'", entity.getId(), countryName);
                    }
                } else {
                    entity.setNormalizedCountryCode(null);
                }
            }
        } catch (Exception ex) {
            logger.error("Error normalizing country code for laureate id {}: {}", entity.getId(), ex.getMessage(), ex);
        }

        return entity;
    }
}