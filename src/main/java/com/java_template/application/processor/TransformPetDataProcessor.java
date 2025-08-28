package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.petingestionjob.version_1.PetIngestionJob;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class TransformPetDataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TransformPetDataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public TransformPetDataProcessor(SerializerFactory serializerFactory,
                                     EntityService entityService,
                                     ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetIngestionJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PetIngestionJob.class)
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

    private boolean isValidEntity(PetIngestionJob entity) {
        return entity != null && entity.isValid();
    }

    private PetIngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetIngestionJob> context) {
        PetIngestionJob job = context.entity();

        // Use a local errors list and local status to avoid compile-time dependency on specific getters/setters
        List<String> localErrors = new ArrayList<>();
        String localStatus = null;

        // Try to pick up existing errors from the job if available
        try {
            Method getErrorsM = job.getClass().getMethod("getErrors");
            Object errs = getErrorsM.invoke(job);
            if (errs instanceof List) {
                localErrors.addAll((List) errs);
            }
        } catch (Exception ignored) {
            // ignore if method not present
        }

        try {
            // Mark job in transforming state
            localStatus = "TRANSFORMING";

            String sourceUrl = null;
            try {
                Method m = job.getClass().getMethod("getSourceUrl");
                Object val = m.invoke(job);
                if (val instanceof String) sourceUrl = (String) val;
            } catch (NoSuchMethodException ns) {
                // fallback to direct field access not possible here; assume null
            }

            if (sourceUrl == null || sourceUrl.isBlank()) {
                localErrors.add("Missing sourceUrl on ingestion job");
                localStatus = "FAILED";
                applyJobFields(job, localErrors, localStatus);
                return job;
            }

            // Fetch data from source URL
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(sourceUrl))
                .GET()
                .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                localErrors.add("Failed to fetch source, HTTP status: " + statusCode);
                localStatus = "FAILED";
                applyJobFields(job, localErrors, localStatus);
                return job;
            }

            String body = response.body();
            JsonNode root = objectMapper.readTree(body);

            List<Pet> petsToPersist = new ArrayList<>();

            // Helper to map a node -> Pet with safe defaults
            java.util.function.Consumer<JsonNode> mapNode = node -> {
                try {
                    // Try to convert node directly to Pet. Unknown/missing fields are ignored by ObjectMapper.
                    Pet pet = objectMapper.treeToValue(node, Pet.class);

                    // Ensure required fields for Pet.isValid()
                    if (pet.getId() == null || pet.getId().isBlank()) {
                        pet.setId(UUID.randomUUID().toString());
                    }
                    if (pet.getImportedAt() == null || pet.getImportedAt().isBlank()) {
                        pet.setImportedAt(Instant.now().toString());
                    }
                    String jobName = null;
                    try {
                        Method m = job.getClass().getMethod("getJobName");
                        Object val = m.invoke(job);
                        if (val instanceof String) jobName = (String) val;
                    } catch (Exception ignored) {}

                    if (pet.getSource() == null || pet.getSource().isBlank()) {
                        // Prefer jobName, fallback to sourceUrl
                        pet.setSource(jobName != null && !jobName.isBlank()
                            ? jobName : sourceUrl);
                    }
                    if (pet.getStatus() == null || pet.getStatus().isBlank()) {
                        // Default to AVAILABLE for newly ingested pets
                        pet.setStatus("AVAILABLE");
                    }
                    // Sanitize photos/tags: ensure lists have no blank entries (Pet.isValid will check later)
                    if (pet.getPhotos() != null) {
                        List<String> photosClean = new ArrayList<>();
                        for (String p : pet.getPhotos()) {
                            if (p != null && !p.isBlank()) photosClean.add(p);
                        }
                        pet.setPhotos(photosClean);
                    }
                    if (pet.getTags() != null) {
                        List<String> tagsClean = new ArrayList<>();
                        for (String t : pet.getTags()) {
                            if (t != null && !t.isBlank()) tagsClean.add(t);
                        }
                        pet.setTags(tagsClean);
                    }

                    // Only add pet if it satisfies minimal validity (id, name, species, status).
                    if (pet.getName() == null || pet.getName().isBlank()
                        || pet.getSpecies() == null || pet.getSpecies().isBlank()) {
                        localErrors.add("Skipping pet without required name/species for id: " + pet.getId());
                    } else {
                        petsToPersist.add(pet);
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to map a pet item from source: {}", ex.getMessage(), ex);
                    localErrors.add("Failed to map item: " + ex.getMessage());
                }
            };

            if (root.isArray()) {
                for (JsonNode item : root) {
                    mapNode.accept(item);
                }
            } else if (root.isObject()) {
                // Try common wrapper keys where APIs may place arrays
                JsonNode items = null;
                if (root.has("pets") && root.get("pets").isArray()) {
                    items = root.get("pets");
                } else if (root.has("items") && root.get("items").isArray()) {
                    items = root.get("items");
                } else if (root.has("data") && root.get("data").isArray()) {
                    items = root.get("data");
                }

                if (items != null) {
                    for (JsonNode item : items) {
                        mapNode.accept(item);
                    }
                } else {
                    // Treat single object as single pet
                    mapNode.accept(root);
                }
            } else {
                localErrors.add("Unexpected payload format from source");
                localStatus = "FAILED";
                applyJobFields(job, localErrors, localStatus);
                return job;
            }

            if (petsToPersist.isEmpty()) {
                if (localErrors.isEmpty()) {
                    localErrors.add("No valid pet items extracted from source");
                }
                try {
                    Method setProcessedCount = job.getClass().getMethod("setProcessedCount", int.class);
                    setProcessedCount.invoke(job, 0);
                } catch (Exception ignored) {}
                localStatus = "FAILED";
                applyJobFields(job, localErrors, localStatus);
                return job;
            }

            // Persist transformed Pet entities (allowed: add other entities)
            CompletableFuture<List<java.util.UUID>> idsFuture = entityService.addItems(
                Pet.ENTITY_NAME,
                Pet.ENTITY_VERSION,
                petsToPersist
            );
            List<java.util.UUID> persistedIds = idsFuture.get();

            try {
                Method setProcessedCount = job.getClass().getMethod("setProcessedCount", int.class);
                setProcessedCount.invoke(job, persistedIds != null ? persistedIds.size() : petsToPersist.size());
            } catch (Exception ignored) {}

            // Advance job to next phase (PERSISTING)
            localStatus = "PERSISTING";

        } catch (Exception e) {
            logger.error("Error while transforming pet data for job {}: {}", getJobNameSafe(job), e.getMessage(), e);
            localErrors.add(e.getMessage() != null ? e.getMessage() : e.toString());
            localStatus = "FAILED";
        }

        applyJobFields(job, localErrors, localStatus);
        return job;
    }

    private void applyJobFields(PetIngestionJob job, List<String> localErrors, String localStatus) {
        // Apply errors: try to merge into existing list or set via setter
        try {
            Method getErrorsM = job.getClass().getMethod("getErrors");
            Object errs = getErrorsM.invoke(job);
            if (errs instanceof List) {
                ((List) errs).addAll(localErrors);
            } else {
                try {
                    Method setErrorsM = job.getClass().getMethod("setErrors", List.class);
                    setErrorsM.invoke(job, localErrors);
                } catch (Exception ignored) {}
            }
        } catch (NoSuchMethodException ns) {
            try {
                Method setErrorsM = job.getClass().getMethod("setErrors", List.class);
                setErrorsM.invoke(job, localErrors);
            } catch (Exception ignored) {}
        } catch (Exception e) {
            logger.warn("Failed to apply errors to job via reflection: {}", e.getMessage());
        }

        if (localStatus != null) {
            try {
                Method setStatusM = job.getClass().getMethod("setStatus", String.class);
                setStatusM.invoke(job, localStatus);
            } catch (Exception e) {
                // ignore if not present
            }
        }
    }

    private String getJobNameSafe(PetIngestionJob job) {
        try {
            Method m = job.getClass().getMethod("getJobName");
            Object val = m.invoke(job);
            if (val instanceof String) return (String) val;
        } catch (Exception ignored) {}
        return "<unknown>";
    }
}