package com.java_template.application.processor;

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
public class NotifyUserOfRejectionProcessor implements CyodaProcessor {

    @Autowired
    private ProcessorSerializer serializer;

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();

        try {
            return serializer.withRequest(request)
                .toEntity(AdoptionRequest.class)
                .validate(adoptionRequest -> adoptionRequest != null, "Adoption request entity cannot be null")
                .validate(adoptionRequest -> "REJECTED".equals(adoptionRequest.getStatus()), "Adoption request must be rejected to notify user")
                .map(userContext -> {
                    // Update adoption request status to indicate users have been notified
                    AdoptionRequest adoptionRequest = userContext.entity();
                    adoptionRequest.setStatus("USERS_NOTIFIED");

                    // In a real implementation, this would send rejection notification
                    System.out.println("Rejection notification sent for adoption request ID: " + adoptionRequest.getId() +
                                     " (Pet ID: " + adoptionRequest.getPetId() + ", User ID: " + adoptionRequest.getUserId() + ")");

                    return adoptionRequest;
                })
                .complete();

        } catch (Exception e) {
            return serializer.responseBuilder(request)
                .withError("NOTIFY_REJECTION_ERROR", "Failed to notify user of rejection: " + e.getMessage())
                .build();
        }
    }

    @Override
    public boolean supports(OperationSpecification modelKey) {
        return "NotifyUserOfRejectionProcessor".equals(modelKey.operationName());
    }
}
