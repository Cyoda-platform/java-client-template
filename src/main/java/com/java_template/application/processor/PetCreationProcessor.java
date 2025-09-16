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

import java.util.ArrayList;
import java.util.UUID;

/**
 * Processor for creating new pets in the system.
 * Handles the create_pet transition from initial_state to draft.
 */
@Component
public class PetCreationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetCreationProcessor.class);
    private final ProcessorSerializer serializer;

    public PetCreationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing pet creation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(pet -> pet.getName() != null && !pet.getName().trim().isEmpty(), 
                     "Pet name is required")
            .validate(pet -> pet.getPhotoUrls() != null && !pet.getPhotoUrls().isEmpty(), 
                     "At least one photo URL is required")
            .map(processingContext -> {
                Pet pet = processingContext.entity();
                
                // Set default values if not provided
                if (pet.getId() == null) {
                    pet.setId(System.currentTimeMillis()); // Simple ID generation
                }
                
                // Initialize empty tags list if not provided
                if (pet.getTags() == null) {
                    pet.setTags(new ArrayList<>());
                }
                
                logger.info("Created pet with ID: {} and name: {}", pet.getId(), pet.getName());
                return pet;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "PetCreationProcessor".equals(opSpec.operationName()) &&
               "Pet".equals(opSpec.modelKey().getName()) &&
               opSpec.modelKey().getVersion() >= 1;
    }
}
