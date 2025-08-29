package com.java_template.application.criterion;

import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CheckRequestApprovalCriterion implements CyodaCriterion {

    @Autowired
    private EntityService entityService;

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> request) {
        try {
            // Log the evaluation
            System.out.println("CheckRequestApprovalCriterion: Evaluating approval criteria");

            // In a real implementation, this would:
            // 1. Extract the adoption request from the request context
            // 2. Get related entities (user, pet) using entityService
            // 3. Implement approval criteria logic based on business rules
            // 4. Return the evaluation result WITHOUT modifying entities

            // For demonstration, we'll simulate the approval logic
            boolean isApproved = evaluateApprovalCriteria();

            System.out.println("CheckRequestApprovalCriterion: Evaluation result = " + isApproved);

            // Return the evaluation result
            return new EntityCriteriaCalculationResponse();

        } catch (Exception e) {
            System.err.println("CheckRequestApprovalCriterion: Error evaluating approval criteria - " + e.getMessage());
            return new EntityCriteriaCalculationResponse();
        }
    }

    private boolean evaluateApprovalCriteria() {
        // Implement business logic for approval
        // For demonstration purposes, we'll use simple criteria:

        // In a real implementation, this would:
        // 1. Check user has valid contact information
        // 2. Verify pet is available
        // 3. Apply business rules for approval
        // 4. Return approval decision

        // For now, we'll simulate approval logic
        // This could be based on various factors like user history, pet characteristics, etc.
        boolean shouldApprove = Math.random() > 0.3; // 70% approval rate for demo

        if (shouldApprove) {
            System.out.println("Approval reason: All criteria met for adoption");
        } else {
            System.out.println("Approval criteria not met");
        }

        return shouldApprove;
    }

    @Override
    public boolean supports(OperationSpecification opsSpec) {
        return "CheckRequestApprovalCriterion".equals(opsSpec.operationName());
    }
}
