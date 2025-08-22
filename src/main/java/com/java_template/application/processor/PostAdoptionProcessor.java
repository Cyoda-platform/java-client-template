package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class PostAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PostAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PostAdoptionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet entity) {
        return entity != null && entity.getId() != null && entity.getStatus() != null;
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();
        if (entity == null) {
            logger.warn("Entity is null in PostAdoptionProcessor");
            return null;
        }

        String status = entity.getStatus();
        if (status == null || !status.equalsIgnoreCase("adopted")) {
            logger.info("Pet {} not in 'adopted' state (current: {}), skipping post-adoption actions.", entity.getId(), status);
            // Ensure updatedAt is touched for idempotency awareness
            try {
                entity.setUpdatedAt(Instant.now().toString());
            } catch (Exception e) {
                // ignore if setter doesn't exist
            }
            return entity;
        }

        List<AdoptionRequest> adoptionRequests = null;
        try {
            adoptionRequests = entity.getAdoptionRequests();
        } catch (Exception e) {
            logger.debug("Pet {} has no adoptionRequests property or getter failed: {}", entity.getId(), e.getMessage());
        }

        if (adoptionRequests != null) {
            for (AdoptionRequest req : adoptionRequests) {
                if (req == null) continue;

                String reqStatus = null;
                try {
                    reqStatus = req.getStatus();
                } catch (Exception ignored) {}

                boolean approved = "approved".equalsIgnoreCase(reqStatus) || "complete".equalsIgnoreCase(reqStatus);
                if (!approved) {
                    // skip non-approved requests
                    continue;
                }

                // mark processedAt if missing
                try {
                    if (req.getProcessedAt() == null) {
                        req.setProcessedAt(Instant.now().toString());
                    }
                } catch (Exception ignored) {}

                // normalize to 'complete' to indicate post-adoption handling finished
                try {
                    req.setStatus("complete");
                } catch (Exception ignored) {}

                // attempt to update owner adoptedPets
                String ownerId = null;
                try {
                    ownerId = req.getOwnerId();
                } catch (Exception ignored) {}

                if (ownerId != null && !ownerId.trim().isEmpty()) {
                    try {
                        // Read owner by technicalId (UUID)
                        ObjectNode ownerNode = entityService.getItem(
                            Owner.ENTITY_NAME,
                            String.valueOf(Owner.ENTITY_VERSION),
                            UUID.fromString(ownerId)
                        ).join();

                        if (ownerNode != null && !ownerNode.isNull()) {
                            Owner owner = objectMapper.treeToValue(ownerNode, Owner.class);
                            if (owner != null) {
                                List<String> adopted = null;
                                try {
                                    adopted = owner.getAdoptedPets();
                                } catch (Exception ignored) {}
                                if (adopted == null) adopted = new ArrayList<>();
                                if (!adopted.contains(entity.getId())) {
                                    adopted.add(entity.getId());
                                    try {
                                        owner.setAdoptedPets(adopted);
                                        entityService.updateItem(
                                            Owner.ENTITY_NAME,
                                            String.valueOf(Owner.ENTITY_VERSION),
                                            UUID.fromString(ownerId),
                                            owner
                                        ).join();
                                        logger.info("Owner {} updated with adopted pet {}", ownerId, entity.getId());
                                    } catch (Exception e) {
                                        logger.error("Failed to update owner {} with adopted pet {}: {}", ownerId, entity.getId(), e.getMessage(), e);
                                    }
                                } else {
                                    logger.debug("Owner {} already contains adopted pet {}", ownerId, entity.getId());
                                }
                            }
                        } else {
                            logger.warn("Owner node for id {} not found when processing post-adoption for pet {}", ownerId, entity.getId());
                        }
                    } catch (Exception e) {
                        logger.error("Error while fetching/updating owner {} for pet {}: {}", ownerId, entity.getId(), e.getMessage(), e);
                    }
                } else {
                    logger.warn("AdoptionRequest for pet {} has no ownerId set", entity.getId());
                }

                // Only handle the first matching approved/complete request to avoid duplicate owner updates
                break;
            }
        } else {
            logger.info("No adoptionRequests found on pet {}", entity.getId());
        }

        // Optionally mark as archive candidate - do not change canonical status here; instead set updatedAt for traceability
        try {
            entity.setUpdatedAt(Instant.now().toString());
        } catch (Exception ignored) {}

        // Emit logs for downstream systems to pick up events (actual event emission mechanism may be elsewhere)
        logger.info("Post-adoption processing completed for pet {}", entity.getId());

        return entity;
    }
}