package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ValidateLaureateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateLaureateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateLaureateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

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
        // Allow processing as long as entity object exists. Detailed validation happens in processEntityLogic.
        return entity != null;
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();
        if (entity == null) return null;

        List<String> errors = new ArrayList<>();

        // Required basic fields
        if (entity.getLaureateId() == null || entity.getLaureateId().isBlank()) {
            errors.add("laureateId is missing or blank");
        }
        if (entity.getAwardYear() == null || entity.getAwardYear().isBlank()) {
            errors.add("awardYear is missing or blank");
        } else {
            // optional: basic numeric year validation (4 digits)
            String y = entity.getAwardYear().trim();
            if (!y.matches("\\d{4}")) {
                errors.add("awardYear has invalid format, expected YYYY");
            }
        }
        if (entity.getCategory() == null || entity.getCategory().isBlank()) {
            errors.add("category is missing or blank");
        }

        // Provenance checks
        if (entity.getProvenance() == null) {
            errors.add("provenance is missing");
        } else {
            if (entity.getProvenance().getIngestionJobId() == null || entity.getProvenance().getIngestionJobId().isBlank()) {
                errors.add("provenance.ingestionJobId is missing or blank");
            }
            if (entity.getProvenance().getSourceRecordId() == null || entity.getProvenance().getSourceRecordId().isBlank()) {
                errors.add("provenance.sourceRecordId is missing or blank");
            }
            if (entity.getProvenance().getSourceTimestamp() == null || entity.getProvenance().getSourceTimestamp().isBlank()) {
                errors.add("provenance.sourceTimestamp is missing or blank");
            }
        }

        // Optional: ensure ageAtAward is non-negative if present
        if (entity.getAgeAtAward() != null && entity.getAgeAtAward() < 0) {
            errors.add("ageAtAward must not be negative");
        }

        // Populate validationErrors and set processingStatus based on results
        entity.getValidationErrors().clear();
        if (!errors.isEmpty()) {
            entity.getValidationErrors().addAll(errors);
            entity.setProcessingStatus("REJECTED");
            logger.info("Laureate {} rejected with {} validation error(s)", entity.getLaureateId(), errors.size());
        } else {
            entity.setProcessingStatus("VALIDATED");
            logger.info("Laureate {} validated successfully", entity.getLaureateId());
        }

        return entity;
    }
}