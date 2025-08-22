package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ApproveAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ApproveAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ApproveAdoptionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet entity) {
        return entity != null && entity.isValid();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();
        try {
            if (pet.getAdoptionRequests() == null || pet.getAdoptionRequests().isEmpty()) {
                logger.info("No adoption requests found for pet: {}", pet.getId());
                return pet;
            }

            // Find the first pending adoption request to approve (idempotent: if already approved, do nothing)
            Optional<AdoptionRequest> pendingRequestOpt = pet.getAdoptionRequests().stream()
                .filter(req -> req != null && "pending".equalsIgnoreCase(req.getStatus()))
                .findFirst();

            if (!pendingRequestOpt.isPresent()) {
                logger.info("No pending adoption request to approve for pet: {}. Skipping.", pet.getId());
                return pet;
            }

            AdoptionRequest request = pendingRequestOpt.get();

            // Idempotency: if already approved, ensure pet status is adopted
            if ("approved".equalsIgnoreCase(request.getStatus())) {
                logger.info("Adoption request {} already approved for pet {}", request.getRequestId(), pet.getId());
                if (!"adopted".equalsIgnoreCase(pet.getStatus())) {
                    pet.setStatus("adopted");
                }
                return pet;
            }

            // Approve the request
            request.setStatus("approved");
            request.setProcessedAt(Instant.now().toString());

            // Update pet status to adopted
            pet.setStatus("adopted");

            // Update Owner: add pet id to owner's adoptedPets
            String ownerBusinessId = request.getOwnerId();
            if (ownerBusinessId != null && !ownerBusinessId.trim().isEmpty()) {
                try {
                    // Search for owner by business id (id field)
                    SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.id", "EQUALS", ownerBusinessId)
                    );

                    CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                        Owner.ENTITY_NAME,
                        String.valueOf(Owner.ENTITY_VERSION),
                        condition,
                        true
                    );

                    ArrayNode owners = itemsFuture.join();
                    if (owners == null || owners.size() == 0) {
                        logger.warn("Owner with id {} not found while approving adoption for pet {}", ownerBusinessId, pet.getId());
                    } else {
                        ObjectNode ownerNode = (ObjectNode) owners.get(0);
                        Owner owner = objectMapper.treeToValue(ownerNode, Owner.class);

                        List<String> adoptedPets = owner.getAdoptedPets();
                        if (adoptedPets == null) {
                            adoptedPets = new ArrayList<>();
                        }
                        if (!adoptedPets.contains(pet.getId())) {
                            adoptedPets.add(pet.getId());
                            owner.setAdoptedPets(adoptedPets);

                            // Use technicalId from stored owner node to update
                            String technicalIdStr = null;
                            if (ownerNode.has("technicalId") && !ownerNode.get("technicalId").isNull()) {
                                technicalIdStr = ownerNode.get("technicalId").asText();
                            }

                            if (technicalIdStr != null && !technicalIdStr.isEmpty()) {
                                CompletableFuture<UUID> updated = entityService.updateItem(
                                    Owner.ENTITY_NAME,
                                    String.valueOf(Owner.ENTITY_VERSION),
                                    UUID.fromString(technicalIdStr),
                                    owner
                                );
                                updated.join();
                                logger.info("Updated owner {} adoptedPets with pet {}", owner.getId(), pet.getId());
                            } else {
                                logger.warn("Owner technicalId not present for owner {}. Cannot update adoptedPets.", owner.getId());
                            }
                        } else {
                            logger.info("Pet {} already present in owner's {} adoptedPets list", pet.getId(), owner.getId());
                        }
                    }
                } catch (Exception ex) {
                    logger.error("Failed updating owner adoptedPets for ownerId {}: {}", ownerBusinessId, ex.getMessage(), ex);
                }
            } else {
                logger.warn("Adoption request {} for pet {} has no ownerId; skipping owner update.", request.getRequestId(), pet.getId());
            }

            // Business event emission and further actions would occur outside this processor (platform).
            logger.info("Adoption approved for pet {}, request {}", pet.getId(), request.getRequestId());
        } catch (Exception e) {
            logger.error("Error processing approval for pet {}: {}", pet != null ? pet.getId() : "unknown", e.getMessage(), e);
        }

        return pet;
    }
}