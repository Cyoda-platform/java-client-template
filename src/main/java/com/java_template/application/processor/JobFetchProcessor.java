package com.java_template.application.processor;

import com.java_template.application.entity.PurrfectPetsJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
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
public class JobFetchProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public JobFetchProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("JobFetchProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PurrfectPetsJob fetch for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PurrfectPetsJob.class)
                .validate(PurrfectPetsJob::isValid)
                .map(this::processFetchJobLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "JobFetchProcessor".equals(modelSpec.operationName()) &&
                "purrfectpetsjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PurrfectPetsJob processFetchJobLogic(PurrfectPetsJob job) {
        // Business logic to fetch pets via Petstore API and create Pet entities
        // Assuming EntityService is injected if needed for fetching or creating entities

        // Example logic based on functional requirement:
        // 1. Verify actionType is FETCH_PETS
        if (!"FETCH_PETS".equalsIgnoreCase(job.getActionType())) {
            logger.warn("Job actionType is not FETCH_PETS, skipping fetch logic");
            return job;
        }

        // 2. Here you would typically call external Petstore API to fetch pets
        // (This code is illustrative and should be replaced with actual API calls)

        // 3. Process and create Pet entities from fetched data
        // For demo, we simulate creation of pets (in real code, call EntityService.addItem or similar)

        // Since no direct update of PurrfectPetsJob entity except status, we just log
        logger.info("Simulated fetch of pets for jobId: {}", job.getJobId());

        // 4. Set job status to PROCESSING or COMPLETED based on your processing
        job.setStatus("PROCESSING");

        return job;
    }
}
