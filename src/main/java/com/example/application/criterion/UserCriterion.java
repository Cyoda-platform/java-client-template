package com.example.application.criterion;

import com.example.application.entity.user.version_1.User;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.workflow.CyodaCriterion;
import org.cyoda.cloud.api.event.common.condition.CriterionEvaluationRequest;
import org.cyoda.cloud.api.event.common.condition.CriterionEvaluationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UserCriterion - Evaluates conditions for User workflow transitions
 * 
 * This criterion can be used to determine if a user meets certain conditions
 * for workflow transitions.
 */
@Component
public class UserCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(UserCriterion.class);

    @Override
    public CriterionEvaluationResponse evaluate(CriterionEvaluationRequest request) {
        logger.debug("Evaluating UserCriterion for request: {}", request.getId());

        // Example: Check if user is present in Auth0
        boolean result = evaluateUserPresence(request);

        CriterionEvaluationResponse response = new CriterionEvaluationResponse();
        response.setId(request.getId());
        response.setResult(result);

        logger.debug("UserCriterion evaluation result: {}", result);
        return response;
    }

    private boolean evaluateUserPresence(CriterionEvaluationRequest request) {
        // This is a placeholder implementation
        // In a real scenario, you would extract and evaluate the entity data
        return true;
    }
}

