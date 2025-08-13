package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.workflow.version_1.Workflow;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ProcessWorkflowProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessWorkflowProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ProcessWorkflowProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Workflow for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Workflow.class)
            .validate(this::isValidEntity, "Invalid Workflow state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Workflow entity) {
        return entity != null && entity.getName() != null && !entity.getName().isEmpty()
                && entity.getInputPetData() != null && !entity.getInputPetData().isEmpty();
    }

    private Workflow processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Workflow> context) {
        Workflow workflow = context.entity();

        // Update status to RUNNING
        workflow.setStatus("RUNNING");
        logger.info("Workflow status set to RUNNING for id: {}", workflow.getTechnicalId());

        String inputPetData = workflow.getInputPetData();
        List<CompletableFuture<UUID>> petFutures = new ArrayList<>();

        try {
            JsonNode rootNode = objectMapper.readTree(inputPetData);
            if (rootNode.isArray()) {
                ArrayNode arrayNode = (ArrayNode) rootNode;
                for (JsonNode petNode : arrayNode) {
                    Pet pet = new Pet();
                    pet.setPetId(petNode.path("petId").asText(null));
                    pet.setName(petNode.path("name").asText(null));
                    pet.setCategory(petNode.path("category").asText("unknown"));
                    pet.setStatus(petNode.path("status").asText("available"));
                    // photoUrls and tags as comma-separated strings
                    if (petNode.has("photoUrls") && petNode.get("photoUrls").isArray()) {
                        List<String> photos = new ArrayList<>();
                        for (JsonNode photoNode : petNode.get("photoUrls")) {
                            photos.add(photoNode.asText());
                        }
                        pet.setPhotoUrls(String.join(",", photos));
                    } else {
                        pet.setPhotoUrls("");
                    }
                    if (petNode.has("tags") && petNode.get("tags").isArray()) {
                        List<String> tags = new ArrayList<>();
                        for (JsonNode tagNode : petNode.get("tags")) {
                            tags.add(tagNode.asText());
                        }
                        pet.setTags(String.join(",", tags));
                    } else {
                        pet.setTags("");
                    }
                    pet.setCreatedAt(java.time.Instant.now().toString());

                    // Validate pet required fields
                    if (pet.getPetId() == null || pet.getPetId().isEmpty() || pet.getName() == null || pet.getName().isEmpty() || pet.getCategory() == null || pet.getCategory().isEmpty() || pet.getStatus() == null || pet.getStatus().isEmpty()) {
                        logger.warn("Skipping pet due to missing required fields: {}", pet);
                        continue; // skip invalid pet
                    }

                    // Persist pet entity asynchronously
                    CompletableFuture<UUID> future = entityService.addItem(
                            Pet.ENTITY_NAME,
                            String.valueOf(Pet.ENTITY_VERSION),
                            pet
                    );
                    petFutures.add(future);
                    logger.info("Scheduled persistence for Pet with petId: {}", pet.getPetId());
                }
            } else {
                logger.error("Input pet data is not an array");
                workflow.setStatus("FAILED");
                return workflow;
            }
        } catch (Exception e) {
            logger.error("Failed to parse inputPetData JSON", e);
            workflow.setStatus("FAILED");
            return workflow;
        }

        // Wait for all pet persistence operations to complete
        try {
            CompletableFuture.allOf(petFutures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            logger.error("Error during pet persistence", e);
            workflow.setStatus("FAILED");
            return workflow;
        }

        workflow.setStatus("COMPLETED");
        logger.info("Workflow processing completed for id: {}", workflow.getTechnicalId());

        return workflow;
    }
}
