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
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ImportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ImportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ImportProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
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
        PetImportJob job = context.entity();
        logger.info("ImportProcessor started for job requestId={}, sourceUrl={}", job.getRequestId(), job.getSourceUrl());

        List<Pet> petsToPersist = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try {
            String sourceUrl = job.getSourceUrl();
            if (sourceUrl == null || sourceUrl.isBlank()) {
                String err = "Source URL is empty";
                logger.error(err);
                job.setStatus("FAILED");
                job.setErrors(err);
                job.setImportedCount(0);
                return job;
            }

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(sourceUrl))
                .GET()
                .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                String err = "Failed to fetch source. HTTP status: " + statusCode;
                logger.error(err);
                job.setStatus("FAILED");
                job.setErrors(err);
                job.setImportedCount(0);
                return job;
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                String err = "Empty response body from source";
                logger.error(err);
                job.setStatus("FAILED");
                job.setErrors(err);
                job.setImportedCount(0);
                return job;
            }

            JsonNode root = objectMapper.readTree(body);
            ArrayNode items;
            if (root.isArray()) {
                items = (ArrayNode) root;
            } else if (root.has("pets") && root.get("pets").isArray()) {
                items = (ArrayNode) root.get("pets");
            } else {
                // If single object, wrap it
                items = objectMapper.createArrayNode();
                items.add(root);
            }

            for (JsonNode node : items) {
                try {
                    Pet pet = new Pet();

                    String id = node.hasNonNull("id") ? node.get("id").asText().trim() : null;
                    if (id == null || id.isBlank()) {
                        id = UUID.randomUUID().toString();
                    }
                    pet.setId(id);

                    String name = null;
                    if (node.hasNonNull("name")) name = node.get("name").asText().trim();
                    if ((name == null || name.isBlank()) && node.hasNonNull("petName")) name = node.get("petName").asText().trim();
                    if (name == null || name.isBlank()) name = "Unnamed Pet";
                    pet.setName(name);

                    Integer age = null;
                    if (node.hasNonNull("age")) {
                        try {
                            age = node.get("age").asInt();
                        } catch (Exception ex) {
                            age = 0;
                        }
                    } else {
                        age = 0;
                    }
                    pet.setAge(age);

                    String breed = node.hasNonNull("breed") ? node.get("breed").asText().trim() : null;
                    pet.setBreed(breed);

                    String description = node.hasNonNull("description") ? node.get("description").asText().trim() : null;
                    pet.setDescription(description);

                    // Use the job source URL as source if no explicit source field
                    pet.setSource(job.getSourceUrl() != null ? job.getSourceUrl() : "Petstore");

                    // Default status to AVAILABLE unless remote provides one
                    String status = node.hasNonNull("status") ? node.get("status").asText().trim() : "AVAILABLE";
                    pet.setStatus(status);

                    // Ensure validity according to Pet.isValid()
                    if (!pet.isValid()) {
                        String err = String.format("Mapped pet invalid (id=%s, name=%s). Skipping.", pet.getId(), pet.getName());
                        logger.warn(err);
                        errors.add(err);
                        continue;
                    }

                    petsToPersist.add(pet);
                } catch (Exception ex) {
                    String err = "Failed to map an item: " + ex.getMessage();
                    logger.warn(err, ex);
                    errors.add(err);
                }
            }

            // Persist pets via EntityService (addItems)
            if (!petsToPersist.isEmpty()) {
                CompletableFuture<List<java.util.UUID>> idsFuture = entityService.addItems(
                    Pet.ENTITY_NAME,
                    Pet.ENTITY_VERSION,
                    petsToPersist
                );
                List<java.util.UUID> persistedIds = idsFuture.get();
                int persisted = persistedIds != null ? persistedIds.size() : petsToPersist.size();
                job.setImportedCount(persisted);
            } else {
                job.setImportedCount(0);
            }

            // Set final status
            if (!errors.isEmpty()) {
                job.setStatus("COMPLETED"); // Partial success - mark completed but record errors
                job.setErrors(String.join("; ", errors));
            } else {
                job.setStatus("COMPLETED");
                job.setErrors("");
            }

            logger.info("ImportProcessor completed for job requestId={}, importedCount={}", job.getRequestId(), job.getImportedCount());

        } catch (Exception ex) {
            logger.error("ImportProcessor failed for job requestId={}: {}", job.getRequestId(), ex.getMessage(), ex);
            job.setStatus("FAILED");
            String message = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            job.setErrors(message);
            job.setImportedCount(job.getImportedCount() != null ? job.getImportedCount() : 0);
        }

        return job;
    }
}