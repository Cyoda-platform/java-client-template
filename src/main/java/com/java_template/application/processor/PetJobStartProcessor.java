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
public class PetJobStartProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetJobStartProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetJobStartProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetJob for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(PetJob.class)
            .validate(this::isValidEntity, "Invalid PetJob state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobStartProcessor".equals(modelSpec.operationName()) &&
               "petjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(PetJob petJob) {
        // Basic validation example - ensure jobId and jobType are not null/blank
        return petJob.getJobId() != null && !petJob.getJobId().isBlank() &&
               petJob.getJobType() != null && !petJob.getJobType().isBlank() &&
               petJob.getStatus() != null && !petJob.getStatus().isBlank();
    }

    private PetJob processEntityLogic(PetJob petJob) {
        // Business logic based on functional requirements from prototype
        // 1. Validate jobType and payload structure already done in isValidEntity

        // 2. Execution: perform action based on jobType
        // For simplicity, simulate execution by updating status

        switch (petJob.getJobType()) {
            case "AddPet":
                // Simulate adding pet logic
                petJob.setStatus("PROCESSING");
                break;
            case "UpdatePetInfo":
                // Simulate updating pet info logic
                petJob.setStatus("PROCESSING");
                break;
            default:
                // Unknown jobType, mark as FAILED
                petJob.setStatus("FAILED");
                break;
        }

        // 3. Event creation and notification would be handled downstream outside this processor

        return petJob;
    }
}
