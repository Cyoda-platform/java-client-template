package com.java_template.application.processor;

import com.java_template.application.entity.userimportjob.version_1.UserImportJob;
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
public class UserImportJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserImportJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UserImportJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing UserImportJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(UserImportJob.class)
            .validate(this::isValidEntity, "Invalid UserImportJob state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(UserImportJob entity) {
        if (entity == null) {
            return false;
        }
        // Basic validation: id and status must not be null
        if (entity.getId() == null || entity.getStatus() == null) {
            return false;
        }
        // Status must be one of PENDING, PROCESSING, VALIDATING, CREATING, SUCCESS, FAILED
        String status = entity.getStatus();
        return status.equals("pending") || status.equals("processing") || status.equals("validating") ||
               status.equals("creating") || status.equals("success") || status.equals("failed");
    }

    private UserImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<UserImportJob> context) {
        UserImportJob entity = context.entity();
        // Business logic implementation for UserImportJob processing
        // TODO: Implement specific processing logic such as updating status, handling errors

        // For MVP, simulate processing logic:
        if (entity.getStatus().equalsIgnoreCase("pending")) {
            entity.setStatus("processing");
            logger.info("UserImportJob status updated to processing");
        } else if (entity.getStatus().equalsIgnoreCase("processing")) {
            logger.info("UserImportJob is processing user data ingestion");
            entity.setStatus("validating");
            logger.info("UserImportJob status updated to validating");
        } else if (entity.getStatus().equalsIgnoreCase("validating")) {
            logger.info("UserImportJob is in validating state");
        } else if (entity.getStatus().equalsIgnoreCase("creating")) {
            logger.info("Creating Users from import job");
            entity.setStatus("success");
            logger.info("UserImportJob status updated to success");
        } else if (entity.getStatus().equalsIgnoreCase("success")) {
            logger.info("UserImportJob completed successfully");
        } else if (entity.getStatus().equalsIgnoreCase("failed")) {
            logger.warn("UserImportJob failed with errors: {}", entity.getErrorMessages());
        }
        return entity;
    }
}
