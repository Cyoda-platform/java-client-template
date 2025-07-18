package com.java_template.application.criteria;

import com.java_template.application.entity.AdoptionRequest;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AdoptionRequestApprovalCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public boolean evaluate(CyodaEventContext<?> context) {
        Object entity = context.getEvent().getEntity();
        if (entity instanceof AdoptionRequest) {
            AdoptionRequest request = (AdoptionRequest) entity;
            logger.info("Evaluating AdoptionRequestApprovalCriterion for request id: {}", request.getId());
            // Example approval logic: approve if message length < 100
            boolean approved = request.getMessage() != null && request.getMessage().length() < 100;
            logger.info("AdoptionRequest approval evaluation result: {}", approved);
            return approved;
        }
        logger.warn("AdoptionRequestApprovalCriterion evaluated on wrong entity type");
        return false;
    }
}
