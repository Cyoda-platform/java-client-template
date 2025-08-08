package com.java_template.application.orchestrator;

import com.cyoda.plugins.mapping.entity.CyodaEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SubscriberWorkflowOrchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(SubscriberWorkflowOrchestrator.class);
    
    @Autowired
    private ProcessorsFactory processorsFactory;
    
    @Autowired
    private CriteriaFactory criteriaFactory;
    
    public String run(String technicalId, CyodaEntity entity, String transition) {
        logger.info("Running {} workflow orchestrator for transition: {}", "Subscriber", transition);
        
        String nextTransition = transition;
        
        try {
            if ("is_active".equals(transition)) {
                processorsFactory.get("SendNotificationProcessor").process(technicalId, entity);
                if (criteriaFactory.get("IsSubscriberActiveCriterion").check(technicalId, entity)) {
                    nextTransition = "notified";
                } else {
                    nextTransition = "failed";
                }
            }

            if ("is_inactive".equals(transition)) {
                if (criteriaFactory.get("IsSubscriberInactiveCriterion").check(technicalId, entity)) {
                    nextTransition = "inactive";
                } else {
                    nextTransition = "failed";
                }
            }
        } catch (Exception e) {
            logger.error("Error processing transition: " + transition, e);
            nextTransition = "error_state";
        }
        
        logger.info("Transition {} resulted in next state: {}", transition, nextTransition);
        return nextTransition;
    }
}