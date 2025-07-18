package com.java_template.application.processor;

import com.java_template.application.entity.Job;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public JobProcessor() {
        logger.info("JobProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request id: {}", request.getId());

        // Extract entity and perform validation
        Job job = context.getSerializer().extractEntity(request, Job.class);

        if (!job.isValid()) {
            logger.error("Invalid Job entity state");
            return context.getSerializer().responseBuilder(request)
                .withError("Invalid Job entity")
                .build();
        }

        // Business logic from prototype is just logging
        logger.info("Processing job id: {}, type: {}, status: {}", job.getId(), job.getType(), job.getStatus());

        // Return success response with the entity unchanged
        return context.getSerializer().responseBuilder(request)
                .withEntity(job)
                .build();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "JobProcessor".equals(modelSpec.operationName()) &&
               "job".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
