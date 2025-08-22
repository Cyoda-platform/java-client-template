package com.java_template.application.processor;

import com.java_template.application.entity.ingestjob.version_1.IngestJob;
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

import java.util.Map;
import java.util.Objects;

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
        logger.info("Processing IngestJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(IngestJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestJob entity) {
        return entity != null && entity.isValid();
    }

    private IngestJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestJob> context) {
        IngestJob job = context.entity();
        if (job == null) {
            logger.warn("Received null IngestJob in processing context");
            return null;
        }

        // Move to VALIDATING state
        job.setStatus("VALIDATING");
        job.setErrorMessage(null);

        Map<String, Object> payload = job.getHnPayload();
        if (payload == null) {
            job.setStatus("FAILED");
            job.setErrorMessage("hn_payload missing or not a valid JSON object");
            logger.info("IngestJob {} validation failed: {}", job.getTechnicalId(), job.getErrorMessage());
            return job;
        }

        // Basic required fields validation based on HNItem rules:
        Object idObj = payload.get("id");
        Object byObj = payload.get("by");
        Object timeObj = payload.get("time");
        Object typeObj = payload.get("type");
        Object titleObj = payload.get("title");

        if (idObj == null) {
            job.setStatus("FAILED");
            job.setErrorMessage("missing required field: id");
            logger.info("IngestJob {} validation failed: {}", job.getTechnicalId(), job.getErrorMessage());
            return job;
        }

        if (byObj == null || String.valueOf(byObj).isBlank()) {
            job.setStatus("FAILED");
            job.setErrorMessage("missing or blank required field: by");
            logger.info("IngestJob {} validation failed: {}", job.getTechnicalId(), job.getErrorMessage());
            return job;
        }

        if (timeObj == null) {
            job.setStatus("FAILED");
            job.setErrorMessage("missing required field: time");
            logger.info("IngestJob {} validation failed: {}", job.getTechnicalId(), job.getErrorMessage());
            return job;
        }

        // For story items require a non-blank title
        String type = typeObj == null ? null : String.valueOf(typeObj);
        if ("story".equalsIgnoreCase(type)) {
            if (titleObj == null || String.valueOf(titleObj).isBlank()) {
                job.setStatus("FAILED");
                job.setErrorMessage("missing or blank required field: title for story item");
                logger.info("IngestJob {} validation failed: {}", job.getTechnicalId(), job.getErrorMessage());
                return job;
            }
        }

        // All validations passed: advance to PROCESSING
        job.setStatus("PROCESSING");
        job.setErrorMessage(null);
        logger.info("IngestJob {} validation passed, moving to PROCESSING", job.getTechnicalId());
        return job;
    }
}