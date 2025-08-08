package com.java_template.application.orchestrator;

import com.cyoda.plugins.mapping.entity.CyodaEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JobWorkflowOrchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(JobWorkflowOrchestrator.class);
    
    @Autowired
    private ProcessorsFactory processorsFactory;
    
    @Autowired
    private CriteriaFactory criteriaFactory;
    
    public String run(String technicalId, CyodaEntity entity, String transition) {
        logger.info("Running {} workflow orchestrator for transition: {}", "Job", transition);
        
        String nextTransition = transition;
        
        try {
            if ("start_ingestion".equals(transition)) {
                nextTransition = "INGESTING";
            }

            if ("ingestion_success".equals(transition)) {
                processorsFactory.get("FetchNobelLaureatesProcessor").process(technicalId, entity);
                nextTransition = "SUCCEEDED";
            }

            if ("ingestion_failure".equals(transition)) {
                nextTransition = "FAILED";
            }

            if ("notify_subscribers".equals(transition)) {
                processorsFactory.get("NotifySubscribersProcessor").process(technicalId, entity);
                nextTransition = "NOTIFIED_SUBSCRIBERS";
            }

            if ("notify_subscribers".equals(transition)) {
                processorsFactory.get("NotifySubscribersProcessor").process(technicalId, entity);
                nextTransition = "NOTIFIED_SUBSCRIBERS";
            }
        } catch (Exception e) {
            logger.error("Error processing transition: " + transition, e);
            nextTransition = "error_state";
        }
        
        logger.info("Transition {} resulted in next state: {}", transition, nextTransition);
        return nextTransition;
    }
}