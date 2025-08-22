package com.java_template.application.processor;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class CreateAdoptionRequestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateAdoptionRequestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CreateAdoptionRequestProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        return entity != null;
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();

        try {
            // Attempt to read the persisted/current version of the pet to make reservation decisions.
            // Use technicalId from the incoming Cyoda request if available.
            String technicalId = context.request() != null ? context.request().getEntityId() : null;
            ObjectNode persistedPetNode = null;
            if (technicalId != null) {
                try {
                    CompletableFuture<ObjectNode> petFuture = entityService.getItem(
                        Pet.ENTITY_NAME,
                        String.valueOf(Pet.ENTITY_VERSION),
                        UUID.fromString(technicalId)
                    );
                    persistedPetNode = petFuture.join();
                } catch (Exception e) {
                    // If we fail to read the persisted entity, continue processing based on available data.
                    logger.warn("Unable to read persisted Pet for technicalId {}: {}. Proceeding with incoming entity.", technicalId, e.getMessage());
                }
            }

            // Extract the incoming adoption request data from the incoming Pet payload.
            // Incoming payload is expected to contain a single adoption request (embedded).
            Object incomingRequestsObj = null;
            if (pet.getAdoptionRequests() != null) {
                // take the first entry as the new request payload (common shape for creation)
                List<?> incomingList = (List<?>) pet.getAdoptionRequests();
                if (!incomingList.isEmpty()) {
                    incomingRequestsObj = incomingList.get(0);
                }
            }

            if (incomingRequestsObj == null) {
                logger.warn("No adoption request payload found on incoming Pet entity. Nothing to do.");
                return pet;
            }

            // Convert incoming request to a mutable JSON node for normalization
            ObjectNode incomingRequestNode = objectMapper.valueToTree(incomingRequestsObj);

            // Build canonical adoption request node
            ObjectNode newRequestNode = objectMapper.createObjectNode();
            String generatedRequestId = "request_" + UUID.randomUUID();
            newRequestNode.put("requestId", incomingRequestNode.hasNonNull("requestId") ? incomingRequestNode.get("requestId").asText() : generatedRequestId);

            String ownerId = incomingRequestNode.hasNonNull("ownerId") ? incomingRequestNode.get("ownerId").asText() : null;
            if (ownerId == null || ownerId.trim().isEmpty()) {
                // If ownerId missing, mark request as rejected and attach processedAt
                newRequestNode.put("ownerId", (ownerId == null ? "" : ownerId));
                newRequestNode.put("notes", incomingRequestNode.hasNonNull("notes") ? incomingRequestNode.get("notes").asText() : "");
                newRequestNode.put("requestedAt", incomingRequestNode.hasNonNull("requestedAt") ? incomingRequestNode.get("requestedAt").asText() : Instant.now().toString());
                newRequestNode.put("status", "rejected");
                newRequestNode.put("processedAt", Instant.now().toString());
                logger.warn("Adoption request missing ownerId; rejecting request {}", newRequestNode.get("requestId").asText());
            } else {
                newRequestNode.put("ownerId", ownerId);
                newRequestNode.put("notes", incomingRequestNode.hasNonNull("notes") ? incomingRequestNode.get("notes").asText() : "");
                newRequestNode.put("requestedAt", incomingRequestNode.hasNonNull("requestedAt") ? incomingRequestNode.get("requestedAt").asText() : Instant.now().toString());
                newRequestNode.put("status", "pending");
                // processedAt remains absent for pending requests
            }

            // Determine reservation policy:
            // - If the persisted pet is currently "available" and there are NO existing pending/approved requests,
            //   we set pet.status to "reserved" (exclusive reservation on first pending request).
            boolean shouldReserve = false;
            try {
                if (persistedPetNode != null) {
                    String persistedStatus = persistedPetNode.hasNonNull("status") ? persistedPetNode.get("status").asText() : null;
                    ArrayNode persistedRequests = persistedPetNode.has("adoptionRequests") && persistedPetNode.get("adoptionRequests").isArray()
                        ? (ArrayNode) persistedPetNode.get("adoptionRequests")
                        : null;

                    boolean hasActiveRequests = false;
                    if (persistedRequests != null) {
                        for (int i = 0; i < persistedRequests.size(); i++) {
                            ObjectNode r = (ObjectNode) persistedRequests.get(i);
                            if (r.hasNonNull("status")) {
                                String s = r.get("status").asText();
                                if ("pending".equalsIgnoreCase(s) || "approved".equalsIgnoreCase(s)) {
                                    hasActiveRequests = true;
                                    break;
                                }
                            }
                        }
                    }

                    if ("available".equalsIgnoreCase(persistedStatus) && !hasActiveRequests && "pending".equalsIgnoreCase(newRequestNode.get("status").asText())) {
                        shouldReserve = true;
                    }
                } else {
                    // If we can't read persisted state, be conservative: if incoming pet.status indicates available, reserve on first pending.
                    String incomingStatus = pet.getStatus();
                    if (incomingStatus != null && "available".equalsIgnoreCase(incomingStatus) && "pending".equalsIgnoreCase(newRequestNode.get("status").asText())) {
                        shouldReserve = true;
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to evaluate reservation policy, defaulting to not reserving: {}", e.getMessage());
                shouldReserve = false;
            }

            // Attach the new request node to the Pet object that will be persisted by Cyoda.
            // Ensure adoptionRequests collection exists on the Pet instance.
            if (pet.getAdoptionRequests() == null) {
                pet.setAdoptionRequests(new ArrayList<>());
            }
            // Convert newRequestNode to a generic Object instance compatible with the adoptionRequests list element type
            Object newRequestValue = objectMapper.treeToValue(newRequestNode, Object.class);
            ((List) pet.getAdoptionRequests()).add(newRequestValue);

            // Apply reservation status if applicable
            if (shouldReserve) {
                try {
                    pet.setStatus("reserved");
                    logger.info("Pet {} set to 'reserved' due to new pending adoption request {}", pet.getId(), newRequestNode.get("requestId").asText());
                } catch (Exception e) {
                    logger.warn("Unable to set pet status to reserved: {}", e.getMessage());
                }
            }

            // Emit log event for domain visibility; actual domain events are emitted by platform integration elsewhere.
            logger.info("Created adoption request {} for pet {} by owner {}", newRequestNode.get("requestId").asText(), pet.getId(), newRequestNode.get("ownerId").asText());

        } catch (Exception ex) {
            logger.error("Error while processing CreateAdoptionRequestProcessor: {}", ex.getMessage(), ex);
        }

        return pet;
    }
}