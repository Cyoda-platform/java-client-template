package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1000.Job;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class FetchNobelLaureatesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchNobelLaureatesProcessor.class);
    private final ProcessorSerializer serializer;

    public FetchNobelLaureatesProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());
        // In a real scenario, here you would fetch data from OpenDataSoft API.
        // For this example, we simulate processing by returning the request entity unchanged.

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "FetchNobelLaureatesProcessor".equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.getJobName() != null && !entity.getJobName().isEmpty();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job entity = context.entity();
        // Implement business logic for fetching and ingesting Nobel laureates
        // This is a stub - actual HTTP call and data processing would be here
        logger.info("Fetching Nobel laureates data for job: {}", entity.getJobName());

        // Simulate success by returning entity as is
        return entity;
    }
}
