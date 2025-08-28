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

@Component
public class PersistProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PersistProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        // Business logic:
        // - Fetch pets from job.sourceUrl
        // - Map to Pet entities
        // - Persist Pet entities using EntityService (addItems)
        // - Update job.importedCount, job.status, job.errors accordingly
        List<String> errors = new ArrayList<>();
        int imported = 0;

        try {
            if (job.getSourceUrl() == null || job.getSourceUrl().isBlank()) {
                job.setStatus("FAILED");
                job.setErrors("Missing sourceUrl");
                job.setImportedCount(0);
                return job;
            }

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(job.getSourceUrl()))
                .GET()
                .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                job.setStatus("FAILED");
                job.setErrors("Remote server returned status: " + response.statusCode());
                job.setImportedCount(0);
                return job;
            }

            String body = response.body();
            JsonNode root = objectMapper.readTree(body);

            if (!root.isArray()) {
                job.setStatus("FAILED");
                job.setErrors("Unexpected payload format: expected JSON array");
                job.setImportedCount(0);
                return job;
            }

            List<Pet> petsToPersist = new ArrayList<>();
            for (JsonNode node : root) {
                try {
                    Pet pet = objectMapper.treeToValue(node, Pet.class);
                    // Validate required Pet fields using existing getters
                    boolean validPet = true;
                    if (pet.getId() == null || pet.getId().isBlank()) validPet = false;
                    if (pet.getName() == null || pet.getName().isBlank()) validPet = false;
                    if (pet.getStatus() == null || pet.getStatus().isBlank()) validPet = false;
                    if (pet.getAge() == null || pet.getAge() < 0) validPet = false;

                    if (!validPet) {
                        String infoId = pet.getId() != null ? pet.getId() : "<no-id>";
                        errors.add("Invalid pet data for id: " + infoId);
                        continue;
                    }

                    if (pet.getSource() == null || pet.getSource().isBlank()) {
                        pet.setSource("Petstore");
                    }

                    // Normalize status to one of known values if needed (simple normalization)
                    String status = pet.getStatus().trim().toUpperCase();
                    if (!status.equals("AVAILABLE") && !status.equals("PENDING_ADOPTION") && !status.equals("ADOPTED")) {
                        // default to AVAILABLE if unknown
                        pet.setStatus("AVAILABLE");
                    } else {
                        pet.setStatus(status);
                    }

                    petsToPersist.add(pet);
                } catch (Exception ex) {
                    logger.warn("Failed to map remote item to Pet: {}", ex.getMessage());
                    errors.add("Mapping error: " + ex.getMessage());
                }
            }

            if (!petsToPersist.isEmpty()) {
                List<java.util.UUID> persistedIds = entityService
                    .addItems(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, petsToPersist)
                    .get();

                imported = persistedIds != null ? persistedIds.size() : petsToPersist.size();
            } else {
                imported = 0;
            }

            job.setImportedCount(imported);

            if (!errors.isEmpty()) {
                // If no items were persisted, mark FAILED; otherwise mark COMPLETED but record errors
                job.setErrors(String.join("; ", errors));
                job.setStatus(imported == 0 ? "FAILED" : "COMPLETED");
            } else {
                job.setErrors(null);
                job.setStatus("COMPLETED");
            }

        } catch (Exception ex) {
            logger.error("Error while persisting pets for job {}: {}", job.getRequestId(), ex.getMessage(), ex);
            job.setStatus("FAILED");
            job.setErrors("Exception during persist: " + ex.getMessage());
            if (job.getImportedCount() == null) job.setImportedCount(imported);
        }

        return job;
    }
}