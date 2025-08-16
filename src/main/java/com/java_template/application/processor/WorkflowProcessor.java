package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.Workflow;
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
public class WorkflowProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final String className = this.getClass().getSimpleName();

    public WorkflowProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Workflow for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Workflow.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Workflow entity) {
        return entity != null && entity.isValid();
    }

    private Workflow processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Workflow> context) {
        Workflow entity = context.entity();

        // Business logic from processWorkflow in prototype
        if (entity.getName() == null || entity.getName().isBlank()) {
            logger.error("Workflow validation failed: name is blank");
            // No modification or error throwing, just logging
            return entity;
        }
        logger.info("Processed Workflow {} successfully", context.request().getEntityId());

        return entity;
    }
}
