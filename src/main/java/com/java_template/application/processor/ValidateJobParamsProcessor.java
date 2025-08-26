package com.java_template.application.processor;

import com.java_template.application.entity.batchjob.version_1.BatchJob;
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

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
public class ValidateJobParamsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateJobParamsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateJobParamsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing BatchJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(BatchJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(BatchJob entity) {
        return entity != null && entity.isValid();
    }

    private BatchJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<BatchJob> context) {
        BatchJob entity = context.entity();
        if (entity == null) {
            logger.warn("BatchJob entity is null in execution context");
            return null;
        }

        List<String> errors = new ArrayList<>();

        // Validate scheduleCron non-empty
        if (entity.getScheduleCron() == null || entity.getScheduleCron().isBlank()) {
            errors.add("scheduleCron must be provided");
        }

        // Validate runMonth format YYYY-MM
        String runMonth = entity.getRunMonth();
        if (runMonth == null || runMonth.isBlank()) {
            errors.add("runMonth must be provided");
        } else {
            try {
                YearMonth.parse(runMonth); // expects YYYY-MM
            } catch (DateTimeParseException ex) {
                errors.add("runMonth must be in format YYYY-MM");
            }
        }

        if (!errors.isEmpty()) {
            // Set job to FAILED with validation summary
            entity.setStatus("FAILED");
            String summary = String.join("; ", errors);
            entity.setSummary("Validation failed: " + summary);
            logger.info("BatchJob validation failed for id {}: {}", entity.getId(), summary);
        } else {
            // Passed validation -> move to VALIDATING
            entity.setStatus("VALIDATING");
            entity.setSummary("Validation passed");
            logger.info("BatchJob validation passed for id {}", entity.getId());
        }

        return entity;
    }
}