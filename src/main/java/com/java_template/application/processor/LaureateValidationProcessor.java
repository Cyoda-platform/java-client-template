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

import java.util.ArrayList;

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

        // Ensure affiliations is not null so downstream processors can rely on a list
        if (entity.getAffiliations() == null) {
            entity.setAffiliations(new ArrayList<>());
        }

        // Normalize country (trim) if present
        if (entity.getCountry() != null) {
            entity.setCountry(entity.getCountry().trim());
        }

        // Determine published state based on changeType:
        // - If changeType indicates deletion, ensure not published.
        // - If new or updated, mark published so notification/enqueue processors can act.
        // - Otherwise, preserve existing published flag (or leave null).
        String changeType = entity.getChangeType();
        if (changeType != null) {
            String ct = changeType.trim().toLowerCase();
            if ("deleted".equals(ct) || "remove".equals(ct)) {
                entity.setPublished(Boolean.FALSE);
            } else if ("new".equals(ct) || "created".equals(ct) || "updated".equals(ct) || "modified".equals(ct)) {
                entity.setPublished(Boolean.TRUE);
            }
        }

        logger.debug("Laureate ({}) validated. changeType={}, published={}", entity.getLaureateId(), entity.getChangeType(), entity.getPublished());

        return entity;
    }
}