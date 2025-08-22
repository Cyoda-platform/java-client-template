package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.petingestionjob.version_1.PetIngestionJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class StartIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public StartIngestionProcessor(SerializerFactory serializerFactory,
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
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(PetIngestionJob entity) {
        if (entity == null) return false;
        // Basic validation: source and requestedBy should be present to start ingestion
        try {
            String src = entity.getSource();
            String reqBy = entity.getRequestedBy();
            return src != null && !src.trim().isEmpty() && reqBy != null && !reqBy.trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private PetIngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetIngestionJob> context) {
        PetIngestionJob job = context.entity();
        logger.info("StartIngestionProcessor started for jobId={}", job.getJobId());

        // mark as running and set startedAt
        try {
            job.setStatus("running");
        } catch (Exception e) {
            logger.warn("Unable to set status on job {}", job.getJobId(), e);
        }
        try {
            job.setStartedAt(Instant.now().toString());
        } catch (Exception e) {
            logger.warn("Unable to set startedAt on job {}", job.getJobId(), e);
        }

        // initialize counters and errors
        int imported = 0;
        List<String> errors = job.getErrors();
        if (errors == null) {
            errors = new ArrayList<>();
            try {
                job.setErrors(errors);
            } catch (Exception e) {
                logger.warn("Unable to set errors list on job {}", job.getJobId(), e);
            }
        }

        String source = job.getSource();
        if (source == null || source.trim().isEmpty()) {
            String err = "Missing or empty source for ingestion";
            errors.add(err);
            logger.error(err + " jobId={}", job.getJobId());
            job.setStatus("failed");
            job.setCompletedAt(Instant.now().toString());
            job.setImportedCount(imported);
            job.setErrors(errors);
            return job;
        }

        // Attempt to handle inline JSON array source payload (simple simulation) or mark unsupported.
        try {
            String trimmed = source.trim();
            if (trimmed.startsWith("[")) {
                JsonNode root = objectMapper.readTree(trimmed);
                if (root instanceof ArrayNode) {
                    ArrayNode arr = (ArrayNode) root;
                    for (JsonNode node : arr) {
                        try {
                            // map record to Pet entity using ObjectMapper and add via EntityService
                            Pet pet = objectMapper.treeToValue(node, Pet.class);
                            if (pet == null) {
                                String e = "Mapped pet record is null, skipping";
                                errors.add(e);
                                logger.warn(e + " jobId={}", job.getJobId());
                                continue;
                            }
                            CompletableFuture<UUID> idFuture = entityService.addItem(
                                Pet.ENTITY_NAME,
                                String.valueOf(Pet.ENTITY_VERSION),
                                pet
                            );
                            // wait for completion to ensure import accounted
                            try {
                                UUID createdId = idFuture.get();
                                logger.info("Imported pet (createdTechnicalId={}) for jobId={}", createdId, job.getJobId());
                                imported++;
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                String e = "Interrupted while adding pet: " + ie.getMessage();
                                errors.add(e);
                                logger.error(e, ie);
                            } catch (ExecutionException ee) {
                                String e = "Failed to add pet: " + ee.getCause();
                                errors.add(e);
                                logger.error(e, ee);
                            }
                        } catch (Exception mappingEx) {
                            String e = "Failed to map pet record: " + mappingEx.getMessage();
                            errors.add(e);
                            logger.error(e, mappingEx);
                        }
                    }
                    // finished processing inline array
                } else {
                    String e = "Source JSON is not an array";
                    errors.add(e);
                    logger.error(e + " jobId={}", job.getJobId());
                }
            } else {
                // Unsupported source format in this simple implementation.
                String e = "Unsupported source format. Expected inline JSON array for this processor.";
                errors.add(e);
                logger.warn(e + " jobId={}", job.getJobId());
            }
        } catch (IOException ioEx) {
            String e = "Failed to parse source: " + ioEx.getMessage();
            errors.add(e);
            logger.error(e, ioEx);
        } catch (Exception ex) {
            String e = "Unexpected error during ingestion: " + ex.getMessage();
            errors.add(e);
            logger.error(e, ex);
        }

        // finalize job state
        job.setImportedCount(imported);
        job.setErrors(errors);
        job.setCompletedAt(Instant.now().toString());
        if (errors.isEmpty()) {
            job.setStatus("completed");
            logger.info("Ingestion completed successfully for jobId={}, importedCount={}", job.getJobId(), imported);
        } else {
            job.setStatus("failed");
            logger.info("Ingestion completed with errors for jobId={}, importedCount={}, errors={}", job.getJobId(), imported, errors.size());
        }

        return job;
    }
}