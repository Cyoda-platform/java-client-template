package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class AdoptPetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdoptPetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AdoptPetProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing adoption for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AdoptionRequest.class)
            .validate(this::isValidEntity, "Invalid adoption request")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AdoptionRequest req) {
        return req != null && req.getRequestId() != null && !req.getRequestId().isEmpty();
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest request = context.entity();
        try {
            String currentStatus = request.getStatus();
            // Only process APPROVED or SUBMITTED depending on business rules
            if (currentStatus != null && !("APPROVED".equals(currentStatus) || "SUBMITTED".equals(currentStatus))) {
                logger.info("AdoptionRequest {} in status {} - skipping adopt logic", request.getRequestId(), currentStatus);
                return request;
            }

            // guard: ensure petId is present
            if (request.getPetId() == null || request.getPetId().isEmpty()) {
                request.setStatus("REJECTED");
                request.setNotes("Pet not specified");
                logger.warn("AdoptionRequest {} rejected because petId is missing", request.getRequestId());
                return request;
            }

            // Fetch pet by petId using EntityService
            try {
                SearchConditionRequest condition = SearchConditionRequest.group(
                    "AND",
                    Condition.of("$.petId", "EQUALS", request.getPetId())
                );
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    condition,
                    true
                );

                ArrayNode results = itemsFuture.get();
                if (results == null || results.size() == 0) {
                    request.setStatus("REJECTED");
                    request.setNotes("Pet not found");
                    logger.warn("AdoptionRequest {}: pet {} not found", request.getRequestId(), request.getPetId());
                    return request;
                }

                // take first matching pet
                JsonNode petNode = results.get(0);
                Pet pet = objectMapper.treeToValue(petNode, Pet.class);

                String petStatus = pet.getStatus();
                if (petStatus == null || (!"AVAILABLE".equals(petStatus) && !"PENDING".equals(petStatus))) {
                    request.setStatus("REJECTED");
                    request.setNotes("Pet not available for adoption (status=" + petStatus + ")");
                    logger.warn("AdoptionRequest {}: pet {} not available (status={})", request.getRequestId(), pet.getPetId(), petStatus);
                    return request;
                }

                // Attempt to mark pet as ADOPTED and persist via EntityService
                pet.setStatus("ADOPTED");
                String adoptedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
                String adopterInfo = "Adopted by " + (request.getRequesterName() == null ? "<unknown>" : request.getRequesterName()) +
                    " (" + (request.getRequesterContact() == null ? "<unknown>" : request.getRequesterContact()) + ") at " + adoptedAt;
                // store adopter info in description (entity has no adopter object)
                String existingDesc = pet.getDescription();
                if (existingDesc == null) existingDesc = "";
                pet.setDescription((existingDesc.isEmpty() ? "" : existingDesc + " | ") + adopterInfo);

                // Persist pet update
                try {
                    // pet.petId is expected to be a UUID string in the backing store
                    UUID petUuid = UUID.fromString(pet.getPetId());
                    ObjectNode petNodeOut = objectMapper.valueToTree(pet);
                    CompletableFuture<UUID> updateFuture = entityService.updateItem(
                        Pet.ENTITY_NAME,
                        String.valueOf(Pet.ENTITY_VERSION),
                        petUuid,
                        petNodeOut
                    );
                    updateFuture.get();

                    // Mark adoption request completed
                    request.setStatus("COMPLETED");
                    request.setNotes("Adoption completed for pet " + pet.getPetId());
                    logger.info("AdoptionRequest {} completed and pet {} marked ADOPTED", request.getRequestId(), pet.getPetId());
                    return request;
                } catch (IllegalArgumentException iae) {
                    // petId is not a UUID - cannot update via entityService.updateItem
                    logger.error("Pet id {} is not a UUID; cannot perform update via EntityService", pet.getPetId());
                    request.setStatus("REJECTED");
                    request.setNotes("Pet identifier invalid in datastore: " + pet.getPetId());
                    return request;
                }

            } catch (Exception e) {
                logger.error("Error while processing adoption request {}: {}", request.getRequestId(), e.getMessage(), e);
                request.setStatus("REJECTED");
                request.setNotes("Adoption processing error: " + e.getMessage());
                return request;
            }

        } catch (Exception e) {
            logger.error("Unhandled error while processing adoption request {}", request == null ? "<null>" : request.getRequestId(), e);
            if (request != null) {
                request.setStatus("REJECTED");
                request.setNotes("Adoption processor error: " + e.getMessage());
            }
            return request;
        }
    }
}
