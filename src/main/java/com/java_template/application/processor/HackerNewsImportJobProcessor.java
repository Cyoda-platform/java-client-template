package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.HackerNewsItem;
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
public class HackerNewsImportJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final String className = this.getClass().getSimpleName();

    public HackerNewsImportJobProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HackerNewsItem for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(HackerNewsItem.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HackerNewsItem entity) {
        return entity != null && entity.isValid();
    }

    private HackerNewsItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HackerNewsItem> context) {
        HackerNewsItem entity = context.entity();

        // Business logic based on functional requirements:
        // 1. Validate presence of id and type already done
        // 2. Set state based on validation

        if (entity.getId() != null && entity.getType() != null && !entity.getType().isBlank()) {
            entity.setState("VALID");
        } else {
            entity.setState("INVALID");
        }

        return entity;
    }

}
