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
public class FetchNobelLaureatesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchNobelLaureatesProcessor.class);

    @Autowired
    private ProcessorSerializer serializer;

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        // In a real scenario, here you would fetch data from OpenDataSoft API.
        // For this example, we simulate processing by returning the request entity unchanged.

        return serializer.withRequest(request)
            .toEntity(com.java_template.application.entity.Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "FetchNobelLaureatesProcessor".equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(com.java_template.application.entity.Job entity) {
        return entity != null && entity.getJobName() != null && !entity.getJobName().isEmpty();
    }

    private com.java_template.application.entity.Job processEntityLogic(com.java_template.application.entity.Job entity) {
        // Implement business logic for fetching and ingesting Nobel laureates
        // This is a stub - actual HTTP call and data processing would be here
        logger.info("Fetching Nobel laureates data for job: {}", entity.getJobName());

        // Simulate success by returning entity as is
        return entity;
    }
}
