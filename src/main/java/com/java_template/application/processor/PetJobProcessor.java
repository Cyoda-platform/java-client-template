package com.java_template.application.processor;

import com.java_template.application.entity.PetJob;
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
                .validate(PetJob::isValid, "Invalid PetJob entity state")
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
        // Business logic derived from functional requirements for processPetJob
        // 1. Validate jobType and payload structure - handled by isValid and validation
        // 2. Perform action based on jobType
        switch (petJob.getJobType()) {
            case "AddPet":
                // Sample logic: Mark pet job status to PROCESSING
                petJob.setStatus("PROCESSING");
                // Additional logic to handle adding pet can be implemented if entity and service available
                break;
            case "UpdatePetInfo":
                // Sample logic: Mark pet job status to PROCESSING
                petJob.setStatus("PROCESSING");
                // Additional logic to handle updating pet info can be implemented if entity and service available
                break;
            default:
                // Unknown jobType - mark as FAILED
                petJob.setStatus("FAILED");
                break;
        }
        return petJob;
    }
}
