package com.java_template.application.processor;

import com.java_template.application.entity.petimportjob.version_1.PetImportJob;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

@Component
public class CreatePetsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreatePetsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    public CreatePetsProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetImportJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PetImportJob.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract PetImportJob: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract PetImportJob: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(PetImportJob entity) {
        return entity != null && entity.isValid();
    }

    private PetImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetImportJob> context) {
        Object jobObj = context.entity();
        if (jobObj == null) return null;

        // Work with JsonNode representation to avoid compile-time coupling to Lombok-generated getters/setters.
        ObjectNode jobNode = objectMapper.valueToTree(jobObj);
        String jobId = jobNode.hasNonNull("jobId") ? jobNode.get("jobId").asText() : null;
        String sourceUrl = jobNode.hasNonNull("sourceUrl") ? jobNode.get("sourceUrl").asText(null) : null;

        // Prepare result node as a mutable copy of original
        ObjectNode resultNode = jobNode.deepCopy();

        // Default safe values
        resultNode.put("createdCount", 0);
        resultNode.put("fetchedCount", 0);

        if (sourceUrl == null || sourceUrl.isBlank()) {
            logger.error("Source URL is missing for job {}", jobId != null ? jobId : "unknown");
            resultNode.put("status", "FAILED");
            resultNode.put("error", "Missing sourceUrl");
            resultNode.put("fetchedCount", 0);
            resultNode.put("createdCount", 0);
            try {
                return objectMapper.treeToValue(resultNode, PetImportJob.class);
            } catch (Exception ex) {
                logger.error("Failed to build PetImportJob result object: {}", ex.getMessage(), ex);
                return context.entity(); // fallback to original
            }
        }

        List<Pet> petsToCreate = new ArrayList<>();
        int fetched = 0;

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(sourceUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                String msg = "Failed to fetch pets from sourceUrl. HTTP status: " + statusCode;
                logger.error(msg);
                resultNode.put("status", "FAILED");
                resultNode.put("error", msg);
                resultNode.put("fetchedCount", 0);
                resultNode.put("createdCount", 0);
                try {
                    return objectMapper.treeToValue(resultNode, PetImportJob.class);
                } catch (Exception ex) {
                    logger.error("Failed to build PetImportJob result object: {}", ex.getMessage(), ex);
                    return context.entity();
                }
            }

            String body = response.body();
            JsonNode root = objectMapper.readTree(body);
            List<JsonNode> sourceItems = new ArrayList<>();

            if (root == null || root.isNull()) {
                logger.warn("Empty response body when fetching pets for job {}", jobId != null ? jobId : "unknown");
            } else if (root.isArray()) {
                for (JsonNode node : root) sourceItems.add(node);
            } else if (root.isObject()) {
                if (root.has("pets") && root.get("pets").isArray()) {
                    for (JsonNode node : root.get("pets")) sourceItems.add(node);
                } else if (root.has("data") && root.get("data").isArray()) {
                    for (JsonNode node : root.get("data")) sourceItems.add(node);
                } else {
                    sourceItems.add(root);
                }
            }

            for (JsonNode node : sourceItems) {
                ObjectNode mapped = mapNodeToPetNode(node, jobNode);
                if (mapped != null) {
                    // Convert to Pet using Jackson (avoids calling setters directly)
                    try {
                        Pet pet = objectMapper.treeToValue(mapped, Pet.class);
                        if (pet != null) petsToCreate.add(pet);
                    } catch (Exception e) {
                        logger.warn("Failed to convert mapped node to Pet for job {}: {}", jobId != null ? jobId : "unknown", e.getMessage(), e);
                    }
                }
            }

            fetched = petsToCreate.size();
            resultNode.put("fetchedCount", fetched);

            if (petsToCreate.isEmpty()) {
                resultNode.put("createdCount", 0);
                resultNode.put("status", "COMPLETED");
                resultNode.putNull("error");
                try {
                    return objectMapper.treeToValue(resultNode, PetImportJob.class);
                } catch (Exception ex) {
                    logger.error("Failed to build PetImportJob result object: {}", ex.getMessage(), ex);
                    return context.entity();
                }
            }

            // Persist pets using EntityService (this will trigger Pet workflows)
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Pet.ENTITY_NAME,
                    Pet.ENTITY_VERSION,
                    petsToCreate
            );
            List<UUID> createdIds = idsFuture.get();
            resultNode.put("createdCount", createdIds != null ? createdIds.size() : 0);
            resultNode.put("status", "COMPLETED");
            resultNode.putNull("error");
            logger.info("Job {} created {} pets (fetched {}).", jobId != null ? jobId : "unknown", resultNode.get("createdCount").asInt(), resultNode.get("fetchedCount").asInt());
        } catch (IOException | InterruptedException e) {
            logger.error("IO/Interrupted while creating pets for job {}: {}", jobId != null ? jobId : "unknown", e.getMessage(), e);
            resultNode.put("status", "FAILED");
            resultNode.put("error", "Fetch error: " + e.getMessage());
            resultNode.put("createdCount", 0);
            resultNode.put("fetchedCount", fetched);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            logger.error("Execution error while persisting pets for job {}: {}", jobId != null ? jobId : "unknown", e.getMessage(), e);
            resultNode.put("status", "FAILED");
            resultNode.put("error", "Persistence error: " + e.getMessage());
            resultNode.put("createdCount", 0);
            resultNode.put("fetchedCount", fetched);
        } catch (Exception e) {
            logger.error("Unexpected error in CreatePetsProcessor for job {}: {}", jobId != null ? jobId : "unknown", e.getMessage(), e);
            resultNode.put("status", "FAILED");
            resultNode.put("error", "Unexpected error: " + e.getMessage());
            resultNode.put("createdCount", 0);
            resultNode.put("fetchedCount", fetched);
        }

        try {
            return objectMapper.treeToValue(resultNode, PetImportJob.class);
        } catch (Exception ex) {
            logger.error("Failed to convert result node back to PetImportJob: {}", ex.getMessage(), ex);
            return context.entity();
        }
    }

    private ObjectNode mapNodeToPetNode(JsonNode node, ObjectNode jobNode) {
        if (node == null || node.isNull()) return null;
        try {
            ObjectNode petNode = objectMapper.createObjectNode();

            // petId: try common fields id or petId, otherwise generate
            String petId = null;
            if (node.hasNonNull("petId")) petId = node.get("petId").asText(null);
            if ((petId == null || petId.isBlank()) && node.hasNonNull("id")) petId = node.get("id").asText(null);
            if (petId == null || petId.isBlank()) petId = UUID.randomUUID().toString();
            petNode.put("petId", petId);

            // name
            String name = node.hasNonNull("name") ? node.get("name").asText() : ("pet-" + petId);
            petNode.put("name", name);

            // species
            String species = node.hasNonNull("species") ? node.get("species").asText() : "unknown";
            petNode.put("species", species);

            // breed
            if (node.hasNonNull("breed")) petNode.put("breed", node.get("breed").asText(null));
            else petNode.putNull("breed");

            // age
            if (node.hasNonNull("age") && node.get("age").canConvertToInt()) petNode.put("age", node.get("age").asInt());
            else petNode.putNull("age");

            // gender
            if (node.hasNonNull("gender")) petNode.put("gender", node.get("gender").asText(null));
            else petNode.putNull("gender");

            // importedFrom
            String importedFrom = jobNode != null && jobNode.hasNonNull("sourceUrl") ? jobNode.get("sourceUrl").asText() : "external";
            petNode.put("importedFrom", importedFrom);

            // description
            if (node.hasNonNull("description")) petNode.put("description", node.get("description").asText(null));
            else petNode.putNull("description");

            // photoUrls - ensure non-null list and non-blank entries
            ArrayNode photoUrls = objectMapper.createArrayNode();
            if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
                for (JsonNode urlNode : node.get("photoUrls")) {
                    if (urlNode != null && !urlNode.isNull()) {
                        String u = urlNode.asText(null);
                        if (u != null && !u.isBlank()) photoUrls.add(u);
                    }
                }
            } else if (node.hasNonNull("photoUrl")) {
                String single = node.get("photoUrl").asText(null);
                if (single != null && !single.isBlank()) photoUrls.add(single);
            }
            petNode.set("photoUrls", photoUrls);

            // status - set available by default for created pets
            petNode.put("status", "AVAILABLE");

            // tags - ensure non-null list; add imported tag
            ArrayNode tags = objectMapper.createArrayNode();
            if (node.has("tags") && node.get("tags").isArray()) {
                for (JsonNode tnode : node.get("tags")) {
                    if (tnode != null && !tnode.isNull()) {
                        String t = tnode.asText(null);
                        if (t != null && !t.isBlank()) tags.add(t);
                    }
                }
            }
            String sourceTag = jobNode != null && jobNode.hasNonNull("jobId") ? ("imported:" + jobNode.get("jobId").asText()) : "imported";
            tags.add(sourceTag);
            petNode.set("tags", tags);

            // Ensure minimal required fields are present so Jackson mapping won't fail at persistence
            if (!petNode.has("photoUrls")) petNode.set("photoUrls", objectMapper.createArrayNode());
            if (!petNode.has("tags")) petNode.set("tags", objectMapper.createArrayNode());
            if (petNode.hasNonNull("name") == false || petNode.get("name").asText("").isBlank()) petNode.put("name", "Unnamed Pet");
            if (petNode.hasNonNull("species") == false || petNode.get("species").asText("").isBlank()) petNode.put("species", "unknown");
            if (petNode.hasNonNull("status") == false || petNode.get("status").asText("").isBlank()) petNode.put("status", "AVAILABLE");
            if (petNode.hasNonNull("petId") == false || petNode.get("petId").asText("").isBlank()) petNode.put("petId", UUID.randomUUID().toString());

            return petNode;
        } catch (Exception e) {
            logger.warn("Failed to map node to Pet node: {}", e.getMessage(), e);
            return null;
        }
    }
}