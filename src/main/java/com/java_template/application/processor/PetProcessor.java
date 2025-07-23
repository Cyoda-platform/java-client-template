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
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.node.ObjectNode;

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

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(this::isValidPet)
            .map(this::processPetLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetProcessor".equals(modelSpec.operationName()) &&
               "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidPet(Pet pet) {
        return pet.getPetId() != null && !pet.getPetId().isBlank() &&
               pet.getName() != null && !pet.getName().isBlank() &&
               pet.getType() != null && !pet.getType().isBlank() &&
               pet.getStatus() != null && !pet.getStatus().isBlank();
    }

    private Pet processPetLogic(Pet pet) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Pet", Config.ENTITY_VERSION, pet.getTechnicalId());
            ObjectNode petNode = itemFuture.get();
            if (petNode == null) {
                logger.error("Pet not found during processing: {}", pet.getTechnicalId());
                return pet;
            }

            // Validate pet data completeness (redundant due to isValidPet but kept for prototype fidelity)
            String petId = petNode.path("petId").asText(null);
            String name = petNode.path("name").asText(null);
            String type = petNode.path("type").asText(null);
            String status = petNode.path("status").asText(null);

            if (petId == null || petId.isBlank() ||
                    name == null || name.isBlank() ||
                    type == null || type.isBlank() ||
                    status == null || status.isBlank()) {
                logger.error("Pet entity validation failed for technicalId: {}", pet.getTechnicalId());
                return pet;
            }

            // Enrich with fun facts (prototype static example)
            logger.info("Enriched Pet {} with fun facts", pet.getTechnicalId());

        } catch (Exception e) {
            logger.error("Error processing Pet with technicalId {}: {}", pet.getTechnicalId(), e.getMessage());
        }
        return pet;
    }
}
