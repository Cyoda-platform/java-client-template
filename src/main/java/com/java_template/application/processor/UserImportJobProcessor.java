package com.java_template.application.processor;

import com.java_template.application.entity.userimportjob.version_1.UserImportJob;
import com.java_template.application.entity.user.version_1.User;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class UserImportJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserImportJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public UserImportJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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
        if (entity.getId() == null || entity.getStatus() == null) {
            return false;
        }
        String status = entity.getStatus();
        return status.equalsIgnoreCase("pending") ||
               status.equalsIgnoreCase("processing") ||
               status.equalsIgnoreCase("validating") ||
               status.equalsIgnoreCase("creating") ||
               status.equalsIgnoreCase("success") ||
               status.equalsIgnoreCase("failed");
    }

    private UserImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<UserImportJob> context) {
        UserImportJob entity = context.entity();
        try {
            if (entity.getStatus().equalsIgnoreCase("pending")) {
                entity.setStatus("processing");
                logger.info("UserImportJob status updated to processing");
            } else if (entity.getStatus().equalsIgnoreCase("processing")) {
                logger.info("UserImportJob is processing user data ingestion");
                // Simulate ingestion of user data
                entity.setStatus("validating");
                logger.info("UserImportJob status updated to validating");
            } else if (entity.getStatus().equalsIgnoreCase("validating")) {
                logger.info("UserImportJob is in validating state");
                // Validation handled externally by criteria
            } else if (entity.getStatus().equalsIgnoreCase("creating")) {
                logger.info("Creating Users from import job");

                // Simulate user creation - create a sample user
                User user = new User();
                user.setId(UUID.randomUUID().toString());
                user.setName("Sample User");
                user.setEmail("sampleuser@example.com");
                user.setPassword("hashedpassword");
                user.setRole("Customer");

                CompletableFuture<UUID> addFuture = entityService.addItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    user
                );

                try {
                    addFuture.get();
                    entity.setStatus("success");
                    logger.info("UserImportJob status updated to success");
                } catch (InterruptedException | ExecutionException e) {
                    entity.setStatus("failed");
                    entity.setErrorMessages("Failed to create user: " + e.getMessage());
                    logger.error("Error creating user", e);
                }

            } else if (entity.getStatus().equalsIgnoreCase("success")) {
                logger.info("UserImportJob completed successfully");
            } else if (entity.getStatus().equalsIgnoreCase("failed")) {
                logger.warn("UserImportJob failed with errors: {}", entity.getErrorMessages());
            }
        } catch (Exception e) {
            logger.error("Unexpected error in UserImportJobProcessor", e);
            entity.setStatus("failed");
            entity.setErrorMessages("Unexpected error: " + e.getMessage());
        }
        return entity;
    }
}
