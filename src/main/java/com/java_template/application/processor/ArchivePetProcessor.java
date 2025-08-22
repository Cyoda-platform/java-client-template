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

@Component
public class ArchivePetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchivePetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ArchivePetProcessor(SerializerFactory serializerFactory) {
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

        // Idempotency: if already archived, no-op
        try {
            String currentStatus = entity.getStatus();
            if (currentStatus != null && "archived".equalsIgnoreCase(currentStatus)) {
                logger.info("Pet {} is already archived. No action taken.", entity.getId());
                return entity;
            }
        } catch (Exception e) {
            // defensive: if getter not present or fails, log and proceed to set archived
            logger.debug("Unable to read current status safely, proceeding to archive. Reason: {}", e.getMessage());
        }

        // Set status to archived as per workflow rules.
        try {
            entity.setStatus("archived");
            logger.info("Pet {} status set to archived.", entity.getId());
        } catch (Exception e) {
            logger.error("Failed to set pet status to archived for pet id {}: {}", entity.getId(), e.getMessage());
        }

        // Update audit timestamp if available
        try {
            entity.setUpdatedAt(Instant.now().toString());
        } catch (Exception e) {
            // ignore if setter not available
            logger.debug("setUpdatedAt not available on Pet entity: {}", e.getMessage());
        }

        // Archive related metadata: best-effort, operate only if fields exist.
        // We will attempt to clear transient or large metadata references where possible, without creating/updating other entities.
        try {
            if (entity.getPhotos() != null && !entity.getPhotos().isEmpty()) {
                // Keep photos as-is for record, but log that they remain attached. Do not remove unless required by policy.
                logger.debug("Pet {} has {} photos attached at archive time.", entity.getId(), entity.getPhotos().size());
            }
        } catch (Exception e) {
            logger.debug("No photos handling available on Pet entity: {}", e.getMessage());
        }

        // If adoptionRequests are embedded, mark ambiguous/pending requests as cancelled where possible (best-effort)
        try {
            if (entity.getAdoptionRequests() != null) {
                entity.getAdoptionRequests().forEach(req -> {
                    try {
                        String reqStatus = req.getStatus();
                        if (reqStatus == null) return;
                        if ("pending".equalsIgnoreCase(reqStatus) || "reserved".equalsIgnoreCase(reqStatus)) {
                            req.setStatus("cancelled");
                            req.setProcessedAt(Instant.now().toString());
                            logger.debug("Marked embedded adoption request {} as cancelled for pet {}", req.getRequestId(), entity.getId());
                        }
                    } catch (Exception ex) {
                        // ignore individual adoption request problems
                        logger.debug("Unable to update an adoption request while archiving pet {}: {}", entity.getId(), ex.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            logger.debug("AdoptionRequests handling not supported on Pet entity: {}", e.getMessage());
        }

        // Note: Do not update other entities here. This processor only mutates the triggering entity's state.
        return entity;
    }
}