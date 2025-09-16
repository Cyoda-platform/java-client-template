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

/**
 * Processor for activating users.
 * Handles the activate_user transition from registered to active.
 */
@Component
public class UserActivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserActivationProcessor.class);
    private final ProcessorSerializer serializer;

    public UserActivationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing user activation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(User.class)
            .map(processingContext -> {
                User user = processingContext.entity();
                
                // Set user status to 1 (active)
                user.setUserStatus(1);
                
                logger.info("Activated user with ID: {} and username: {}", user.getId(), user.getUsername());
                
                // Note: In a real implementation, this would send an activation confirmation email
                
                return user;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "UserActivationProcessor".equals(opSpec.operationName()) &&
               "User".equals(opSpec.modelKey().getName()) &&
               opSpec.modelKey().getVersion() >= 1;
    }
}
