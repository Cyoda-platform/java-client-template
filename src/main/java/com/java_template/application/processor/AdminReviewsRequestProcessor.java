package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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
public class AdminReviewsRequestProcessor implements CyodaProcessor {

    @Autowired
    private ProcessorSerializer serializer;

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();

        try {
            // For this processor, we'll assume it's processing Pet entities by default
            // In a real implementation, you might determine entity type from the request context
            return serializer.withRequest(request)
                .toEntity(Pet.class)
                .validate(pet -> pet != null, "Pet entity cannot be null")
                .validate(pet -> "ADOPTION_REQUESTED".equals(pet.getStatus()), "Pet must be in adoption requested status")
                .map(requestContext -> {
                    // Update pet status to indicate admin is reviewing
                    Pet pet = requestContext.entity();
                    pet.setStatus("APPROVAL_PROCESS");
                    return pet;
                })
                .complete();

        } catch (Exception e) {
            return serializer.responseBuilder(request)
                .withError("ADMIN_REVIEW_ERROR", "Failed to process admin review: " + e.getMessage())
                .build();
        }
    }

    @Override
    public boolean supports(OperationSpecification modelKey) {
        return "AdminReviewsRequestProcessor".equals(modelKey.operationName());
    }
}
