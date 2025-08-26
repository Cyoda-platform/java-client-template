package com.java_template.application.processor;
import com.java_template.application.entity.hnitem.version_1.HNItem;
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
import java.util.List;

@Component
public class ValidateRequiredFieldsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateRequiredFieldsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateRequiredFieldsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HNItem for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(HNItem.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HNItem entity) {
        // Basic pre-check: entity must exist and have originalJson to inspect.
        return entity != null && entity.getOriginalJson() != null && !entity.getOriginalJson().isBlank();
    }

    private HNItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HNItem> context) {
        HNItem entity = context.entity();

        // Validate required HN fields: id and type
        List<String> missingFields = new ArrayList<>();
        if (entity.getId() == null || entity.getId() <= 0) {
            missingFields.add("id");
        }
        if (entity.getType() == null || entity.getType().isBlank()) {
            missingFields.add("type");
        }

        if (!missingFields.isEmpty()) {
            // Mark entity as failed due to missing required fields.
            entity.setStatus("FAILED");
            logger.warn("HNItem validation failed for technicalId={} missingFields={}", context.request().getEntityId(), missingFields);
        } else {
            // Validation passed
            entity.setStatus("VALIDATED");
            logger.info("HNItem validation passed for technicalId={}", context.request().getEntityId());
        }

        return entity;
    }
}