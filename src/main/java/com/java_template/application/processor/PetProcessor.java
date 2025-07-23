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

import java.util.ArrayList;
import java.util.List;

@Component
public class PetProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Pet.class)
                .validate(Pet::isValid, "Invalid entity state")
                .map(this::processPet)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetProcessor".equals(modelSpec.operationName()) &&
                "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Pet processPet(Pet pet) {
        logger.info("Processing Pet with technicalId: {}", pet.getTechnicalId());

        if (pet.getName() == null || pet.getName().isBlank() ||
                pet.getCategory() == null || pet.getCategory().isBlank()) {
            logger.error("Pet validation failed for technicalId {}: Missing name or category", pet.getTechnicalId());
            return pet; // Return as is if validation fails
        }

        if (pet.getTags() != null) {
            List<String> normalizedTags = new ArrayList<>();
            for (String tag : pet.getTags()) {
                if (tag != null) {
                    normalizedTags.add(tag.toLowerCase());
                }
            }
            pet.setTags(normalizedTags);
        }

        if (pet.getPhotoUrls() != null) {
            List<String> validUrls = new ArrayList<>();
            for (String url : pet.getPhotoUrls()) {
                if (url != null && url.startsWith("http")) {
                    validUrls.add(url);
                } else {
                    logger.warn("Invalid photo URL skipped for Pet technicalId {}: {}", pet.getTechnicalId(), url);
                }
            }
            pet.setPhotoUrls(validUrls);
        }

        logger.info("Pet with technicalId {} processed successfully", pet.getTechnicalId());
        return pet;
    }

}