package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.importjob.version_1.ImportJob;
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

@Component
public class MarkJobCompletedProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarkJobCompletedProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    public MarkJobCompletedProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportJob entity) {
        return entity != null && entity.isValid();
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob entity = context.entity();
        if (entity == null) return null;

        try {
            // Ensure job is marked completed and has resultItemId if possible.
            // If resultItemId is missing but payload contains an "id" field, extract it.
            String payload = entity.getPayload();
            if (payload != null && !payload.isBlank()) {
                try {
                    JsonNode root = objectMapper.readTree(payload);
                    if (entity.getResultItemId() == null && root.has("id") && !root.get("id").isNull()) {
                        JsonNode idNode = root.get("id");
                        if (idNode.canConvertToLong()) {
                            entity.setResultItemId(idNode.longValue());
                            logger.info("Extracted resultItemId {} from payload for ImportJob", entity.getResultItemId());
                        } else if (idNode.isTextual()) {
                            try {
                                long parsed = Long.parseLong(idNode.asText());
                                entity.setResultItemId(parsed);
                                logger.info("Parsed resultItemId {} from textual payload id for ImportJob", entity.getResultItemId());
                            } catch (NumberFormatException nfe) {
                                // ignore - cannot parse textual id
                                logger.debug("Could not parse textual id from payload: {}", idNode.asText());
                            }
                        }
                    }
                } catch (Exception ex) {
                    // Non-fatal: log and continue. We don't want to fail the whole processor because of payload parse issues.
                    logger.debug("Failed to parse payload JSON for ImportJob id {}: {}", context.request().getId(), ex.getMessage());
                }
            }

            // If status is not COMPLETED but we have a resultItemId, set COMPLETED.
            if (entity.getStatus() == null || !entity.getStatus().equalsIgnoreCase("COMPLETED")) {
                if (entity.getResultItemId() != null) {
                    entity.setStatus("COMPLETED");
                    entity.setErrorMessage(null);
                    logger.info("Marking ImportJob as COMPLETED with resultItemId={}", entity.getResultItemId());
                } else {
                    // If no resultItemId but status is not COMPLETED, leave as-is.
                    logger.debug("ImportJob status not COMPLETED and no resultItemId available; leaving state unchanged.");
                }
            }

        } catch (Exception e) {
            // As a safety net, if something unexpected happens, set job to FAILED with an error message.
            logger.error("Unexpected error while marking job completed: {}", e.getMessage(), e);
            entity.setStatus("FAILED");
            entity.setErrorMessage("MarkJobCompletedProcessor error: " + e.getMessage());
        }

        return entity;
    }
}