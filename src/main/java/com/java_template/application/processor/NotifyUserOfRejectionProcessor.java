package com.java_template.application.processor;

import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NotifyUserOfRejectionProcessor implements CyodaProcessor {

    @Autowired
    private EntityService entityService;

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        try {
            // Log the processing
            System.out.println("NotifyUserOfRejectionProcessor: Sending rejection notification");

            // In a real implementation, this would:
            // 1. Extract the adoption request from context
            // 2. Get related entities (user, pet) using entityService
            // 3. Send rejection notification
            // 4. Update the pet status back to Available

            // For demonstration, we'll simulate the notification
            System.out.println("📧 REJECTION NOTIFICATION SENT");
            System.out.println("📝 Subject: Update on your adoption request");
            System.out.println("📝 Message: We regret to inform you that your adoption request has not been approved at this time.");
            System.out.println("📝 We encourage you to browse other available pets or contact us for more information.");

            // Return success response
            return new EntityProcessorCalculationResponse();

        } catch (Exception e) {
            System.err.println("NotifyUserOfRejectionProcessor: Error processing rejection notification - " + e.getMessage());
            return new EntityProcessorCalculationResponse();
        }
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "NotifyUserOfRejectionProcessor".equals(opSpec.operationName());
    }
}
