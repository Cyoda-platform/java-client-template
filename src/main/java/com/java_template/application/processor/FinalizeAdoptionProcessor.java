package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.owner.version_1.Owner;
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
            .validate(this::isValidEntity, "Invalid adoption request entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AdoptionRequest entity) {
        return entity != null && entity.getPetTechnicalId() != null && entity.getOwnerTechnicalId() != null;
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest ar = context.entity();
        try {
            Pet pet = ar.getPet();
            Owner owner = ar.getOwner();

            if (pet == null || owner == null) {
                logger.warn("Cannot finalize adoption {} because pet or owner payload missing", ar.getTechnicalId());
                ar.setStatus("NEEDS_REVIEW");
                return ar;
            }

            if ("RESERVED".equalsIgnoreCase(pet.getStatus()) && pet.getReservation() != null && ar.getTechnicalId() != null
                && ar.getTechnicalId().equals(pet.getReservation().getRequestTechnicalId())) {

                // finalize
                pet.setStatus("ADOPTED");
                pet.setReservation(null);

                if (owner.getAdoptionHistory() == null) {
                    owner.setAdoptionHistory(new java.util.ArrayList<>());
                }
                owner.getAdoptionHistory().add(ar.getTechnicalId());

                ar.setStatus("APPROVED");
                ar.setProcessedBy("system");

                logger.info("Adoption {} finalized: pet {} adopted by owner {}", ar.getTechnicalId(), pet.getTechnicalId(), owner.getTechnicalId());
            } else {
                logger.warn("Cannot finalize adoption {} due to reservation mismatch or pet not reserved", ar.getTechnicalId());
                ar.setStatus("NEEDS_REVIEW");
            }
        } catch (Exception e) {
            logger.error("Error during FinalizeAdoptionProcessor for request {}: {}", ar == null ? "<null>" : ar.getTechnicalId(), e.getMessage(), e);
        }
        return ar;
    }
}
