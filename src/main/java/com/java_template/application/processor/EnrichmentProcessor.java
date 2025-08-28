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

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
public class EnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public EnrichmentProcessor(SerializerFactory serializerFactory,
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
        try {
            // 1) Enrich ageAtAward: compute from born (ISO date) and year (award year)
            Integer age = computeAgeAtAward(entity);
            if (age != null) {
                entity.setAgeAtAward(age);
            }

            // 2) Normalize country code: prefer bornCountryCode if present
            String normalized = normalizeCountryCode(entity);
            if (normalized != null) {
                entity.setNormalizedCountryCode(normalized);
            }

            // 3) Update lastUpdatedAt to current timestamp (ISO-8601)
            entity.setLastUpdatedAt(Instant.now().toString());

        } catch (Exception ex) {
            // Ensure processor does not throw; log and attach minimal error info via context if available
            logger.error("Error while enriching Laureate id={}: {}", entity != null ? entity.getId() : null, ex.getMessage(), ex);
        }

        return entity;
    }

    private Integer computeAgeAtAward(Laureate entity) {
        if (entity == null) return null;
        String born = entity.getBorn();
        String yearStr = entity.getYear();
        if (born == null || born.isBlank() || yearStr == null || yearStr.isBlank()) return null;

        try {
            Integer birthYear = null;
            try {
                // Try parsing born as ISO_LOCAL_DATE
                LocalDate bornDate = LocalDate.parse(born);
                birthYear = bornDate.getYear();
            } catch (DateTimeParseException dx) {
                // Fallback: try to extract first 4 digits
                String digits = born.replaceAll("[^0-9]", "");
                if (digits.length() >= 4) {
                    birthYear = Integer.parseInt(digits.substring(0, 4));
                }
            }

            if (birthYear == null) return null;

            // award year may contain non-digits (e.g., "2010"); extract leading digits
            String yearDigits = yearStr.replaceAll("[^0-9]", "");
            if (yearDigits.isBlank()) return null;
            Integer awardYear = Integer.parseInt(yearDigits.substring(0, Math.min(4, yearDigits.length())));

            int age = awardYear - birthYear;
            if (age < 0) return null;
            return age;
        } catch (Exception e) {
            logger.debug("Failed to compute age for laureate id={}, born='{}', year='{}': {}", entity.getId(), born, yearStr, e.getMessage());
            return null;
        }
    }

    private String normalizeCountryCode(Laureate entity) {
        if (entity == null) return null;
        String code = entity.getBornCountryCode();
        if (code != null && !code.isBlank()) {
            String cleaned = code.trim().toUpperCase();
            // basic validation: expect 2-letter ISO codes
            if (cleaned.length() == 2) return cleaned;
            // if longer, try last two chars (best-effort)
            if (cleaned.length() > 2) return cleaned.substring(cleaned.length() - 2);
            return cleaned;
        }
        // No bornCountryCode provided: cannot reliably infer from bornCountry here
        return null;
    }
}