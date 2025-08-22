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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;

@Component
public class CreateAdoptionRequestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateAdoptionRequestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper mapper = new ObjectMapper();

    public CreateAdoptionRequestProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
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
        return entity != null && entity.isValid();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();
        try {
            // Extract potential ownerId and notes from the incoming request payload if available (defensive/reflection)
            String ownerId = tryExtractFieldFromRequest(context.request(), "ownerId");
            String notes = tryExtractFieldFromRequest(context.request(), "notes");

            // Prepare new adoption request as a generic map to avoid depending on a concrete AdoptionRequest class
            Map<String, Object> newRequest = new HashMap<>();
            String requestId = "request_" + UUID.randomUUID().toString();
            newRequest.put("requestId", requestId);
            newRequest.put("ownerId", ownerId);
            newRequest.put("requestedAt", Instant.now().toString());
            newRequest.put("notes", notes);
            newRequest.put("status", "pending");
            newRequest.put("processedAt", null);

            // Get existing adoption requests (use raw type to avoid compile-time generic mismatches)
            List adoptionRequests = null;
            try {
                Object ar = entity.getAdoptionRequests();
                if (ar == null) {
                    adoptionRequests = new ArrayList();
                } else if (ar instanceof List) {
                    adoptionRequests = (List) ar;
                } else {
                    // unexpected type - wrap it
                    adoptionRequests = new ArrayList();
                    adoptionRequests.add(ar);
                }
            } catch (Exception e) {
                // If getter not present or fails, initialize a list and try to set it later if setter exists
                logger.warn("Could not read adoptionRequests from Pet entity: {}", e.getMessage());
                adoptionRequests = new ArrayList();
            }

            // Idempotency: avoid adding duplicate requestId (extremely unlikely) or duplicate owner request at same timestamp
            boolean alreadyExists = false;
            for (Object o : adoptionRequests) {
                try {
                    if (o instanceof Map) {
                        Object existingId = ((Map) o).get("requestId");
                        Object existingOwner = ((Map) o).get("ownerId");
                        if ((existingId != null && existingId.equals(requestId)) ||
                            (ownerId != null && existingOwner != null && ownerId.equals(existingOwner) &&
                             "pending".equalsIgnoreCase(String.valueOf(((Map) o).get("status"))))) {
                            alreadyExists = true;
                            break;
                        }
                    } else {
                        // Try reflection to read requestId/ownerId/status if it's a POJO
                        Method getRequestId = tryFindMethod(o.getClass(), "getRequestId", "requestId");
                        Method getOwnerId = tryFindMethod(o.getClass(), "getOwnerId", "ownerId");
                        Method getStatus = tryFindMethod(o.getClass(), "getStatus", "status");
                        Object existingId = invokeIfExists(getRequestId, o);
                        Object existingOwner = invokeIfExists(getOwnerId, o);
                        Object existingStatus = invokeIfExists(getStatus, o);
                        if ((existingId != null && existingId.equals(requestId)) ||
                            (ownerId != null && existingOwner != null && ownerId.equals(existingOwner) &&
                             existingStatus != null && "pending".equalsIgnoreCase(String.valueOf(existingStatus)))) {
                            alreadyExists = true;
                            break;
                        }
                    }
                } catch (Exception ex) {
                    // ignore individual item errors
                }
            }

            if (!alreadyExists) {
                adoptionRequests.add(newRequest);
                // Try to set adoptionRequests back on entity
                try {
                    // Prefer setter if available
                    Method setter = tryFindSetter(entity.getClass(), "setAdoptionRequests");
                    if (setter != null) {
                        setter.invoke(entity, adoptionRequests);
                    } else {
                        // As fallback, try to assign via a public field (unlikely) or ignore (many entities will accept the mutated list)
                        // No further action required; mutated list will be persisted if the entity exposes it
                    }
                } catch (Exception e) {
                    logger.warn("Failed to set adoptionRequests on Pet entity: {}", e.getMessage());
                }

                logger.info("Added adoption request {} to pet {}", requestId, safeGetId(entity));
            } else {
                logger.info("Duplicate adoption request detected for pet {} - skipping add", safeGetId(entity));
            }

            // Business rule: If pet is available and there is no other pending/reserved request, set to reserved.
            try {
                String status = entity.getStatus();
                if (status == null) status = "";
                boolean hasPendingOrReserved = false;
                for (Object o : adoptionRequests) {
                    String s = null;
                    if (o instanceof Map) {
                        Object st = ((Map) o).get("status");
                        s = st != null ? String.valueOf(st) : null;
                    } else {
                        Method getStatus = tryFindMethod(o.getClass(), "getStatus", "status");
                        Object st = invokeIfExists(getStatus, o);
                        s = st != null ? String.valueOf(st) : null;
                    }
                    if (s != null && ("pending".equalsIgnoreCase(s) || "reserved".equalsIgnoreCase(s))) {
                        hasPendingOrReserved = true;
                        break;
                    }
                }

                if ("available".equalsIgnoreCase(status) && hasPendingOrReserved) {
                    // exclusive reservation policy: if there is at least one pending/reserved, move pet to reserved
                    entity.setStatus("reserved");
                    logger.info("Pet {} status changed to reserved due to adoption request", safeGetId(entity));
                } else if ("available".equalsIgnoreCase(status) && !hasPendingOrReserved) {
                    // No change: remain available but keep track of request
                    logger.info("Pet {} remains available; adoption interest recorded", safeGetId(entity));
                } else if (!"reserved".equalsIgnoreCase(status) && hasPendingOrReserved) {
                    // If pet was new/validated/etc. and now has a pending request, set to reserved
                    entity.setStatus("reserved");
                    logger.info("Pet {} moved to reserved due to adoption request (previous status: {})", safeGetId(entity), status);
                }
            } catch (Exception e) {
                logger.warn("Failed to evaluate/set pet status after adding adoption request: {}", e.getMessage());
            }

        } catch (Exception e) {
            logger.error("Error while processing adoption request for pet {}: {}", safeGetId(entity), e.getMessage(), e);
        }
        return entity;
    }

    // Helper: attempt to extract a field (like ownerId or notes) from request payload using reflection and JSON parsing
    private String tryExtractFieldFromRequest(EntityProcessorCalculationRequest req, String fieldName) {
        if (req == null) return null;
        try {
            // Try commonly named getters that may return a payload
            String[] candidateMethods = new String[]{"getBody", "getPayload", "getData", "body", "payload"};
            Object payload = null;
            for (String mName : candidateMethods) {
                try {
                    Method m = req.getClass().getMethod(mName);
                    if (m != null) {
                        payload = m.invoke(req);
                        if (payload != null) break;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }

            if (payload == null) {
                // try getRequestModel or getRequest if present
                try {
                    Method m = req.getClass().getMethod("getRequestModel");
                    payload = m.invoke(req);
                } catch (NoSuchMethodException ignored) {
                }
            }

            if (payload == null) return null;

            // If payload is already a JsonNode
            if (payload instanceof JsonNode) {
                JsonNode node = (JsonNode) payload;
                JsonNode f = node.get(fieldName);
                return f != null && !f.isNull() ? f.asText() : null;
            }

            // If payload is a Map
            if (payload instanceof Map) {
                Object v = ((Map) payload).get(fieldName);
                return v != null ? String.valueOf(v) : null;
            }

            // If payload is a String, try to parse as JSON
            if (payload instanceof String) {
                try {
                    JsonNode node = mapper.readTree((String) payload);
                    JsonNode f = node.get(fieldName);
                    return f != null && !f.isNull() ? f.asText() : null;
                } catch (Exception ex) {
                    return null;
                }
            }

            // As a final attempt, reflectively call a getter on the payload object
            try {
                Method getter = tryFindMethod(payload.getClass(), "get" + capitalize(fieldName), fieldName);
                if (getter != null) {
                    Object v = getter.invoke(payload);
                    return v != null ? String.valueOf(v) : null;
                }
            } catch (Exception ignored) {
            }

        } catch (Exception e) {
            logger.debug("Unable to extract field {} from request payload: {}", fieldName, e.getMessage());
        }
        return null;
    }

    private Method tryFindMethod(Class<?> cls, String... names) {
        for (String n : names) {
            try {
                Method m = cls.getMethod(n);
                if (m != null) return m;
            } catch (NoSuchMethodException ignored) {
            }
            // try with "get" prefix
            try {
                Method m = cls.getMethod("get" + capitalize(n));
                if (m != null) return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private Method tryFindSetter(Class<?> cls, String name) {
        // common setter signature: setAdoptionRequests(List)
        for (Method m : cls.getMethods()) {
            if (m.getName().equalsIgnoreCase(name) && m.getParameterCount() == 1) {
                return m;
            }
        }
        return null;
    }

    private Object invokeIfExists(Method m, Object target) {
        if (m == null || target == null) return null;
        try {
            return m.invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeGetId(Pet entity) {
        try {
            Method m = tryFindMethod(entity.getClass(), "getId", "id");
            Object v = invokeIfExists(m, entity);
            return v != null ? String.valueOf(v) : "<unknown>";
        } catch (Exception e) {
            return "<unknown>";
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}