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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Processor for registering new users in the system.
 * Handles the register_user transition from initial_state to registered.
 */
@Component
public class UserRegistrationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserRegistrationProcessor.class);
    private final ProcessorSerializer serializer;

    public UserRegistrationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing user registration for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(User.class)
            .validate(user -> user.getUsername() != null && !user.getUsername().trim().isEmpty(), 
                     "Username is required")
            .validate(user -> user.getEmail() != null && !user.getEmail().trim().isEmpty(), 
                     "Email is required")
            .validate(user -> user.getPassword() != null && !user.getPassword().trim().isEmpty(), 
                     "Password is required")
            .validate(user -> isValidEmail(user.getEmail()), 
                     "Email format is invalid")
            .map(context -> {
                User user = context.entity();
                
                // Generate unique user ID if not provided
                if (user.getId() == null) {
                    user.setId(System.currentTimeMillis()); // Simple ID generation
                }
                
                // Encrypt password (simple hash for demo purposes)
                user.setPassword(encryptPassword(user.getPassword()));
                
                // Set default user status to 0 (registered but not active)
                if (user.getUserStatus() == null) {
                    user.setUserStatus(0);
                }
                
                logger.info("Registered user with ID: {} and username: {}", user.getId(), user.getUsername());
                
                // Note: In a real implementation, this would send a welcome email
                
                return user;
            })
            .complete();
    }

    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }

    private String encryptPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            logger.error("Error encrypting password", e);
            return password; // Fallback to plain text (not recommended for production)
        }
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "UserRegistrationProcessor".equals(opSpec.operationName()) &&
               "User".equals(opSpec.modelKey().getName()) &&
               opSpec.modelKey().getVersion() >= 1;
    }
}
