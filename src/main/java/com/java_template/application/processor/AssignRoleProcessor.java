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

@Component
public class AssignRoleProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AssignRoleProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AssignRoleProcessor(SerializerFactory serializerFactory) {
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

        // Business rules:
        // - Assign a default role if none provided. Allowed roles: "Admin" or "Customer".
        // - Normalize role values (case-insensitive).
        // - After role setup mark the user as active (transition to ACTIVE state).

        if (entity != null) {
            String role = entity.getRole();
            if (role == null || role.isBlank()) {
                entity.setRole("Customer");
            } else if (role.equalsIgnoreCase("admin")) {
                entity.setRole("Admin");
            } else {
                // Any non-admin role is treated as Customer by default
                entity.setRole("Customer");
            }

            // Move user to active state after role setup
            entity.setStatus("active");

            logger.info("Assigned role '{}' and set status='active' for user '{}'", entity.getRole(), entity.getUserId());
        } else {
            logger.warn("Received null User entity in AssignRoleProcessor");
        }

        return entity;
    }
}