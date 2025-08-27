package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Component
public class MarkForSummaryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarkForSummaryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public MarkForSummaryProcessor(SerializerFactory serializerFactory) {
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

        // Mark the laureate as included in job summary by updating relevant metadata fields.
        // Do not perform persistence calls on this entity; Cyoda will persist changes automatically.

        // 1) Update lastSeenAt to current timestamp
        try {
            entity.setLastSeenAt(Instant.now().toString());
        } catch (Exception ex) {
            logger.warn("Failed to set lastSeenAt for Laureate id={}: {}", entity != null ? entity.getId() : null, ex.getMessage());
        }

        // 2) Ensure ageAtAward is calculated if missing and data available
        if (entity.getAgeAtAward() == null) {
            String born = entity.getBorn();
            String awardYear = entity.getYear();
            if (born != null && !born.isBlank() && awardYear != null && !awardYear.isBlank()) {
                try {
                    // born expected format "YYYY-.." -> take first 4 chars as year if possible
                    int birthYear = Integer.parseInt(born.substring(0, 4));
                    int y = Integer.parseInt(awardYear.trim());
                    int computedAge = y - birthYear;
                    if (computedAge >= 0 && computedAge <= 150) {
                        entity.setAgeAtAward(computedAge);
                    } else {
                        logger.debug("Computed age out of range for Laureate id={}: {} (birth={}, award={})",
                                entity.getId(), computedAge, birthYear, y);
                    }
                } catch (Exception e) {
                    logger.debug("Unable to compute ageAtAward for Laureate id={}: {}", entity.getId(), e.getMessage());
                }
            }
        }

        // 3) Normalize country code if missing
        if (entity.getNormalizedCountryCode() == null || entity.getNormalizedCountryCode().isBlank()) {
            String code = entity.getBornCountryCode();
            if (code != null && !code.isBlank()) {
                try {
                    entity.setNormalizedCountryCode(code.trim().toUpperCase());
                } catch (Exception e) {
                    logger.debug("Unable to set normalizedCountryCode for Laureate id={}: {}", entity.getId(), e.getMessage());
                }
            }
        }

        // 4) Do not change validationStatus here — MarkForSummary should operate for both VALID and INVALID entities.
        // Additional summary-related logic may be added later (e.g., tagging NEW vs UPDATED) when an explicit field exists.

        return entity;
    }
}