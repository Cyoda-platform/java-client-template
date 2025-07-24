package com.java_template.application.processor;

import com.java_template.application.entity.Pet;
import com.java_template.common.serializer.ErrorInfo;
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

import java.util.List;
import java.util.stream.Collectors;

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

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid Pet entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetProcessor".equals(modelSpec.operationName()) &&
               "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(Pet pet) {
        return pet.isValid();
    }

    private Pet processEntityLogic(Pet pet) {
        // Business logic to process Pet entity
        // According to functional requirements, no specific complex logic given,
        // so we assume no modifications but validation already done.
        // We could add additional logic if needed here.

        // Example: Normalize the pet name (capitalize first letter)
        if (pet.getName() != null && !pet.getName().isBlank()) {
            String name = pet.getName();
            pet.setName(name.substring(0, 1).toUpperCase() + name.substring(1));
        }

        // Example: Ensure status is uppercase
        if (pet.getStatus() != null) {
            pet.setStatus(pet.getStatus().toUpperCase());
        }

        // Example: Remove empty tags
        if (pet.getTags() != null) {
            List<String> filteredTags = pet.getTags().stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .collect(Collectors.toList());
            pet.setTags(filteredTags);
        }

        // Could add more domain-specific logic if defined in prototype

        return pet;
    }
}
