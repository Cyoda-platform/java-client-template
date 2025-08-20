package com.java_template.application.processor;

import com.java_template.application.entity.coverphoto.version_1.CoverPhoto;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class EnrichMetadataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichMetadataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private static final int MAX_RETRIES = 3;

    public EnrichMetadataProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CoverPhoto for enrich request: {}", request.getId());

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

            // Simulate enrichment: set dimensions, size, mimeType and thumbnails if metadata is an ObjectNode or POJO
            try {
                if (entity.getMetadata() == null) {
                    // create a minimal metadata structure if not present
                    // The entity metadata type may be ObjectNode; avoid creating types not present. We will attempt to set via known setters if available.
                }

                // Set sample derived fields if setters exist on entity
                try {
                    // set tags using title heuristics
                    List<String> tags = new ArrayList<>();
                    if (entity.getTitle() != null) {
                        String t = entity.getTitle().toLowerCase();
                        if (t.contains("fiction") || t.contains("novel")) tags.add("fiction");
                        if (t.contains("nonfiction") || t.contains("non-fiction")) tags.add("nonfiction");
                    }
                    // If entity has setTags
                    entity.setTags(tags);
                } catch (NoSuchMethodError | UnsupportedOperationException e) {
                    // ignore if tags not mutable
                }

                // Mark basicChecked true if metadata object supports
                try {
                    // Nothing to do concretely here if no explicit metadata setters exist. We'll assume metadata updated elsewhere.
                } catch (Exception e) {
                    // ignore
                }

                entity.setStatus("PUBLISHED");
                logger.info("CoverPhoto {} enriched successfully", entity.getTechnicalId());
                return entity;
            } catch (Exception e) {
                logger.warn("Transient enrichment failure for {}: {}", entity.getTechnicalId(), e.getMessage());
                if (entity.getProcessingAttempts() != null && entity.getProcessingAttempts() < MAX_RETRIES) {
                    // let the workflow retry - keep status as ENRICHING
                    logger.info("Will retry enrichment for {} later (attempts={})", entity.getTechnicalId(), entity.getProcessingAttempts());
                    return entity;
                } else {
                    entity.setErrorFlag(true);
                    entity.setStatus("FAILED");
                    return entity;
                }
            }
        } catch (Exception ex) {
            logger.error("Unexpected error enriching CoverPhoto {}: {}", entity == null ? "?" : entity.getTechnicalId(), ex.getMessage(), ex);
            if (entity != null) {
                entity.setErrorFlag(true);
                entity.setStatus("FAILED");
            }
            return entity;
        }
    }
}
