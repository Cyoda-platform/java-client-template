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

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Entity is null")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate entity) {
        // Only ensure entity object exists here; detailed validation handled in processEntityLogic
        return entity != null;
    }

    private ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> noopContext(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> ctx) {
        return ctx;
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();

        if (entity == null) {
            logger.warn("Laureate entity is null in processing context");
            return entity;
        }

        // Basic normalization: trim commonly-used string fields if present
        if (entity.getFullName() != null) {
            String trimmed = entity.getFullName().trim();
            entity.setFullName(trimmed.isEmpty() ? null : trimmed);
        }
        if (entity.getCategory() != null) {
            String trimmed = entity.getCategory().trim();
            entity.setCategory(trimmed.isEmpty() ? null : trimmed);
        }
        if (entity.getYear() != null) {
            String trimmed = entity.getYear().trim();
            entity.setYear(trimmed.isEmpty() ? null : trimmed);
        }

        // Business rule: required minimal fields for validation stage
        boolean missingName = entity.getFullName() == null || entity.getFullName().isBlank();
        boolean missingCategory = entity.getCategory() == null || entity.getCategory().isBlank();

        if (missingName || missingCategory) {
            // If required fields missing, mark as REJECTED
            logger.warn("Laureate missing required fields (missingFullName: {}, missingCategory: {}). Marking as REJECTED. id={}",
                    missingName, missingCategory, entity.getId());
            entity.setStatus("REJECTED");
            return entity;
        }

        // Passed basic validation -> mark as VALIDATED
        logger.info("Laureate passed validation. Marking as VALIDATED. id={}", entity.getId());
        entity.setStatus("VALIDATED");

        return entity;
    }
}