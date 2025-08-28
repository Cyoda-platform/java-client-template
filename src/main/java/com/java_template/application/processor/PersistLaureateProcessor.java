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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;

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
            return null;
        }

        try {
            // Normalize textual fields (trim) if present
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

            // Ensure normalizedCountryCode is set (prefer existing normalized, else use bornCountryCode)
            String normalized = entity.getNormalizedCountryCode();
            String bornCode = entity.getBornCountryCode();
            if ((normalized == null || normalized.isBlank()) && bornCode != null && !bornCode.isBlank()) {
                entity.setNormalizedCountryCode(bornCode.trim().toUpperCase());
                logger.debug("Set normalizedCountryCode from bornCountryCode for laureate id={}: {}", entity.getId(), entity.getNormalizedCountryCode());
            } else if (normalized != null && !normalized.isBlank()) {
                entity.setNormalizedCountryCode(normalized.trim().toUpperCase());
            }

            // Compute ageAtAward if missing and if born and year available
            if (entity.getAgeAtAward() == null) {
                String born = entity.getBorn();
                String yearStr = entity.getYear();
                if (born != null && !born.isBlank() && yearStr != null && !yearStr.isBlank()) {
                    try {
                        LocalDate bornDate = LocalDate.parse(born);
                        int awardYear = Integer.parseInt(yearStr.trim());
                        int age = awardYear - bornDate.getYear();
                        if (age >= 0) {
                            entity.setAgeAtAward(age);
                            logger.debug("Computed ageAtAward={} for laureate id={}", age, entity.getId());
                        } else {
                            logger.warn("Computed negative age for laureate id={} (born={}, year={}), leaving ageAtAward null", entity.getId(), born, yearStr);
                        }
                    } catch (DateTimeParseException | NumberFormatException ex) {
                        logger.warn("Failed to compute ageAtAward for laureate id={} due to parse error: {}", entity.getId(), ex.getMessage());
                        // leave ageAtAward null if parsing fails
                    }
                }
            }

            // Ensure lastUpdatedAt set to now (ISO-8601)
            entity.setLastUpdatedAt(Instant.now().toString());

            // Make sure optional fields that might have alternate names are normalized:
            // bornCountry vs bornCountryCode naming in entity: we preserve existing values only.
            // If affiliationName missing but affiliationCity or affiliationCountry present, keep as-is (no create).
            // No add/update/delete of the triggering entity beyond changing its fields (persistence handled by Cyoda).

            logger.info("PersistLaureateProcessor completed processing for laureate id={}", entity.getId());
        } catch (Exception ex) {
            // Catch unexpected exceptions to avoid failing the whole workflow here.
            logger.error("Unexpected error while processing Laureate id={}: {}", entity.getId(), ex.getMessage(), ex);
            // annotate error into entity's sourceSnapshot if possible, but do not throw
            String prevSnapshot = entity.getSourceSnapshot();
            String marker = (prevSnapshot == null ? "" : prevSnapshot + " ");
            entity.setSourceSnapshot(marker + "{\"persistError\":\"" + ex.getMessage().replace("\"", "'") + "\"}");
            // update lastUpdatedAt anyway
            entity.setLastUpdatedAt(Instant.now().toString());
        }

        return entity;
    }
}