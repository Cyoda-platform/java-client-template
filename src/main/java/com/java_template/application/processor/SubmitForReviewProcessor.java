package com.java_template.application.processor;

import com.java_template.application.entity.task.version_1.Task;
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
public class SubmitForReviewProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubmitForReviewProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubmitForReviewProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Task (SubmitForReview) for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Task.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Task task) {
        return task != null && task.isValid();
    }

    private Task processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Task> context) {
        Task task = context.entity();
        try {
            task.setStatus("in_review");
            String now = Instant.now().toString();
            task.setUpdatedAt(now);
            // event emission/notifications handled by orchestration
        } catch (Exception e) {
            logger.error("Error in SubmitForReviewProcessor: ", e);
            if (task != null) {
                if (task.getMetadata() == null) task.setMetadata(new java.util.HashMap<>());
                task.getMetadata().put("lastProcessorError", e.getMessage());
            }
        }
        return task;
    }
}
