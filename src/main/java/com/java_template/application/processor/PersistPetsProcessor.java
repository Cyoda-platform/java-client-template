package com.java_template.application.processor;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class PersistPetsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistPetsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PersistPetsProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PersistPets for request: {}", request.getId());

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
            // Collect parsed_pet entries
            List<String> parsed = job.getErrors().stream().filter(s -> s != null && s.startsWith("parsed_pet=")).toList();
            if (parsed.isEmpty()) {
                job.getErrors().add("no_candidates_to_persist");
                job.setStatus("failed");
                job.setUpdatedAt(Instant.now().toString());
                logger.warn("No parsed candidates for job {}", job.getId());
                return job;
            }

            int created = 0;
            for (String entry : parsed) {
                try {
                    String json = entry.substring(entry.indexOf('=') + 1);
                    Pet dto = objectMapper.readValue(json, Pet.class);

                    // Deduplication: prefer externalId
                    boolean duplicate = false;
                    if (dto.getExternalId() != null && !dto.getExternalId().isBlank()) {
                        try {
                            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> foundFuture = entityService.getItemsByCondition(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), SearchConditionRequest.group("AND", Condition.of("$.externalId", "EQUALS", dto.getExternalId())), true);
                            var found = foundFuture.join();
                            if (found != null && found.size() > 0) duplicate = true;
                        } catch (Exception e) {
                            logger.debug("EntityService externalId lookup failed: {}", e.getMessage());
                        }
                    }

                    if (!duplicate) {
                        try {
                            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> foundFuture = entityService.getItemsByCondition(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), SearchConditionRequest.group("AND",
                                Condition.of("$.name", "IEQUALS", dto.getName()),
                                Condition.of("$.species", "IEQUALS", dto.getSpecies()),
                                Condition.of("$.breed", "IEQUALS", dto.getBreed()),
                                Condition.of("$.age", "EQUALS", dto.getAge() == null ? "" : String.valueOf(dto.getAge()))
                            ), true);
                            var found = foundFuture.join();
                            if (found != null && found.size() > 0) duplicate = true;
                        } catch (Exception e) {
                            logger.debug("EntityService key lookup failed: {}", e.getMessage());
                        }
                    }

                    if (duplicate) {
                        job.getErrors().add("duplicate_candidate=" + (dto.getExternalId() == null ? dto.getName() : dto.getExternalId()));
                        continue;
                    }

                    // Create new Pet via EntityService.addItem
                    dto.setId(UUID.randomUUID().toString());
                    if (dto.getStatus() == null || dto.getStatus().isBlank()) dto.setStatus("available");
                    dto.setCreatedAt(Instant.now().toString());
                    dto.setUpdatedAt(Instant.now().toString());

                    CompletableFuture<java.util.UUID> addFuture = entityService.addItem(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), objectMapper.convertValue(dto, ObjectNode.class));
                    addFuture.join();
                    created++;
                    logger.info("Persisted new pet (externalId={}) for job {}", dto.getExternalId(), job.getId());

                } catch (Exception e) {
                    job.getErrors().add("persist_error=" + e.getMessage());
                    logger.error("Error persisting candidate for job {}: {}", job.getId(), e.getMessage(), e);
                }
            }

            job.setImportedCount((job.getImportedCount() == null ? 0 : job.getImportedCount()) + created);
            job.setStatus(created > 0 ? "completed" : "failed");
            job.setUpdatedAt(Instant.now().toString());
            logger.info("Job {} persisted {} new pets", job.getId(), created);
        } catch (Exception e) {
            job.getErrors().add(e.getMessage());
            job.setStatus("failed");
            job.setUpdatedAt(Instant.now().toString());
            logger.error("Error in PersistPetsProcessor for job {}: {}", job.getId(), e.getMessage(), e);
        }
        return job;
    }
}
