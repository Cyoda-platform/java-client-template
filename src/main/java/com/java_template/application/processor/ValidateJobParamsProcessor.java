package com.java_template.application.processor;

import com.java_template.application.entity.batchjob.version_1.BatchJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(BatchJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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

        StringBuilder errors = new StringBuilder();

        // Validate scheduleCron: must be present and not blank
        try {
            String scheduleCron = entity.getScheduleCron();
            if (scheduleCron == null || scheduleCron.isBlank()) {
                if (errors.length() > 0) errors.append("; ");
                errors.append("scheduleCron is required");
            }
        } catch (Exception e) {
            if (errors.length() > 0) errors.append("; ");
            errors.append("scheduleCron validation error");
            logger.debug("Error while validating scheduleCron", e);
        }

        // Validate runMonth: must match YYYY-MM
        try {
            String runMonth = entity.getRunMonth();
            if (runMonth == null || runMonth.isBlank()) {
                if (errors.length() > 0) errors.append("; ");
                errors.append("runMonth is required");
            } else {
                String runMonthPattern = "^\\d{4}-\\d{2}$";
                if (!runMonth.matches(runMonthPattern)) {
                    if (errors.length() > 0) errors.append("; ");
                    errors.append("runMonth must match YYYY-MM");
                }
            }
        } catch (Exception e) {
            if (errors.length() > 0) errors.append("; ");
            errors.append("runMonth validation error");
            logger.debug("Error while validating runMonth", e);
        }

        // If there are validation errors, mark FAILED and set summary
        if (errors.length() > 0) {
            entity.setStatus("FAILED");
            entity.setSummary(errors.toString());
            logger.info("BatchJob validation failed: {}", errors.toString());
        } else {
            // Passed validation checks -> move to VALIDATING state
            entity.setStatus("VALIDATING");
            // Clear any previous summary
            entity.setSummary(null);
            logger.info("BatchJob validation passed. Status set to VALIDATING for jobName={}", entity.getJobName());
        }

        return entity;
    }
}