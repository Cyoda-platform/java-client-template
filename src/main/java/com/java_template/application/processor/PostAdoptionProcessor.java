package com.java_template.application.processor;

import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class PostAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PostAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PostAdoptionProcessor(SerializerFactory serializerFactory,
                                 EntityService entityService,
                                 ObjectMapper objectMapper) {
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
            if (pet == null) {
                logger.warn("PostAdoptionProcessor received null pet");
                return pet;
            }

            String status = null;
            try {
                status = pet.getStatus();
            } catch (Exception e) {
                // If getter not present or fails, continue defensively
                logger.debug("Unable to read pet.status: {}", e.getMessage());
            }

            // Only run post-adoption actions when pet is in adopted state
            if (status == null || !status.equalsIgnoreCase("adopted")) {
                logger.info("Pet {} is not in adopted state (status={}), skipping post-adoption actions.", safeId(pet), status);
                return pet;
            }

            logger.info("Executing post-adoption actions for pet {}", safeId(pet));

            // Attempt to update adoption requests inside the pet (mark processedAt/complete)
            try {
                Object adoptionRequestsObj = null;
                try {
                    adoptionRequestsObj = pet.getAdoptionRequests();
                } catch (Throwable t) {
                    logger.debug("Pet.getAdoptionRequests() not accessible or failed: {}", t.getMessage());
                }

                if (adoptionRequestsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> requests = (List<Object>) adoptionRequestsObj;
                    for (int i = 0; i < requests.size(); i++) {
                        Object rawReq = requests.get(i);
                        ObjectNode reqNode = objectMapper.convertValue(rawReq, ObjectNode.class);
                        String reqStatus = reqNode.has("status") ? reqNode.get("status").asText(null) : null;

                        // Treat approved or complete as adoption to process owner updates
                        if (reqStatus != null && (reqStatus.equalsIgnoreCase("approved") || reqStatus.equalsIgnoreCase("complete"))) {
                            // set processedAt if absent
                            if (!reqNode.has("processedAt") || reqNode.get("processedAt").isNull()) {
                                reqNode.put("processedAt", Instant.now().toString());
                            }
                            // normalize status to complete after post-adoption processing
                            reqNode.put("status", "complete");

                            // Persist back into the list element (as generic Object)
                            Object updatedReq = objectMapper.convertValue(reqNode, Object.class);
                            requests.set(i, updatedReq);

                            // Update owner adoptedPets
                            String ownerId = reqNode.has("ownerId") ? reqNode.get("ownerId").asText(null) : null;
                            if (ownerId != null && !ownerId.isBlank()) {
                                try {
                                    // Find owner by business id field 'id'
                                    SearchConditionRequest condition = SearchConditionRequest.group("AND",
                                        Condition.of("$.id", "EQUALS", ownerId)
                                    );

                                    CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                                        Owner.ENTITY_NAME,
                                        String.valueOf(Owner.ENTITY_VERSION),
                                        condition,
                                        true
                                    );

                                    ArrayNode owners = itemsFuture.get();
                                    if (owners != null && owners.size() > 0) {
                                        ObjectNode ownerNode = (ObjectNode) owners.get(0);

                                        // Try to determine technicalId for update
                                        String technicalIdStr = null;
                                        if (ownerNode.has("technicalId")) {
                                            technicalIdStr = ownerNode.get("technicalId").asText(null);
                                        } else if (ownerNode.has("id") && ownerNode.has("technicalId")) {
                                            technicalIdStr = ownerNode.get("technicalId").asText(null);
                                        }

                                        // Merge pet id into owner's adoptedPets array
                                        ArrayNode adoptedPetsNode = null;
                                        if (ownerNode.has("adoptedPets") && ownerNode.get("adoptedPets").isArray()) {
                                            adoptedPetsNode = (ArrayNode) ownerNode.get("adoptedPets");
                                        } else {
                                            adoptedPetsNode = objectMapper.createArrayNode();
                                            ownerNode.set("adoptedPets", adoptedPetsNode);
                                        }

                                        String petBusinessId = safeId(pet);
                                        boolean alreadyPresent = false;
                                        for (int idx = 0; idx < adoptedPetsNode.size(); idx++) {
                                            if (petBusinessId.equals(adoptedPetsNode.get(idx).asText())) {
                                                alreadyPresent = true;
                                                break;
                                            }
                                        }
                                        if (!alreadyPresent) {
                                            adoptedPetsNode.add(petBusinessId);
                                        }

                                        // If we can obtain technicalId, call updateItem to persist owner changes
                                        if (technicalIdStr != null && !technicalIdStr.isBlank()) {
                                            try {
                                                UUID technicalId = UUID.fromString(technicalIdStr);
                                                // Prepare entity payload as ObjectNode without metadata
                                                ObjectNode ownerPayload = ownerNode;
                                                // Remove technicalId from payload if present (updateItem expects entity payload)
                                                ownerPayload.remove("technicalId");

                                                CompletableFuture<UUID> updateFuture = entityService.updateItem(
                                                    Owner.ENTITY_NAME,
                                                    String.valueOf(Owner.ENTITY_VERSION),
                                                    technicalId,
                                                    objectMapper.convertValue(ownerPayload, Object.class)
                                                );
                                                updateFuture.get();
                                                logger.info("Updated owner {} with adopted pet {}", ownerId, petBusinessId);
                                            } catch (Exception e) {
                                                logger.warn("Unable to update owner by technicalId (ownerId={}): {}", ownerId, e.getMessage());
                                            }
                                        } else {
                                            logger.warn("Cannot determine technicalId for owner {}, skipping update", ownerId);
                                        }
                                    } else {
                                        logger.warn("No owner found with id={} to update adoptedPets", ownerId);
                                    }
                                } catch (Exception ex) {
                                    logger.error("Failed to update owner {} for adopted pet {}: {}", ownerId, safeId(pet), ex.getMessage());
                                }
                            }
                        }
                    }

                    // set back modified adoptionRequests list on pet (will be persisted by Cyoda)
                    try {
                        pet.setAdoptionRequests(requests);
                    } catch (Throwable t) {
                        logger.debug("Unable to set adoptionRequests on Pet via setter: {}", t.getMessage());
                    }
                } else {
                    logger.debug("Pet.adoptionRequests is null or not a List, skipping adoptionRequests processing.");
                }
            } catch (Throwable t) {
                logger.error("Error while processing adoptionRequests for pet {}: {}", safeId(pet), t.getMessage());
            }

            // Optionally mark pet as archive candidate: apply policy of leaving adopted pets for manual archive.
            // We will not automatically set pet.status to archived here to allow manual review, but we will tag a candidate flag if available.
            try {
                // If there is a field archiveCandidate (boolean) we try to set it via setter if present
                // This is best-effort: use reflection-like setter invocation attempt
                // Many generated POJOs have setArchiveCandidate(Boolean) or similar; attempt common setter:
                // Note: We do not fail if setter not available.
                try {
                    // reflectively attempt setter: pet.setArchiveCandidate(true);
                    pet.getClass().getMethod("setArchiveCandidate", Boolean.class).invoke(pet, Boolean.TRUE);
                    logger.debug("Marked pet {} as archive candidate", safeId(pet));
                } catch (NoSuchMethodException nsme) {
                    // ignore - field not present
                }
            } catch (Throwable t) {
                logger.debug("Unable to mark archive candidate for pet {}: {}", safeId(pet), t.getMessage());
            }

            // Emit domain event log
            logger.info("Post-adoption processing completed for pet {}", safeId(pet));

        } catch (Exception e) {
            logger.error("Unexpected error in PostAdoptionProcessor for pet {}: {}", safeId(pet), e.getMessage(), e);
        }

        return pet;
    }

    private String safeId(Pet pet) {
        try {
            String id = pet.getId();
            return id != null ? id : "unknown";
        } catch (Throwable t) {
            return "unknown";
        }
    }
}