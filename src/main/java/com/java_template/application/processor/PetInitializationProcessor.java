package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * PetInitializationProcessor - Initialize pet data and set up for adoption
 */
@Component
public class PetInitializationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetInitializationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PetInitializationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet initialization for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Pet.class)
                .validate(this::isValidEntityWithMetadata, "Invalid pet entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Pet> entityWithMetadata) {
        Pet entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Initialize pet data and set up for adoption
     */
    private EntityWithMetadata<Pet> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Pet> context) {

        EntityWithMetadata<Pet> entityWithMetadata = context.entityResponse();
        Pet pet = entityWithMetadata.entity();

        logger.debug("Initializing pet: {}", pet.getPetId());

        // Set arrival date to current time if not already set
        if (pet.getArrivalDate() == null) {
            pet.setArrivalDate(LocalDateTime.now());
        }

        // Calculate adoption fee based on species and age if not already set
        if (pet.getAdoptionFee() == null) {
            pet.setAdoptionFee(calculateAdoptionFee(pet));
        }

        // Set default health status if not provided
        if (pet.getHealthStatus() == null || pet.getHealthStatus().trim().isEmpty()) {
            pet.setHealthStatus("Healthy");
        }

        // Set default vaccination status if not provided
        if (pet.getVaccinated() == null) {
            pet.setVaccinated(false);
        }

        // Set default spay/neuter status if not provided
        if (pet.getSpayedNeutered() == null) {
            pet.setSpayedNeutered(false);
        }

        logger.info("Pet {} initialized successfully with adoption fee: {}", 
                   pet.getPetId(), pet.getAdoptionFee());

        return entityWithMetadata;
    }

    /**
     * Calculate adoption fee based on species and age
     */
    private Double calculateAdoptionFee(Pet pet) {
        double baseFee = 100.0;
        
        // Adjust fee based on species
        if (pet.getSpecies() != null) {
            switch (pet.getSpecies().toLowerCase()) {
                case "dog":
                    baseFee = 250.0;
                    break;
                case "cat":
                    baseFee = 150.0;
                    break;
                case "bird":
                    baseFee = 75.0;
                    break;
                case "rabbit":
                    baseFee = 100.0;
                    break;
                default:
                    baseFee = 125.0;
            }
        }

        // Adjust fee based on age (younger pets typically have higher fees)
        if (pet.getAge() != null) {
            if (pet.getAge() < 1) {
                baseFee *= 1.5; // 50% increase for puppies/kittens
            } else if (pet.getAge() > 7) {
                baseFee *= 0.7; // 30% discount for senior pets
            }
        }

        return Math.round(baseFee * 100.0) / 100.0; // Round to 2 decimal places
    }
}
