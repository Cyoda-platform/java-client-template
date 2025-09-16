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

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Processor for validating pets before making them available.
 * Handles the make_available transition from draft to available.
 */
@Component
public class PetValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetValidationProcessor.class);
    private final ProcessorSerializer serializer;

    public PetValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing pet validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(pet -> pet.getName() != null && !pet.getName().trim().isEmpty(), 
                     "Pet name must not be empty")
            .validate(pet -> pet.getName().length() <= 100, 
                     "Pet name must be 100 characters or less")
            .validate(pet -> pet.getPhotoUrls() != null && !pet.getPhotoUrls().isEmpty(), 
                     "At least one photo URL must be provided")
            .validate(pet -> validatePhotoUrls(pet), 
                     "All photo URLs must be valid HTTP/HTTPS URLs")
            .map(processingContext -> {
                Pet pet = processingContext.entity();
                
                // Set last modified timestamp (conceptually)
                logger.info("Validated pet with ID: {} and name: {}", pet.getId(), pet.getName());
                return pet;
            })
            .complete();
    }

    private boolean validatePhotoUrls(Pet pet) {
        if (pet.getPhotoUrls() == null) {
            return false;
        }
        
        for (String photoUrl : pet.getPhotoUrls()) {
            if (photoUrl == null || photoUrl.trim().isEmpty()) {
                return false;
            }
            
            try {
                URL url = new URL(photoUrl);
                String protocol = url.getProtocol().toLowerCase();
                if (!"http".equals(protocol) && !"https".equals(protocol)) {
                    return false;
                }
            } catch (MalformedURLException e) {
                return false;
            }
        }
        
        return true;
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "PetValidationProcessor".equals(opSpec.operationName()) &&
               "Pet".equals(opSpec.modelKey().getName()) &&
               opSpec.modelKey().getVersion() >= 1;
    }
}
