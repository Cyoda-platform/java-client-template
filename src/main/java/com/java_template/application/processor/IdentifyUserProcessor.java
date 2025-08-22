package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class IdentifyUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IdentifyUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public IdentifyUserProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User identify request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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
        User user = context.entity();

        try {
            if (user == null) {
                logger.warn("IdentifyUserProcessor received null user entity in context");
                return null;
            }

            // If already identified, nothing to do
            if ("IDENTIFIED".equalsIgnoreCase(user.getIdentificationStatus())) {
                logger.info("User {} already IDENTIFIED", user.getId());
                return user;
            }

            logger.info("Identifying user {}", user.getId());
            // Mark user as identified and update timestamp. The platform will persist this entity automatically.
            user.setIdentificationStatus("IDENTIFIED");
            user.setUpdatedAt(Instant.now().toString());

            // NOTE:
            // Linking anonymous carts to the newly identified user is handled by a separate processor (LinkAnonCartsProcessor).
            // We do not perform update operations on the same entity here (the platform persists user changes automatically).
            // EntityService is available if additional cross-entity operations are required in future.

            return user;
        } catch (Exception ex) {
            logger.error("Error while processing IdentifyUserProcessor for user {}: {}", user != null ? user.getId() : "null", ex.getMessage(), ex);
            throw ex;
        }
    }
}