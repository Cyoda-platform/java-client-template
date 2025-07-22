package com.java_template.application.processor;

import com.java_template.application.entity.Pet;
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

import java.util.Collections;
import java.util.concurrent.ExecutionException;

@Component
public class PetProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public PetProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Pet.class)
                .validate(Pet::isValid)
                .map(this::processPetLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetProcessor".equals(modelSpec.operationName()) &&
               "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Pet processPetLogic(Pet pet) {
        logger.info("Processing Pet with technicalId: {}", pet.getTechnicalId());

        if (pet.getName() == null || pet.getName().isBlank() || pet.getCategory() == null || pet.getCategory().isBlank()) {
            logger.error("Pet processing failed: name or category is blank");
            return pet;
        }

        boolean changed = false;
        if (pet.getTags() == null || pet.getTags().isEmpty()) {
            pet.setTags(Collections.singletonList("default"));
            logger.info("Added default tag to Pet technicalId: {}", pet.getTechnicalId());
            changed = true;
        }

        if (pet.getPhotoUrls() == null || pet.getPhotoUrls().isEmpty()) {
            pet.setPhotoUrls(Collections.singletonList("http://example.com/default-pet.jpg"));
            logger.info("Added default photo URL to Pet technicalId: {}", pet.getTechnicalId());
            changed = true;
        }

        if (changed) {
            try {
                entityService.updateItem("Pet", Config.ENTITY_VERSION, pet.getTechnicalId(), pet).get();
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Failed to update Pet entity for technicalId: {}", pet.getTechnicalId(), e);
                Thread.currentThread().interrupt();
            }
        }

        logger.info("Completed processing Pet with technicalId: {}", pet.getTechnicalId());
        return pet;
    }
}
