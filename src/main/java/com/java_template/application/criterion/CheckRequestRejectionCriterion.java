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
public class CheckRequestRejectionCriterion implements CyodaCriterion {

    @Autowired
    private EntityService entityService;

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> request) {
        try {
            // Log the evaluation
            System.out.println("CheckRequestRejectionCriterion: Evaluating rejection criteria");

            // In a real implementation, this would:
            // 1. Extract the adoption request from the request context
            // 2. Get related entities (user, pet) using entityService
            // 3. Implement rejection criteria logic based on business rules
            // 4. Return the evaluation result WITHOUT modifying entities

            // For demonstration, we'll simulate the rejection logic
            boolean shouldReject = evaluateRejectionCriteria();

            System.out.println("CheckRequestRejectionCriterion: Evaluation result = " + shouldReject);

            // Return the evaluation result
            return new EntityCriteriaCalculationResponse();

        } catch (Exception e) {
            System.err.println("CheckRequestRejectionCriterion: Error evaluating rejection criteria - " + e.getMessage());
            return new EntityCriteriaCalculationResponse();
        }
    }

    private boolean evaluateRejectionCriteria() {
        // Implement business logic for rejection
        // For demonstration purposes, we'll use simple criteria:

        // In a real implementation, this would:
        // 1. Check if user has incomplete information
        // 2. Verify pet availability
        // 3. Apply business rules for rejection
        // 4. Return rejection decision

        // For now, we'll simulate rejection logic
        // This should be the inverse of approval to ensure mutual exclusivity
        long currentTime = System.currentTimeMillis();
        boolean shouldReject = (currentTime % 10) >= 7; // 30% rejection rate, inverse of approval

        if (shouldReject) {
            System.out.println("Rejection reason: Criteria not met for adoption");
        } else {
            System.out.println("No rejection criteria met - request can proceed");
        }

        return shouldReject;
    }

    @Override
    public boolean supports(OperationSpecification opsSpec) {
        return "CheckRequestRejectionCriterion".equals(opsSpec.operationName());
    }
}
