package com.java_template.application.processor;

import com.java_template.application.entity.coverphoto.version_1.CoverPhoto;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class DeduplicateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeduplicateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private static final int HAMMING_THRESHOLD = 10; // from requirements

    public DeduplicateProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CoverPhoto for dedup request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CoverPhoto.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CoverPhoto entity) {
        return entity != null;
    }

    private CoverPhoto processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CoverPhoto> context) {
        CoverPhoto entity = context.entity();
        try {
            Integer attempts = entity.getProcessingAttempts();
            entity.setProcessingAttempts((attempts == null ? 0 : attempts) + 1);
            entity.setLastProcessedAt(Instant.now());

            // Compute a simple sha256 hash of the imageUrl as a proxy for image hash when none provided
            String imageUrl = entity.getImageUrl();
            if (imageUrl != null) {
                try {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] hash = digest.digest(imageUrl.getBytes());
                    String sha = Base64.getEncoder().encodeToString(hash);
                    // store as part of metadata.imageHash.sha256 if possible
                    try {
                        ObjectNode md = (ObjectNode) entity.getMetadata();
                        if (md != null) {
                            ObjectNode imageHash = md.putObject("imageHash");
                            imageHash.put("sha256", sha);
                        }
                    } catch (Exception e) {
                        // ignore if metadata structure not compatible
                    }
                } catch (Exception e) {
                    logger.warn("Unable to compute sha256 for CoverPhoto {}: {}", entity.getTechnicalId(), e.getMessage());
                }
            }

            // First try exact match by sha256 in metadata
            String sha256 = null;
            try {
                ObjectNode md = (ObjectNode) entity.getMetadata();
                if (md != null && md.has("imageHash") && md.get("imageHash").has("sha256")) {
                    sha256 = md.get("imageHash").get("sha256").asText();
                }
            } catch (Exception e) {
                // ignore
            }

            if (sha256 != null) {
                // Search for existing CoverPhoto by exact hash
                try {
                    String condition = "$.metadata.imageHash.sha256";
                    CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItemsByCondition(
                        CoverPhoto.ENTITY_NAME,
                        String.valueOf(CoverPhoto.ENTITY_VERSION),
                        com.java_template.common.util.SearchConditionRequest.group("AND",
                            com.java_template.common.util.Condition.of("$.metadata.imageHash.sha256", "EQUALS", sha256)
                        ),
                        true
                    );
                    com.fasterxml.jackson.databind.node.ArrayNode items = itemsFuture.get();
                    if (items != null && items.size() > 0) {
                        String existingTechnicalId = items.get(0).get("technicalId").asText();
                        entity.setDuplicateOf(existingTechnicalId);
                        entity.setStatus("ARCHIVED");
                        logger.info("CoverPhoto {} marked duplicate of {} by exact hash", entity.getTechnicalId(), existingTechnicalId);
                        return entity;
                    }
                } catch (Exception e) {
                    logger.warn("Error searching by exact hash: {}", e.getMessage());
                }
            }

            // No exact match found - perform a simple perceptual match simulation using URL equality or origin id
            try {
                // Try match by coverId/origin id
                if (entity.getCoverId() != null) {
                    CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItemsByCondition(
                        CoverPhoto.ENTITY_NAME,
                        String.valueOf(CoverPhoto.ENTITY_VERSION),
                        com.java_template.common.util.SearchConditionRequest.group("AND",
                            com.java_template.common.util.Condition.of("$.coverId", "EQUALS", entity.getCoverId())
                        ),
                        true
                    );
                    com.fasterxml.jackson.databind.node.ArrayNode items = itemsFuture.get();
                    if (items != null && items.size() > 0) {
                        String existingTechnicalId = items.get(0).get("technicalId").asText();
                        entity.setDuplicateOf(existingTechnicalId);
                        entity.setStatus("ARCHIVED");
                        logger.info("CoverPhoto {} marked duplicate of {} by coverId", entity.getTechnicalId(), existingTechnicalId);
                        return entity;
                    }
                }
            } catch (Exception e) {
                logger.warn("Error searching by coverId: {}", e.getMessage());
            }

            // No duplicate found
            entity.setDuplicateOf(null);
            // advance to ENRICHING - in workflow this transition will handle next state; here just set status to ENRICHING
            entity.setStatus("ENRICHING");
            logger.info("CoverPhoto {} no duplicate detected", entity.getTechnicalId());
            return entity;
        } catch (Exception ex) {
            logger.error("Unexpected error in DeduplicateProcessor for {}: {}", entity == null ? "?" : entity.getTechnicalId(), ex.getMessage(), ex);
            return entity;
        }
    }
}
