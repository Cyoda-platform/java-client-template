package com.java_template.application.processor;
import com.java_template.application.entity.user.version_1.User;
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

import java.util.UUID;

@Component
public class StoreUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StoreUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StoreUserProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(User entity) {
        return entity != null && entity.isValid();
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User entity = context.entity();
        if (entity == null) {
            logger.warn("Received null User entity in processing context");
            return entity;
        }

        String currentStatus = entity.getProcessingStatus();
        if (currentStatus == null) {
            logger.info("User[{}] has no processingStatus, skipping storage", entity.getId());
            return entity;
        }

        // Only store users that have been transformed
        if (!"TRANSFORMED".equalsIgnoreCase(currentStatus)) {
            logger.info("User[{}] in status '{}' is not TRANSFORMED, skipping storage", entity.getId(), currentStatus);
            return entity;
        }

        // Simulate persisting the normalized record into cloud DB by assigning a stored reference
        String storedRef = "stored_user_" + UUID.randomUUID();
        entity.setStoredReference(storedRef);

        // Update processing status to STORED so Cyoda will persist this entity change automatically
        entity.setProcessingStatus("STORED");

        logger.info("User[{}] stored with reference '{}', status set to STORED", entity.getId(), storedRef);

        return entity;
    }
}