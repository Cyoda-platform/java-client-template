package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.serializer.ProcessorSerializer;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NotifyUserProcessor implements CyodaProcessor {

    @Autowired
    private ProcessorSerializer serializer;

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();

        try {
            return serializer.withRequest(request)
                .toEntity(Pet.class)
                .validate(pet -> pet != null, "Pet entity cannot be null")
                .validate(pet -> "ADOPTED".equals(pet.getStatus()), "Pet must be in adopted status to notify user")
                .map(userContext -> {
                    // Update pet status to indicate users have been notified
                    Pet pet = userContext.entity();
                    pet.setStatus("USERS_NOTIFIED");

                    // In a real implementation, this would trigger notification logic
                    // For now, we'll just log the notification action
                    System.out.println("Notification sent: Pet " + pet.getName() + " (ID: " + pet.getId() + ") has been adopted successfully!");

                    return pet;
                })
                .complete();

        } catch (Exception e) {
            return serializer.responseBuilder(request)
                .withError("NOTIFY_USER_ERROR", "Failed to notify user: " + e.getMessage())
                .build();
        }
    }

    @Override
    public boolean supports(OperationSpecification modelKey) {
        return "NotifyUserProcessor".equals(modelKey.operationName());
    }
}
