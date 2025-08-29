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
public class NotifyUserOfApprovalProcessor implements CyodaProcessor {

    @Autowired
    private EntityService entityService;

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        try {
            // Log the processing
            System.out.println("NotifyUserOfApprovalProcessor: Sending approval notification");

            // In a real implementation, this would:
            // 1. Extract the adoption request from context
            // 2. Get related entities (user, pet) using entityService
            // 3. Send approval notification
            // 4. Update the pet status to ADOPTED

            // For demonstration, we'll simulate the notification
            System.out.println("🎉 APPROVAL NOTIFICATION SENT");
            System.out.println("📧 Subject: Congratulations! Your adoption request has been APPROVED!");
            System.out.println("📝 Message: Your adoption request has been approved!");
            System.out.println("📞 We will contact you with next steps.");

            // Return success response
            return new EntityProcessorCalculationResponse();

        } catch (Exception e) {
            System.err.println("NotifyUserOfApprovalProcessor: Error processing approval notification - " + e.getMessage());
            return new EntityProcessorCalculationResponse();
        }
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "NotifyUserOfApprovalProcessor".equals(opSpec.operationName());
    }
}
