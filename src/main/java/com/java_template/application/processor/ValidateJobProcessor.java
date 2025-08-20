package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
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

import java.util.HashMap;
import java.util.Map;

@Component
public class ValidateJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.getJobId() != null;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        if (job == null) return null;

        if ("VALIDATING".equalsIgnoreCase(job.getStatus()) || "FAILED".equalsIgnoreCase(job.getStatus()) || "SCHEDULED".equalsIgnoreCase(job.getStatus())) {
            logger.info("Job already in state: {}", job.getStatus());
            return job;
        }

        job.setStatus("VALIDATING");

        boolean valid = true;
        if (job.getPayload() == null) {
            valid = false;
        }
        if (!valid) {
            job.setStatus("FAILED");
            try {
                if (job.getMetadata() == null || !(job.getMetadata() instanceof java.util.Map)) {
                    java.util.Map<String,Object> m = new java.util.HashMap<>();
                    m.put("validationFailure", "missing-payload");
                    job.setMetadata(m);
                } else {
                    ((java.util.Map) job.getMetadata()).put("validationFailure", "missing-payload");
                }
            } catch (Exception ignored) {}
        } else {
            job.setStatus("SCHEDULED");
        }

        return job;
    }
}
