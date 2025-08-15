package com.java_template.application.processor;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class FetchAndParseProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchAndParseProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public FetchAndParseProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FetchAndParse for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(IngestionJob.class)
            .validate(this::isValidEntity, "Invalid ingestion job")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestionJob job) {
        return job != null && job.isValid();
    }

    private IngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestionJob> context) {
        IngestionJob job = context.entity();
        try {
            job.setStatus("running");
            job.setUpdatedAt(Instant.now().toString());

            String url = job.getSourceUrl();
            if (url == null || url.isBlank()) {
                job.getErrors().add("sourceUrl_missing");
                job.setStatus("failed");
                logger.error("Ingestion job {} missing sourceUrl", job.getId());
                return job;
            }

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

            // Retry with exponential backoff up to 3 attempts
            int maxAttempts = 3;
            int attempt = 0;
            HttpResponse<String> response = null;
            while (attempt < maxAttempts) {
                try {
                    attempt++;
                    response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() >= 200 && response.statusCode() < 300) break;
                    logger.warn("Fetch attempt {} returned status {} for job {}", attempt, response.statusCode(), job.getId());
                } catch (Exception e) {
                    logger.warn("Fetch attempt {} failed for job {}: {}", attempt, job.getId(), e.getMessage());
                }
                // backoff
                try { Thread.sleep(500L * attempt); } catch (InterruptedException ignored) {}
            }

            if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
                job.getErrors().add("source_unreachable");
                job.setStatus("failed");
                job.setUpdatedAt(Instant.now().toString());
                logger.error("Failed to fetch source for job {} after {} attempts", job.getId(), maxAttempts);
                return job;
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                job.getErrors().add("empty_payload");
                job.setStatus("failed");
                job.setUpdatedAt(Instant.now().toString());
                logger.error("Empty payload for job {}", job.getId());
                return job;
            }

            // Parse payload. Accept either an array of pet objects or an object with data array
            List<ObjectNode> candidates = new ArrayList<>();
            try {
                if (body.trim().startsWith("[")) {
                    ArrayNode arr = (ArrayNode) objectMapper.readTree(body);
                    for (int i = 0; i < arr.size(); i++) candidates.add((ObjectNode) arr.get(i));
                } else {
                    ObjectNode root = (ObjectNode) objectMapper.readTree(body);
                    if (root.has("data") && root.get("data").isArray()) {
                        ArrayNode arr = (ArrayNode) root.get("data");
                        for (int i = 0; i < arr.size(); i++) candidates.add((ObjectNode) arr.get(i));
                    } else if (root.has("pets") && root.get("pets").isArray()) {
                        ArrayNode arr = (ArrayNode) root.get("pets");
                        for (int i = 0; i < arr.size(); i++) candidates.add((ObjectNode) arr.get(i));
                    } else {
                        // Try to coerce single object into candidate
                        candidates.add(root);
                    }
                }
            } catch (Exception e) {
                job.getErrors().add("parse_error=" + e.getMessage());
                job.setStatus("failed");
                job.setUpdatedAt(Instant.now().toString());
                logger.error("Failed to parse payload for job {}: {}", job.getId(), e.getMessage());
                return job;
            }

            if (candidates.isEmpty()) {
                job.getErrors().add("no_pets_found");
            }

            // Validate and store candidates as serialized JSON strings in job.errors prefixed with parsed_pet=
            int accepted = 0;
            for (ObjectNode node : candidates) {
                try {
                    // Map allowed fields only: externalId, name, species, breed, age, gender, photos, description, status
                    Pet dto = new Pet();
                    if (node.hasNonNull("externalId")) dto.setExternalId(node.get("externalId").asText());
                    if (node.hasNonNull("name")) dto.setName(node.get("name").asText());
                    if (node.hasNonNull("species")) dto.setSpecies(node.get("species").asText());
                    if (node.hasNonNull("breed")) dto.setBreed(node.get("breed").asText());
                    if (node.hasNonNull("age")) dto.setAge(node.get("age").asInt());
                    if (node.hasNonNull("gender")) dto.setGender(node.get("gender").asText());
                    if (node.hasNonNull("description")) dto.setDescription(node.get("description").asText());
                    if (node.hasNonNull("status")) dto.setStatus(node.get("status").asText()); else dto.setStatus("available");
                    if (node.hasNonNull("photos") && node.get("photos").isArray()) {
                        List<String> photos = objectMapper.convertValue(node.get("photos"), new TypeReference<List<String>>(){});
                        dto.setPhotos(photos);
                    }

                    // Validate required fields (name, species, status)
                    if (dto.getName() == null || dto.getName().isBlank() || dto.getSpecies() == null || dto.getSpecies().isBlank() || dto.getStatus() == null || dto.getStatus().isBlank()) {
                        job.getErrors().add("validation_failed_missing_fields=" + (dto.getExternalId() == null ? "unknown" : dto.getExternalId()));
                        continue;
                    }

                    // Serialize dto and store
                    String ser = objectMapper.writeValueAsString(dto);
                    job.getErrors().add("parsed_pet=" + ser);
                    accepted++;
                } catch (Exception e) {
                    job.getErrors().add("candidate_parse_error=" + e.getMessage());
                }
            }

            job.setImportedCount(0);
            job.setStatus("processing");
            job.getErrors().add("parsed_candidates=" + accepted);
            job.setUpdatedAt(Instant.now().toString());

            logger.info("Ingestion job {} parsed {} candidate pets", job.getId(), accepted);
        } catch (Exception e) {
            job.getErrors().add(e.getMessage());
            job.setStatus("failed");
            job.setUpdatedAt(Instant.now().toString());
            logger.error("Error in FetchAndParseProcessor for job {}: {}", job.getId(), e.getMessage(), e);
        }
        return job;
    }
}
