package com.java_template.application.processor;

import com.java_template.application.entity.coverphoto.version_1.CoverPhoto;
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

@Component
public class PublishProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PublishProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PublishProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CoverPhoto for request: {}", request.getId());

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
        return entity != null && entity.isValid();
    }

    private CoverPhoto processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CoverPhoto> context) {
        CoverPhoto entity = context.entity();

        if (entity == null) {
            logger.warn("Received null CoverPhoto entity in PublishProcessor");
            return null;
        }

        try {
            String status = entity.getIngestionStatus();
            if (status == null) status = "";

            // Business rule:
            // - If a CoverPhoto is in INGESTED state the PublishProcessor should move it to PUBLISHED.
            // - If already published or in another state, do nothing.
            // Note: Deduplication and cross-entity checks should be handled by criteria or other processors.
            if ("INGESTED".equalsIgnoreCase(status)) {
                entity.setIngestionStatus("PUBLISHED");

                // Ensure publishedDate is set (use current timestamp if missing)
                if (entity.getPublishedDate() == null || entity.getPublishedDate().isBlank()) {
                    entity.setPublishedDate(Instant.now().toString());
                }

                // Update updatedAt to current time
                entity.setUpdatedAt(Instant.now().toString());

                logger.info("CoverPhoto [{}] transitioned from INGESTED to PUBLISHED", entity.getId());
            } else {
                logger.debug("CoverPhoto [{}] ingestionStatus is '{}'; no publish action taken", entity.getId(), status);
            }
        } catch (Exception ex) {
            logger.error("Error while publishing CoverPhoto [{}]: {}", entity.getId(), ex.getMessage(), ex);
            // On unexpected error mark entity as FAILED to surface issue in workflow
            try {
                entity.setIngestionStatus("FAILED");
                entity.setUpdatedAt(Instant.now().toString());
            } catch (Exception ignore) {
                logger.debug("Failed to mark CoverPhoto as FAILED", ignore);
            }
        }

        return entity;
    }
}