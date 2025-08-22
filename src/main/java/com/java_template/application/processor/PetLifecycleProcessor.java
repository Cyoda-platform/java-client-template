package com.java_template.application.processor;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Component
public class PetLifecycleProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetLifecycleProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetLifecycleProcessor(SerializerFactory serializerFactory,
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
        if (entity == null) return false;
        try {
            // Basic required-field validation per functional requirements
            String name = entity.getName();
            String species = entity.getSpecies();
            return name != null && !name.trim().isEmpty() && species != null && !species.trim().isEmpty();
        } catch (Exception e) {
            logger.warn("isValidEntity check failed due to unexpected entity shape", e);
            return false;
        }
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();
        if (entity == null) return null;

        String status = null;
        try {
            status = entity.getStatus();
        } catch (Exception e) {
            logger.warn("Unable to read pet.status, defaulting to null", e);
        }

        // Ensure status normalization
        if (status != null) status = status.trim().toLowerCase();

        try {
            // 1) New -> validated or validation_failed
            if ("new".equals(status)) {
                if (isMissingRequiredFields(entity)) {
                    entity.setStatus("validation_failed");
                    logger.info("Pet {} validation failed: missing required fields", safeId(entity));
                } else {
                    entity.setStatus("validated");
                    logger.info("Pet {} validated", safeId(entity));
                }
                return entity;
            }

            // 2) validated -> enrichment -> available or validation_failed
            if ("validated".equals(status)) {
                // normalize breed
                try {
                    String breed = entity.getBreed();
                    if (breed != null) {
                        entity.setBreed(breed.trim());
                    }
                } catch (Exception ignored) { }

                // simple photo accessibility heuristic:
                boolean photosOk = true;
                try {
                    List<String> photos = entity.getPhotos();
                    if (photos == null || photos.isEmpty()) {
                        photosOk = false;
                    } else {
                        for (String url : photos) {
                            if (url == null || url.trim().isEmpty()) {
                                photosOk = false;
                                break;
                            }
                            // basic URL check
                            String u = url.trim().toLowerCase();
                            if (!(u.startsWith("http://") || u.startsWith("https://"))) {
                                photosOk = false;
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    photosOk = false;
                    logger.debug("Error evaluating photos accessibility for pet {}: {}", safeId(entity), e.getMessage());
                }

                if (!photosOk) {
                    // policy per requirements: fail validation if photos inaccessible
                    entity.setStatus("validation_failed");
                    logger.info("Pet {} enrichment failed due to inaccessible or missing photos", safeId(entity));
                } else {
                    entity.setStatus("available");
                    logger.info("Pet {} enriched and set to available", safeId(entity));
                }
                return entity;
            }

            // 3) Handle transition to adopted when there's an approved adoption request
            // If pet is already adopted or archived, do nothing
            if (!"adopted".equals(status) && !"archived".equals(status) && entity.getAdoptionRequests() != null) {
                List<?> requests = entity.getAdoptionRequests();
                boolean foundApproved = false;
                Object approvedRequest = null;
                String approvedOwnerId = null;
                for (Object req : requests) {
                    if (req == null) continue;
                    String reqStatus = null;
                    String ownerId = null;
                    try {
                        if (req instanceof ObjectNode) {
                            ObjectNode on = (ObjectNode) req;
                            if (on.has("status") && !on.get("status").isNull()) reqStatus = on.get("status").asText();
                            if (on.has("ownerId") && !on.get("ownerId").isNull()) ownerId = on.get("ownerId").asText();
                        } else {
                            // try reflective getters getStatus() and getOwnerId()
                            try {
                                Method mStatus = req.getClass().getMethod("getStatus");
                                Object val = mStatus.invoke(req);
                                if (val != null) reqStatus = val.toString();
                            } catch (NoSuchMethodException ignored) {}

                            try {
                                Method mOwner = req.getClass().getMethod("getOwnerId");
                                Object val = mOwner.invoke(req);
                                if (val != null) ownerId = val.toString();
                            } catch (NoSuchMethodException ignored) {}
                        }
                    } catch (Exception e) {
                        logger.debug("Unable to introspect adoption request: {}", e.getMessage());
                    }

                    if (reqStatus != null && "approved".equalsIgnoreCase(reqStatus.trim())) {
                        foundApproved = true;
                        approvedRequest = req;
                        approvedOwnerId = ownerId;
                        break;
                    }
                }

                if (foundApproved) {
                    // mark pet as adopted
                    entity.setStatus("adopted");
                    // set processedAt on the request if possible
                    setProcessedAtOnRequest(approvedRequest);

                    logger.info("Pet {} marked as adopted due to an approved adoption request", safeId(entity));

                    // update Owner.adoptedPets to include this pet id if owner exists and owner id provided
                    if (approvedOwnerId != null && !approvedOwnerId.trim().isEmpty()) {
                        try {
                            // find owner by business id using condition search (in-memory)
                            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                                Condition.of("$.id", "EQUALS", approvedOwnerId)
                            );
                            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                                Owner.ENTITY_NAME,
                                String.valueOf(Owner.ENTITY_VERSION),
                                condition,
                                true
                            );
                            ArrayNode owners = itemsFuture.join();
                            if (owners != null && owners.size() > 0) {
                                ObjectNode ownerNode = (ObjectNode) owners.get(0);
                                Owner owner = objectMapper.treeToValue(ownerNode, Owner.class);

                                // ensure adoptedPets list exists and contains pet id
                                List<String> adopted = owner.getAdoptedPets();
                                if (adopted == null) adopted = new ArrayList<>();
                                String petId = safeId(entity);
                                if (!adopted.contains(petId)) {
                                    adopted.add(petId);
                                    owner.setAdoptedPets(adopted);
                                }

                                // extract technicalId for update
                                String ownerTechnicalId = null;
                                if (ownerNode.has("technicalId") && !ownerNode.get("technicalId").isNull()) {
                                    ownerTechnicalId = ownerNode.get("technicalId").asText();
                                }

                                if (ownerTechnicalId != null) {
                                    try {
                                        CompletableFuture<java.util.UUID> updated = entityService.updateItem(
                                            Owner.ENTITY_NAME,
                                            String.valueOf(Owner.ENTITY_VERSION),
                                            java.util.UUID.fromString(ownerTechnicalId),
                                            owner
                                        );
                                        updated.join();
                                        logger.info("Owner {} updated with adopted pet {}", approvedOwnerId, petId);
                                    } catch (Exception e) {
                                        logger.warn("Failed to update Owner {} adoptedPets: {}", approvedOwnerId, e.getMessage());
                                    }
                                } else {
                                    logger.warn("Owner {} found but technicalId missing, skipping update", approvedOwnerId);
                                }
                            } else {
                                logger.warn("Owner with id {} not found; cannot update adoptedPets", approvedOwnerId);
                            }
                        } catch (Exception e) {
                            logger.warn("Error while updating owner for adopted pet {}: {}", safeId(entity), e.getMessage());
                        }
                    } else {
                        logger.warn("Approved adoption request has no ownerId; owner update skipped for pet {}", safeId(entity));
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Unexpected error processing pet {}: {}", safeId(entity), e.getMessage(), e);
            // On unexpected errors, mark for manual review by setting validation_failed if appropriate
            try {
                entity.setStatus("validation_failed");
            } catch (Exception ignore) {}
        }

        // For other statuses or no-op, simply return the entity as-is. Persistence is handled by Cyoda.
        return entity;
    }

    // Helpers

    private boolean isMissingRequiredFields(Pet entity) {
        try {
            String name = entity.getName();
            String species = entity.getSpecies();
            return name == null || name.trim().isEmpty() || species == null || species.trim().isEmpty();
        } catch (Exception e) {
            return true;
        }
    }

    private void setProcessedAtOnRequest(Object request) {
        if (request == null) return;
        String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        try {
            if (request instanceof ObjectNode) {
                ObjectNode on = (ObjectNode) request;
                on.put("processedAt", now);
            } else {
                try {
                    Method m = request.getClass().getMethod("setProcessedAt", String.class);
                    m.invoke(request, now);
                    return;
                } catch (NoSuchMethodException ignored) {}
                // try setProcessedAt(Date) variants are not attempted to keep simple
            }
        } catch (Exception e) {
            logger.debug("Unable to set processedAt on adoption request: {}", e.getMessage());
        }
    }

    private String safeId(Pet entity) {
        try {
            String id = entity.getId();
            if (id != null) return id;
        } catch (Exception ignored) {}
        try {
            String tid = entity.getTechnicalId();
            if (tid != null) return tid;
        } catch (Exception ignored) {}
        return "<unknown>";
    }
}