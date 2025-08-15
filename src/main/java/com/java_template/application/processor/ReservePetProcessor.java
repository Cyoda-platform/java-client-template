package com.java_template.application.processor;

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
public class ReservePetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReservePetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReservePetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReservePet for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid pet entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet entity) {
        return entity != null;
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();
        try {
            if (pet.getReservation() == null && "AVAILABLE".equalsIgnoreCase(pet.getStatus())) {
                // In prototype we assume adoptionRequest id is provided in reservation.requestTechnicalId
                // If not present, do not reserve
                logger.info("Attempting to reserve pet {}", pet.getTechnicalId());
                pet.setStatus("RESERVED");
                if (pet.getReservation() == null) {
                    pet.setReservation(new Pet.Reservation("unknown", java.time.Instant.now().toString(), java.time.Instant.now().plusSeconds(60 * 60).toString()));
                }
            } else {
                logger.info("Pet {} could not be reserved - already reserved or not available", pet.getTechnicalId());
            }
        } catch (Exception e) {
            logger.error("Error during ReservePetProcessor for pet {}: {}", pet == null ? "<null>" : pet.getTechnicalId(), e.getMessage(), e);
        }
        return pet;
    }
}
