package com.java_template.application.processor;

import com.java_template.application.entity.Workflow;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PetProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Workflow entity for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Workflow.class)
            .validate(Workflow::isValid)
            .map(this::processWorkflow)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetProcessor".equals(modelSpec.operationName()) &&
               "workflow".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Workflow processWorkflow(Workflow workflow) {
        logger.info("Processing Workflow with name: {}", workflow.getWorkflowName());
        if ("CREATED".equalsIgnoreCase(workflow.getStatus())) {
            logger.info("Starting orchestration for Workflow: {}", workflow.getWorkflowName());
            // Simulated orchestration and processing
            workflow.setStatus("COMPLETED");
            logger.info("Workflow {} completed successfully", workflow.getWorkflowName());
        } else {
            logger.error("Workflow {} is in invalid status: {}", workflow.getWorkflowName(), workflow.getStatus());
            workflow.setStatus("FAILED");
        }
        return workflow;
    }
}
