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

import java.util.ArrayList;
import java.util.List;

@Component
public class CompleteProfileProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CompleteProfileProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CompleteProfileProcessor(SerializerFactory serializerFactory) {
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
        User user = context.entity();

        // Determine profile completeness based on existing fields.
        boolean fullNameOk = user.getFullName() != null && !user.getFullName().isBlank();
        boolean emailOk = user.getEmail() != null && !user.getEmail().isBlank() && user.getEmail().contains("@");
        boolean phoneOk = user.getPhone() != null && !user.getPhone().isBlank();
        boolean addressOk = user.getAddress() != null && !user.getAddress().isBlank();

        boolean profileComplete = fullNameOk && emailOk && phoneOk && addressOk;

        // The User entity model in this codebase does not include an explicit 'status' or 'verified' field.
        // To reflect that the profile is complete (as required by business rules) we mark the user's preferences
        // with a durable flag "profile_ready". This modifies only existing fields and uses available getters/setters.
        List<String> prefs = user.getPreferences();
        if (prefs == null) {
            prefs = new ArrayList<>();
            user.setPreferences(prefs);
        }

        if (profileComplete) {
            if (!prefs.contains("profile_ready")) {
                prefs.add("profile_ready");
            }
            logger.info("User {} profile is complete; marked profile_ready", user.getId());
        } else {
            // If profile is not complete, ensure the flag is not present
            if (prefs.contains("profile_ready")) {
                prefs.removeIf(p -> "profile_ready".equals(p));
                logger.info("User {} profile is incomplete; removed profile_ready flag", user.getId());
            } else {
                logger.debug("User {} profile is incomplete", user.getId());
            }
        }

        return user;
    }
}