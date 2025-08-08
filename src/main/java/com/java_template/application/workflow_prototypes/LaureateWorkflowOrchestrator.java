package com.java_template.application.orchestrator;

import com.cyoda.plugins.mapping.entity.CyodaEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class LaureateWorkflowOrchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(LaureateWorkflowOrchestrator.class);
    
    @Autowired
    private ProcessorsFactory processorsFactory;
    
    @Autowired
    private CriteriaFactory criteriaFactory;
    
    public String run(String technicalId, CyodaEntity entity, String transition) {
        logger.info("Running {} workflow orchestrator for transition: {}", "Laureate", transition);
        
        String nextTransition = transition;
        
        try {
            if ("validation_passed".equals(transition)) {
                processorsFactory.get("LaureateValidationProcessor").process(technicalId, entity);
                nextTransition = "enrichment";
            }

            if ("validation_failed".equals(transition)) {
                nextTransition = "discarded";
            }

            if ("enrichment_complete".equals(transition)) {
                processorsFactory.get("LaureateEnrichmentProcessor").process(technicalId, entity);
                nextTransition = "persisted";
            }
        } catch (Exception e) {
            logger.error("Error processing transition: " + transition, e);
            nextTransition = "error_state";
        }
        
        logger.info("Transition {} resulted in next state: {}", transition, nextTransition);
        return nextTransition;
    }
}