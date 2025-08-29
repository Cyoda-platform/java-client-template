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
public class NotifyUserAboutRequestStatusProcessor implements CyodaProcessor {

    @Autowired
    private EntityService entityService;

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        try {
            // Log the processing
            System.out.println("NotifyUserAboutRequestStatusProcessor: Sending status notification to user");

            // In a real implementation, this would:
            // 1. Extract the user from context
            // 2. Determine the current status of the user's adoption requests using entityService
            // 3. Send appropriate notifications (email, SMS, push notification)
            // 4. Update user notification preferences or history

            // For demonstration, we'll simulate sending a notification
            System.out.println("📧 STATUS NOTIFICATION SENT");
            System.out.println("📱 Message: Your adoption request status has been updated. Please check your account for details.");

            // Return success response
            return new EntityProcessorCalculationResponse();

        } catch (Exception e) {
            System.err.println("NotifyUserAboutRequestStatusProcessor: Error processing status notification - " + e.getMessage());
            return new EntityProcessorCalculationResponse();
        }
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "NotifyUserAboutRequestStatusProcessor".equals(opSpec.operationName());
    }
}
