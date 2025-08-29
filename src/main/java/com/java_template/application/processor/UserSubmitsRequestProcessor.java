package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.serializer.ProcessorSerializer;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserSubmitsRequestProcessor implements CyodaProcessor {

    @Autowired
    private ProcessorSerializer serializer;

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();

        try {
            // Process Pet entity when user submits adoption request
            return serializer.withRequest(request)
                .toEntity(Pet.class)
                .validate(pet -> pet != null, "Pet entity cannot be null")
                .validate(pet -> "Available".equals(pet.getStatus()), "Pet must be available for adoption")
                .map(petContext -> {
                    // Update pet status to indicate adoption request has been submitted
                    Pet pet = petContext.entity();
                    pet.setStatus("ADOPTION_REQUESTED");
                    return pet;
                })
                .complete();

        } catch (Exception e) {
            return serializer.responseBuilder(request)
                .withError("USER_SUBMIT_REQUEST_ERROR", "Failed to process user adoption request: " + e.getMessage())
                .build();
        }
    }

    @Override
    public boolean supports(OperationSpecification modelKey) {
        return "UserSubmitsRequestProcessor".equals(modelKey.operationName());
    }
}
