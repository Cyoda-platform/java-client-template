package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ImportUpsertProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ImportUpsertProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ImportUpsertProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportUpsert for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid pet payload")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet pet) {
        return pet != null && ((pet.getPetId() != null && !pet.getPetId().isEmpty())
            || (pet.getName() != null && !pet.getName().isEmpty()));
    }

    private Pet processEntityLogic(ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();
        try {
            // Create or ensure pet in datastore via EntityService when possible
            if (pet.getPetId() == null || pet.getPetId().isEmpty()) {
                // assign a new UUID as petId
                String newId = UUID.randomUUID().toString();
                pet.setPetId(newId);
            }

            // ensure status set
            if (pet.getStatus() == null || pet.getStatus().isEmpty()) {
                pet.setStatus("CREATED");
            }

            // Persist via entityService.addItem
            ObjectNode petNode = objectMapper.valueToTree(pet);
            CompletableFuture<UUID> addFuture = entityService.addItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                petNode
            );
            addFuture.get();

            logger.info("Imported/Upserted pet {}", pet.getPetId());
            return pet;
        } catch (Exception e) {
            logger.error("Error during import upsert processing", e);
            if (pet != null) {
                pet.setStatus("FAILED");
                try {
                    pet.setDescription("Import upsert error: " + e.getMessage());
                } catch (Throwable ignore) {
                }
            }
            return pet;
        }
    }
}
