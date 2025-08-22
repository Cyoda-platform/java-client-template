package com.java_template.application.processor;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Component
public class PetLifecycleProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetLifecycleProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PetLifecycleProcessor(SerializerFactory serializerFactory) {
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
        if (entity == null) {
            logger.warn("Received null Pet entity in processing context");
            return null;
        }

        String status = safeTrimLower(entity.getStatus());
        try {
            // If no status, assume new
            if (status == null || status.isEmpty()) {
                status = "new";
                entity.setStatus(status);
                logger.debug("Pet {} had no status; defaulting to 'new'", entity.getId());
            }

            // 1) Validation step for newly created pets
            if ("new".equals(status)) {
                boolean missingName = isNullOrBlank(entity.getName());
                boolean missingSpecies = isNullOrBlank(entity.getSpecies());
                if (missingName || missingSpecies) {
                    entity.setStatus("validation_failed");
                    logger.warn("Pet {} validation failed: missing required fields (name or species). name='{}' species='{}'",
                            entity.getId(), entity.getName(), entity.getSpecies());
                    // emit event: PetValidationFailed (logged here)
                    logger.info("Event=PetValidationFailed petId={}", entity.getId());
                    return entity;
                } else {
                    entity.setStatus("validated");
                    logger.info("Pet {} validated. Event=PetValidated", entity.getId());
                    // continue to enrichment when next processor run or immediate pass-through to enrichment below
                    status = "validated";
                }
            }

            // 2) Enrichment step for validated pets
            if ("validated".equals(status)) {
                // Normalize breed if present
                String breed = entity.getBreed();
                if (!isNullOrBlank(breed)) {
                    String normalized = normalizeBreed(breed);
                    if (!Objects.equals(normalized, breed)) {
                        entity.setBreed(normalized);
                        logger.debug("Pet {} breed normalized from '{}' to '{}'", entity.getId(), breed, normalized);
                    }
                }

                // Photos validation: simple syntactic check (URLs should start with http/https)
                List<?> photos = entity.getPhotos();
                if (photos != null && !photos.isEmpty()) {
                    boolean allAccessibleLike = true;
                    for (Object p : photos) {
                        String url = String.valueOf(p);
                        if (isNullOrBlank(url) || !(url.startsWith("http://") || url.startsWith("https://"))) {
                            allAccessibleLike = false;
                            break;
                        }
                    }
                    if (!allAccessibleLike) {
                        // Policy: mark severe enrichment failures as validation_failed
                        entity.setStatus("validation_failed");
                        logger.warn("Pet {} enrichment failed: one or more photos invalid or inaccessible. Event=PetEnrichmentFailed", entity.getId());
                        return entity;
                    } else {
                        entity.setStatus("available");
                        logger.info("Pet {} enriched successfully. Event=PetEnriched", entity.getId());
                    }
                } else {
                    // No photos provided - acceptable but log for review; set to available
                    entity.setStatus("available");
                    logger.info("Pet {} enriched (no photos present). Event=PetEnriched", entity.getId());
                }
            }

            // Additional transitions (reserved/adopted/archived) are managed by other processors (CreateAdoptionRequestProcessor, ApproveAdoptionProcessor, ArchivePetProcessor)
            // This processor focuses on validation and enrichment stages as per canonical workflow.

        } catch (Exception ex) {
            logger.error("Unexpected error while processing Pet {}: {}", entity.getId(), ex.getMessage(), ex);
            // On unexpected failure, mark for manual review without throwing to keep idempotency
            try {
                entity.setStatus("validation_failed");
            } catch (Exception ignore) {
                // best-effort only
            }
        } finally {
            // update timestamp if available
            try {
                entity.setUpdatedAt(Instant.now().toString());
            } catch (Exception ignore) {
                // ignore if setter not present
            }
        }

        return entity;
    }

    private static boolean isNullOrBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String safeTrimLower(String s) {
        return s == null ? null : s.trim().toLowerCase();
    }

    private static String normalizeBreed(String breed) {
        if (breed == null) return null;
        String[] parts = breed.trim().toLowerCase().split("[^a-z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1));
            sb.append(' ');
        }
        return sb.toString().trim();
    }
}