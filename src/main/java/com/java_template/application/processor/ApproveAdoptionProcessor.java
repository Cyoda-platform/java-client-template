package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class ApproveAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ApproveAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ApproveAdoptionProcessor(SerializerFactory serializerFactory,
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
        if (pet == null) {
            logger.warn("Pet entity is null in context");
            return null;
        }

        try {
            // Find an adoption request to approve: prefer status "pending"
            Object targetRequest = null;
            String ownerId = null;

            List<?> requests = safeGetAdoptionRequests(pet);
            if (requests == null || requests.isEmpty()) {
                logger.info("No adoption requests found for pet {}", safeGetPetId(pet));
                return pet;
            }

            for (Object req : requests) {
                String status = extractStringField(req, "status");
                if (status != null && "pending".equalsIgnoreCase(status)) {
                    targetRequest = req;
                    break;
                }
            }

            // If no pending found, try to pick the first request (idempotency tolerance)
            if (targetRequest == null && !requests.isEmpty()) {
                targetRequest = requests.get(0);
            }

            if (targetRequest == null) {
                logger.info("No target adoption request to approve for pet {}", safeGetPetId(pet));
                return pet;
            }

            // Approve request: set status and processedAt
            setStringField(targetRequest, "status", "approved");
            setStringField(targetRequest, "processedAt", Instant.now().toString());

            ownerId = extractStringField(targetRequest, "ownerId");

            // Move pet to adopted
            setPetStatus(pet, "adopted");

            logger.info("Adoption request approved for pet {}. OwnerId={}", safeGetPetId(pet), ownerId);

            // Update Owner.adoptedPets (only other-entity updates via entityService)
            if (ownerId != null && !ownerId.isBlank()) {
                try {
                    SearchConditionRequest cond = SearchConditionRequest.group("AND",
                        Condition.of("$.id", "EQUALS", ownerId)
                    );

                    CompletableFuture<ArrayNode> ownerItemsFuture = entityService.getItemsByCondition(
                        Owner.ENTITY_NAME,
                        String.valueOf(Owner.ENTITY_VERSION),
                        cond,
                        true
                    );

                    ArrayNode owners = ownerItemsFuture.join();
                    if (owners != null && owners.size() > 0) {
                        ObjectNode ownerNode = (ObjectNode) owners.get(0);
                        // Convert to Owner POJO if possible, otherwise manipulate JSON directly
                        List<String> adoptedPets = new ArrayList<>();
                        JsonNode adoptedPetsNode = ownerNode.get("adoptedPets");
                        if (adoptedPetsNode != null && adoptedPetsNode.isArray()) {
                            for (JsonNode n : adoptedPetsNode) {
                                adoptedPets.add(n.asText());
                            }
                        }

                        String petId = safeGetPetId(pet);
                        if (petId != null && !adoptedPets.contains(petId)) {
                            adoptedPets.add(petId);
                        }

                        // Replace adoptedPets array in ownerNode
                        ArrayNode updatedArray = objectMapper.createArrayNode();
                        adoptedPets.forEach(updatedArray::add);
                        ownerNode.set("adoptedPets", updatedArray);

                        // Determine technicalId for update operation
                        JsonNode technicalIdNode = ownerNode.get("technicalId");
                        if (technicalIdNode == null || technicalIdNode.isNull() || technicalIdNode.asText().isBlank()) {
                            logger.warn("Owner found by id but missing technicalId, cannot update adoptedPets for ownerId={}", ownerId);
                        } else {
                            UUID technicalUuid = UUID.fromString(technicalIdNode.asText());
                            CompletableFuture<UUID> updateFuture = entityService.updateItem(
                                Owner.ENTITY_NAME,
                                String.valueOf(Owner.ENTITY_VERSION),
                                technicalUuid,
                                ownerNode
                            );
                            updateFuture.join();
                            logger.info("Owner {} updated with new adoptedPets count={}", ownerId, adoptedPets.size());
                        }
                    } else {
                        logger.warn("Owner with id {} not found; cannot update adoptedPets", ownerId);
                    }
                } catch (Exception e) {
                    logger.error("Failed to update owner adoptedPets for ownerId=" + ownerId, e);
                    // Don't fail the processor; mark in logs for manual inspection
                }
            } else {
                logger.warn("No ownerId associated with approved adoption request for pet {}", safeGetPetId(pet));
            }

            // Emit minimal log as domain event placeholder
            logger.info("PetAdopted event: petId={}, ownerId={}", safeGetPetId(pet), ownerId);

        } catch (Exception ex) {
            logger.error("Error while approving adoption for pet {}", safeGetPetId(pet), ex);
            // Per reliability rules, processors should be resilient: do not throw to avoid breaking workflow
        }

        return pet;
    }

    // Helpers

    @SuppressWarnings("unchecked")
    private List<?> safeGetAdoptionRequests(Pet pet) {
        try {
            Method m = pet.getClass().getMethod("getAdoptionRequests");
            Object val = m.invoke(pet);
            if (val instanceof List) return (List<?>) val;
        } catch (NoSuchMethodException nsme) {
            // ignore - try common getter
        } catch (Exception ignored) { }
        return Collections.emptyList();
    }

    private String safeGetPetId(Pet pet) {
        try {
            Method m = pet.getClass().getMethod("getId");
            Object val = m.invoke(pet);
            return val != null ? String.valueOf(val) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void setPetStatus(Pet pet, String status) {
        try {
            Method setter = pet.getClass().getMethod("setStatus", String.class);
            setter.invoke(pet, status);
        } catch (Exception e) {
            // fallback: try public field (unlikely). Log and continue.
            logger.warn("Unable to set pet.status via setter for pet {}", safeGetPetId(pet));
        }
    }

    private String extractStringField(Object obj, String fieldName) {
        if (obj == null) return null;
        try {
            if (obj instanceof ObjectNode) {
                JsonNode n = ((ObjectNode) obj).get(fieldName);
                return n != null && !n.isNull() ? n.asText() : null;
            }
            if (obj instanceof Map) {
                Object v = ((Map<?, ?>) obj).get(fieldName);
                return v != null ? String.valueOf(v) : null;
            }
            // try getter
            Method getter = obj.getClass().getMethod("get" + capitalize(fieldName));
            Object v = getter.invoke(obj);
            return v != null ? String.valueOf(v) : null;
        } catch (NoSuchMethodException nsme) {
            // try boolean getter style or direct field
            try {
                Method getter = obj.getClass().getMethod(fieldName);
                Object v = getter.invoke(obj);
                return v != null ? String.valueOf(v) : null;
            } catch (Exception ignored) {}
        } catch (Exception e) {
            // reflection errors
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void setStringField(Object obj, String fieldName, String value) {
        if (obj == null) return;
        try {
            if (obj instanceof ObjectNode) {
                ((ObjectNode) obj).put(fieldName, value);
                return;
            }
            if (obj instanceof Map) {
                ((Map<String, Object>) obj).put(fieldName, value);
                return;
            }
            // try setter
            Method setter = obj.getClass().getMethod("set" + capitalize(fieldName), String.class);
            setter.invoke(obj, value);
        } catch (NoSuchMethodException nsme) {
            // try direct field via reflection fallback
            try {
                java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}