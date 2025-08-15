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
public class ScreeningProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ScreeningProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ScreeningProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Screening for request: {}", request.getId());

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
            // Move to SCREENING state handled by workflow engine. Implement screening checks here.
            ar.setStatus("SCREENING");

            // Load owner and pet entities - in this prototype we assume serializer provides payload with nested entities
            Owner owner = ar.getOwner();
            Pet pet = ar.getPet();

            boolean ownerVerified = false;
            boolean petAvailable = false;

            if (owner != null) {
                ownerVerified = Boolean.TRUE.equals(owner.getVerified());
            }
            if (pet != null) {
                petAvailable = "AVAILABLE".equalsIgnoreCase(pet.getStatus());
            }

            if (ownerVerified && petAvailable) {
                // Attempt to reserve pet. In absence of DB locks here, we attempt a best-effort reservation by marking
                if (pet != null) {
                    pet.setStatus("RESERVED");
                    pet.setReservation(new com.java_template.application.entity.pet.version_1.Pet.Reservation(ar.getTechnicalId(), java.time.Instant.now().toString(), java.time.Instant.now().plusSeconds(60 * 60).toString()));
                    ar.setStatus("READY_TO_REVIEW");
                    logger.info("Screening succeeded and reserved pet {} for request {}", pet.getTechnicalId(), ar.getTechnicalId());
                }
            } else {
                ar.setStatus("NEEDS_REVIEW");
                logger.info("Screening failed for request {} - ownerVerified={} petAvailable={}", ar.getTechnicalId(), ownerVerified, petAvailable);
            }

        } catch (Exception e) {
            logger.error("Error during ScreeningProcessor for request {}: {}", ar == null ? "<null>" : ar.getTechnicalId(), e.getMessage(), e);
        }
        return ar;
    }
}
