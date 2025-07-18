package com.java_template.application.processor;

import com.java_template.application.entity.PetJob;
import com.java_template.application.entity.Pet;
import com.java_template.common.serializer.ErrorInfo;
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
public class PetJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetJobProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetJob for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(PetJob.class)
            .validate(PetJob::isValid, "Invalid PetJob state")
            .map(this::processPetJobLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobProcessor".equals(modelSpec.operationName()) &&
               "petjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetJob processPetJobLogic(PetJob petJob) {
        logger.info("Processing PetJob with jobId: {} and action: {}", petJob.getJobId(), petJob.getAction());

        // Actual business logic for PetJob processing
        // Validate action and petId consistency
        if (petJob.getAction() == null || petJob.getAction().isBlank()) {
            petJob.setStatus("FAILED");
            return petJob;
        }

        // Example of processing CREATE, UPDATE, DELETE actions
        // In real implementation, Pet entity service calls would be here
        switch (petJob.getAction()) {
            case "CREATE":
                // Create a new Pet entity (simulation)
                logger.info("Create action for PetId: {}", petJob.getPetId());
                petJob.setStatus("PROCESSING");
                break;
            case "UPDATE":
                // Update existing Pet entity (simulation)
                logger.info("Update action for PetId: {}", petJob.getPetId());
                petJob.setStatus("PROCESSING");
                break;
            case "DELETE":
                // Delete Pet entity (simulation)
                logger.info("Delete action for PetId: {}", petJob.getPetId());
                petJob.setStatus("PROCESSING");
                break;
            default:
                petJob.setStatus("FAILED");
                break;
        }

        // Finalize processing (example)
        // In real scenario, update status to COMPLETED or FAILED based on actual processing
        petJob.setStatus("COMPLETED");
        return petJob;
    }

}
