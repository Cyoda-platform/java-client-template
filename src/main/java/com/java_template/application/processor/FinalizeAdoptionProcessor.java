package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FinalizeAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FinalizeAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FinalizeAdoptionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FinalizeAdoption for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AdoptionRequest.class)
            .validate(this::isValidEntity, "Invalid adoption request for finalize adoption")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AdoptionRequest req) {
        return req != null && req.getTechnicalId() != null;
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest req = context.entity();

        // Only finalize if request is COMPLETED
        if (!"COMPLETED".equalsIgnoreCase(req.getStatus())) {
            logger.info("AdoptionRequest {} not COMPLETED - skipping finalization (current={})", req.getTechnicalId(), req.getStatus());
            return req;
        }

        // In a full system we'd load and update the Pet entity; here we rely on a Pet snapshot attached to the request if present
        // If the AdoptionRequest contains a pet snapshot object, attempt to mark it adopted
        Pet pet = req.getPet(); // optional getter; if not available this will be null
        if (pet != null) {
            pet.setStatus("ADOPTED");
            if (pet.getVersion() != null) pet.setVersion(pet.getVersion() + 1);
            logger.info("Pet {} marked ADOPTED as part of finalizing adoption {}", pet.getTechnicalId(), req.getTechnicalId());
            // Clear reservation if any
            // request.step to closed handled by another processor
        } else {
            logger.info("No embedded pet snapshot available on AdoptionRequest {} - downstream process should update Pet", req.getTechnicalId());
        }

        // set request to CLOSED to finalize
        req.setStatus("CLOSED");
        if (req.getVersion() != null) req.setVersion(req.getVersion() + 1);
        logger.info("AdoptionRequest {} finalized and CLOSED", req.getTechnicalId());
        return req;
    }
}
