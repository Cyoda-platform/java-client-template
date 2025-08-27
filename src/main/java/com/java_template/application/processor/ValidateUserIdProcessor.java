package com.java_template.application.processor;
import com.java_template.application.entity.getuserjob.version_1.GetUserJob;
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
public class ValidateUserIdProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateUserIdProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateUserIdProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing GetUserJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(GetUserJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(GetUserJob entity) {
        return entity != null && entity.isValid();
    }

    private GetUserJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<GetUserJob> context) {
        GetUserJob entity = context.entity();

        // Validation: requestUserId must be present and a positive integer
        String requestUserId = entity.getRequestUserId();
        if (requestUserId == null || requestUserId.isBlank()) {
            entity.setStatus("FAILED");
            entity.setErrorMessage("User ID is required");
            logger.warn("Validation failed for GetUserJob: requestUserId is missing");
            return entity;
        }

        // Trim and validate numeric positive integer
        String trimmed = requestUserId.trim();
        try {
            int id = Integer.parseInt(trimmed);
            if (id <= 0) {
                entity.setStatus("FAILED");
                entity.setErrorMessage("User ID must be a positive integer");
                logger.warn("Validation failed for GetUserJob: requestUserId is not a positive integer: {}", trimmed);
                return entity;
            }
        } catch (NumberFormatException nfe) {
            entity.setStatus("FAILED");
            entity.setErrorMessage("User ID must be a positive integer");
            logger.warn("Validation failed for GetUserJob: requestUserId is not numeric: {}", trimmed);
            return entity;
        }

        // Validation passed -> proceed to fetching stage
        entity.setStatus("FETCHING");
        entity.setErrorMessage(null);
        logger.info("Validation passed for GetUserJob, moving to FETCHING for user id: {}", requestUserId);

        return entity;
    }
}