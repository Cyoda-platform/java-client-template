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

import java.util.List;

@Component
public class MediaCheckProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MediaCheckProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public MediaCheckProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet media check for request: {}", request.getId());

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
            String photo = pet.getPhotoUrl();
            List<String> tags = pet.getTags();

            boolean hasPhoto = photo != null && !photo.trim().isEmpty();
            boolean hasTags = tags != null && !tags.isEmpty();

            if (hasPhoto && hasTags) {
                pet.setMetadataVerified(true);
                pet.setStatus("MEDIA_VERIFIED");
                logger.info("Pet {} media OK - marked MEDIA_VERIFIED", pet.getTechnicalId());
            } else {
                pet.setMetadataVerified(false);
                // leave status unchanged; workflow will route to manual review on failure
                logger.info("Pet {} media incomplete - scheduling manual review", pet.getTechnicalId());
            }
        } catch (Exception e) {
            logger.error("Error during MediaCheckProcessor for pet {}: {}", pet == null ? "<null>" : pet.getTechnicalId(), e.getMessage(), e);
        }
        return pet;
    }
}
