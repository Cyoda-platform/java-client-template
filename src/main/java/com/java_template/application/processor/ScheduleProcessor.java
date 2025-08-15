package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ScheduleProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ScheduleProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Schedule for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        String technicalId = null;
        try {
            try { technicalId = context.request().getEntityId(); } catch (Exception ignored) {}

            job.setStatus("SCHEDULED");
            // In a real system we'd schedule execution time; here set scheduled flag by updating attemptCount
            Integer attempts = job.getAttemptCount();
            if (attempts == null) attempts = 0;
            job.setAttemptCount(attempts);

            // persist
            if (technicalId != null) {
                entityService.updateItem(Job.ENTITY_NAME, String.valueOf(Job.ENTITY_VERSION), UUID.fromString(technicalId), job).join();
            }
        } catch (Exception e) {
            logger.error("Error scheduling job {}: {}", technicalId, e.getMessage());
        }
        return job;
    }
}
