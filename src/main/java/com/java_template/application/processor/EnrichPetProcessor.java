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
import java.util.List;
import java.util.Objects;

@Component
public class EnrichPetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichPetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichPetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid pet state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet pet) {
        return pet != null && pet.getTechnicalId() != null;
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();
        // Idempotency: if pet already progressed beyond INGESTED/ENRICHING/PENDING, skip
        String status = pet.getStatus();
        if (status != null) {
            String s = status.toUpperCase();
            if (!("INGESTED".equals(s) || "ENRICHING".equals(s) || "PENDING".equals(s))) {
                logger.info("Pet {} has status {} - skipping enrichment", pet.getTechnicalId(), status);
                return pet;
            }
        }

        // Simple enrichment: normalize species and merge photos list de-duplicating by url
        if (pet.getSpecies() != null) {
            pet.setSpecies(pet.getSpecies().trim().toLowerCase());
        }

        List<?> photos = pet.getPhotos();
        if (photos == null) {
            pet.setPhotos(new ArrayList<>());
        } else {
            // basic dedup by url if Photo has getUrl - we operate generically to avoid assumptions
            List<Object> unique = new ArrayList<>();
            for (Object p : pet.getPhotos()) {
                if (p == null) continue;
                if (!unique.contains(p)) unique.add(p);
            }
            pet.setPhotos(unique);
        }

        // Determine completeness: must have name and species and (at least one photo or description)
        boolean hasName = pet.getName() != null && !pet.getName().trim().isEmpty();
        boolean hasSpecies = pet.getSpecies() != null && !pet.getSpecies().trim().isEmpty();
        boolean hasPhoto = pet.getPhotos() != null && !pet.getPhotos().isEmpty();
        boolean hasDescription = pet.getDescription() != null && !pet.getDescription().trim().isEmpty();

        if (hasName && hasSpecies && (hasPhoto || hasDescription)) {
            pet.setStatus("AVAILABLE");
            logger.info("Pet {} marked AVAILABLE by enrichment", pet.getTechnicalId());
            // event emission handled elsewhere
        } else {
            pet.setStatus("PENDING");
            logger.info("Pet {} marked PENDING by enrichment - missing required fields", pet.getTechnicalId());
        }

        // bump version if present
        if (pet.getVersion() != null) {
            pet.setVersion(pet.getVersion() + 1);
        }

        return pet;
    }
}
