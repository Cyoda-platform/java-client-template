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
public class WorkflowProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public WorkflowProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("WorkflowProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Workflow for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
                .toEntity(Workflow.class)
                .validate(Workflow::isValid)
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "WorkflowProcessor".equals(modelSpec.operationName()) &&
                "workflow".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Workflow processEntityLogic(Workflow workflow) {
        logger.info("Processing Workflow with name: {}", workflow.getName());

        if (workflow.getName().isBlank() || workflow.getDescription().isBlank()) {
            logger.error("Workflow validation failed: name or description is blank");
            workflow.setStatus("FAILED");
            return workflow;
        }
        workflow.setStatus("RUNNING");

        logger.info("Orchestrating related Orders and Customers for workflow: {}", workflow.getName());

        // Simulate completion
        workflow.setStatus("COMPLETED");
        logger.info("Workflow processing completed successfully");

        return workflow;
    }
}
