package com.java_template.application.processor;

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
public class ProcessPetProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProcessPetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Workflow for request: {}", request.getId());

        // Chain processing using ProcessorSerializer
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
        Workflow workflow = context.entity();
        logger.info("Executing Pet entities processing for Workflow: {}", workflow.getName());
        // Business logic from prototype:
        // The prototype method runProcessPetEntitiesProcessor just logs information.
        // So here we replicate that logic exactly.

        // No changes to the workflow entity are made here.

        return workflow;
    }
}
