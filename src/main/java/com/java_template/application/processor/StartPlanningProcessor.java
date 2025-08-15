package com.java_template.application.processor;

import com.java_template.application.entity.project.version_1.Project;
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

import java.time.Instant;

@Component
public class StartPlanningProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartPlanningProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StartPlanningProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Project (StartPlanning) for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Project.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Project project) {
        return project != null && project.isValid();
    }

    private Project processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Project> context) {
        Project project = context.entity();
        try {
            if (project.getStatus() == null || !project.getStatus().equalsIgnoreCase("created")) {
                // nothing to do; idempotent
                return project;
            }
            // initialize default metadata if missing
            if (project.getMetadata() == null) {
                project.setMetadata(new java.util.HashMap<>());
            }
            project.getMetadata().putIfAbsent("milestonesInitialized", true);
            project.setStatus("planning");
            String now = Instant.now().toString();
            project.setUpdatedAt(now);
        } catch (Exception e) {
            logger.error("Error in StartPlanningProcessor: ", e);
            // record error in metadata for diagnosis
            if (project != null) {
                if (project.getMetadata() == null) project.setMetadata(new java.util.HashMap<>());
                project.getMetadata().put("lastProcessorError", e.getMessage());
            }
        }
        return project;
    }
}
