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
public class NotifyUserProcessor implements CyodaProcessor {

    @Autowired
    private EntityService entityService;

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        try {
            // Log the processing
            System.out.println("NotifyUserProcessor: Processing user notification");

            // In a real implementation, this would:
            // 1. Extract the pet from context (triggered from Pet workflow)
            // 2. Find the adoption request related to this pet using entityService
            // 3. Get the user from the adoption request
            // 4. Send notification (email, SMS, push notification, etc.)

            // For demonstration purposes, we'll simulate the notification
            System.out.println("📧 NOTIFICATION SENT: Congratulations! Your adoption has been approved and completed!");
            System.out.println("📱 SMS/Email would be sent to the user with adoption details and next steps.");

            // Return success response
            return new EntityProcessorCalculationResponse();

        } catch (Exception e) {
            System.err.println("NotifyUserProcessor: Error processing user notification - " + e.getMessage());
            return new EntityProcessorCalculationResponse();
        }
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "NotifyUserProcessor".equals(opSpec.operationName());
    }
}
