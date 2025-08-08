package com.java_template.application.processor;

import com.cyoda.plugins.mapping.entity.CyodaEntity;
import com.cyoda.plugins.mapping.entity.EntityProcessorCalculationRequest;
import com.cyoda.plugins.mapping.entity.EntityProcessorCalculationResponse;
import com.cyoda.plugins.mapping.entity.CyodaEventContext;
import com.cyoda.plugins.mapping.entity.OperationSpecification;
import com.cyoda.plugins.mapping.entity.CyodaProcessor;
import com.cyoda.plugins.mapping.entity.ProcessorSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class NotifySubscribersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifySubscribersProcessor.class);

    @Autowired
    private ProcessorSerializer serializer;

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job notification for request: {}", request.getId());

        // Stub for notification logic
        // In a real implementation, would retrieve active subscribers and send notifications

        return serializer.withRequest(request)
            .toEntity(com.java_template.application.entity.Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "NotifySubscribersProcessor".equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(com.java_template.application.entity.Job entity) {
        return entity != null && entity.getStatus() != null && !entity.getStatus().isEmpty();
    }

    private com.java_template.application.entity.Job processEntityLogic(com.java_template.application.entity.Job entity) {
        logger.info("Notifying subscribers for job: {}", entity.getJobName());
        // Simulated notification logic
        return entity;
    }
}
