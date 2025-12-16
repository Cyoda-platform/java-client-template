package com.java_template.application.processor;

import com.java_template.application.entity.user_account.version_1.UserAccount;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Processor for updating user accounts
 * Handles account profile updates and permission changes
 */
@Component
public class UserAccountUpdateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserAccountUpdateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public UserAccountUpdateProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing UserAccountUpdate for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(UserAccount.class)
                .validate(this::isValidEntityWithMetadata, "Invalid user account wrapper")
                .map(this::updateAccount)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<UserAccount> entityWithMetadata) {
        UserAccount entity = entityWithMetadata.entity();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) &&
               entityWithMetadata.metadata().getId() != null;
    }

    private EntityWithMetadata<UserAccount> updateAccount(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<UserAccount> context) {

        EntityWithMetadata<UserAccount> entityWithMetadata = context.entityResponse();
        UserAccount account = entityWithMetadata.entity();

        logger.debug("Updating user account: {} with role: {}", account.getUserId(), account.getRole());

        // Validate email format (simple check)
        if (account.getEmail() != null && !account.getEmail().contains("@")) {
            logger.error("Invalid email format: {}", account.getEmail());
            throw new IllegalArgumentException("Invalid email format");
        }

        // Validate role
        String[] validRoles = {"ADMIN", "TRADER", "RISK_MANAGER", "COMPLIANCE", "VIEWER"};
        boolean validRole = false;
        for (String role : validRoles) {
            if (role.equals(account.getRole())) {
                validRole = true;
                break;
            }
        }
        if (!validRole) {
            logger.error("Invalid role: {}", account.getRole());
            throw new IllegalArgumentException("Invalid role");
        }

        // Update timestamp
        account.setUpdatedAt(LocalDateTime.now());

        logger.info("User account {} updated successfully", account.getUserId());
        return entityWithMetadata;
    }
}

