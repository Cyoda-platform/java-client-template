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
        logger.info("Processing Pet with id: {}", pet.getId());

        if (pet.getName() == null || pet.getName().isBlank()) {
            logger.error("Pet name is invalid");
            return pet;
        }

        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            logger.error("Pet status is invalid");
            return pet;
        }

        if (pet.getTags() != null) {
            List<String> normalizedTags = new ArrayList<>();
            for (String tag : pet.getTags()) {
                normalizedTags.add(tag.toLowerCase());
            }
            pet.setTags(normalizedTags);
        }

        logger.info("Pet {} processed successfully", pet.getId());
        return pet;
    }

}
