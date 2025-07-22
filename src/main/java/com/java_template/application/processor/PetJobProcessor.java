package com.java_template.application.processor;

import com.java_template.application.entity.PetJob;
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

        return serializer.withRequest(request)
            .toEntity(PetJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobProcessor".equals(modelSpec.operationName()) &&
               "petjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(PetJob entity) {
        // Example validation, adjust if needed based on PetJob properties
        return entity != null && entity.getJobType() != null && !entity.getJobType().isBlank();
    }

    private PetJob processEntityLogic(PetJob entity) {
        // Business logic based on functional requirements:
        // 1. Validate jobType and payload structure - assumed done in isValidEntity
        // 2. Perform action based on jobType (e.g., add pet, update pet info)
        // 3. Generate corresponding PetEvent (not implemented here as it might be separate processor)
        // 4. Update status to COMPLETED or FAILED based on process

        String jobType = entity.getJobType();
        switch (jobType) {
            case "AddPet":
                // Logic to add a pet based on payload
                // For example, extract pet details from payload and create Pet entity
                // This is a simplified placeholder logic
                entity.setStatus("COMPLETED");
                break;
            case "UpdatePetInfo":
                // Logic to update pet info
                entity.setStatus("COMPLETED");
                break;
            default:
                entity.setStatus("FAILED");
                break;
        }
        return entity;
    }
}
