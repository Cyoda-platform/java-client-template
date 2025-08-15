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
public class FinalizeRejectionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FinalizeRejectionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FinalizeRejectionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FinalizeRejection for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AdoptionRequest.class)
            .validate(this::isValidEntity, "Invalid adoption request entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AdoptionRequest entity) {
        return entity != null && entity.getPetTechnicalId() != null;
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest ar = context.entity();
        try {
            ar.setStatus("REJECTED");
            // release reservation if present in nested pet payload
            Pet pet = ar.getPet();
            if (pet != null && pet.getReservation() != null && ar.getTechnicalId() != null
                && ar.getTechnicalId().equals(pet.getReservation().getRequestTechnicalId())) {
                pet.setStatus("AVAILABLE");
                pet.setReservation(null);
            }
            logger.info("AdoptionRequest {} finalized as REJECTED", ar.getTechnicalId());
        } catch (Exception e) {
            logger.error("Error during FinalizeRejectionProcessor for request {}: {}", ar == null ? "<null>" : ar.getTechnicalId(), e.getMessage(), e);
        }
        return ar;
    }
}
