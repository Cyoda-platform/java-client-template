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
 * Processor for deleting users.
 * Handles both delete_user (active → deleted) and delete_suspended_user (suspended → deleted) transitions.
 */
@Component
public class UserDeletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserDeletionProcessor.class);
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public UserDeletionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing user deletion for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(User.class)
            .map(processingContext -> {
                User user = processingContext.entity();
                
                // Anonymize personal data (keep ID for referential integrity)
                user.setFirstName("DELETED");
                user.setLastName("DELETED");
                user.setEmail("deleted@example.com");
                user.setPhone("DELETED");
                user.setPassword("DELETED");
                
                // Set user status to -2 (deleted)
                user.setUserStatus(-2);
                
                logger.info("Deleted user with ID: {} (anonymized)", user.getId());
                
                // Note: In a real implementation, this would:
                // 1. Cancel all active orders by querying orders for this user
                //    and calling entityService.applyTransition() with cancellation transitions
                // 2. Send account deletion confirmation
                
                return user;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "UserDeletionProcessor".equals(opSpec.operationName()) &&
               "User".equals(opSpec.modelKey().getName()) &&
               opSpec.modelKey().getVersion() >= 1;
    }
}
