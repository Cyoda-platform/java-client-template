package com.java_template.application.processor;

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
import org.springframework.stereotype.Component;

/**
 * Processor for suspending users.
 * Handles the suspend_user transition from active to suspended.
 */
@Component
public class UserSuspensionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserSuspensionProcessor.class);
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public UserSuspensionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing user suspension for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(User.class)
            .map(context -> {
                User user = context.entity();
                
                // Set user status to -1 (suspended)
                user.setUserStatus(-1);
                
                logger.info("Suspended user with ID: {} and username: {}", user.getId(), user.getUsername());
                
                // Note: In a real implementation, this would:
                // 1. Cancel active orders if any by querying orders for this user
                //    and calling entityService.applyTransition() with cancellation transitions
                // 2. Send suspension notification email
                
                return user;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "UserSuspensionProcessor".equals(opSpec.operationName()) &&
               "User".equals(opSpec.modelKey().getName()) &&
               opSpec.modelKey().getVersion() >= 1;
    }
}
