package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
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

@Component
public class AdminApproveProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdminApproveProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AdminApproveProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdminApprove for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid user state")
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
            user.setStatus("Active");
            logger.info("User {} approved by admin", user.getUserId());
            return user;
        } catch (Exception e) {
            logger.error("Error approving user {}: {}", user.getUserId(), e.getMessage(), e);
            return user;
        }
    }
}
