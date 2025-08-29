package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.serializer.ProcessorSerializer;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserSubmitsAdoptionRequestProcessor implements CyodaProcessor {

    @Autowired
    private ProcessorSerializer serializer;

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();

        try {
            return serializer.withRequest(request)
                .toEntity(User.class)
                .validate(user -> user != null, "User entity cannot be null")
                .validate(user -> user.isValid(), "User must have valid data (name, email, phone)")
                .map(userContext -> {
                    // In a real implementation, this would create an adoption request
                    // For now, we'll just log the action
                    User user = userContext.entity();
                    System.out.println("User " + user.getName() + " (ID: " + user.getId() + ") has submitted an adoption request");

                    // User entity doesn't change state in this workflow, just return as-is
                    return user;
                })
                .complete();

        } catch (Exception e) {
            return serializer.responseBuilder(request)
                .withError("USER_SUBMIT_ADOPTION_REQUEST_ERROR", "Failed to process user adoption request submission: " + e.getMessage())
                .build();
        }
    }

    @Override
    public boolean supports(OperationSpecification modelKey) {
        return "UserSubmitsAdoptionRequestProcessor".equals(modelKey.operationName());
    }
}
