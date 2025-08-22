package com.java_template.application.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
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

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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

        // If payload is missing or empty, mark job as FAILED and set errorMessage
        if (entity.getPayload() == null || entity.getPayload().isBlank()) {
            logger.warn("ImportJob payload is empty, marking job as FAILED");
            entity.setStatus("FAILED");
            entity.setErrorMessage("Missing payload");
            return entity;
        }

        // Attempt to extract numeric id from payload if resultItemId is not already set
        if (entity.getResultItemId() == null) {
            try {
                JsonNode root = objectMapper.readTree(entity.getPayload());
                JsonNode idNode = root.get("id");
                if (idNode != null && idNode.isNumber()) {
                    long idValue = idNode.asLong();
                    entity.setResultItemId(idValue);
                    logger.info("Extracted id={} from payload and set as resultItemId", idValue);
                } else if (idNode != null && idNode.isTextual()) {
                    // sometimes id might be textual; try parse long
                    try {
                        long idValue = Long.parseLong(idNode.asText());
                        entity.setResultItemId(idValue);
                        logger.info("Parsed textual id={} from payload and set as resultItemId", idValue);
                    } catch (NumberFormatException nfe) {
                        logger.warn("Unable to parse textual id from payload: {}", idNode.asText());
                        // leave resultItemId null; will still mark COMPLETED but without result id
                    }
                } else {
                    logger.warn("Payload does not contain numeric 'id' field");
                }
            } catch (JsonProcessingException e) {
                logger.error("Failed to parse payload JSON: {}", e.getMessage());
                entity.setStatus("FAILED");
                entity.setErrorMessage("Invalid payload JSON: " + e.getMessage());
                return entity;
            }
        }

        // Ensure status is COMPLETED (if not already FAILED)
        if (!"FAILED".equalsIgnoreCase(entity.getStatus())) {
            entity.setStatus("COMPLETED");
            // clear errorMessage on successful completion if present
            entity.setErrorMessage(null);
            logger.info("Marked ImportJob as COMPLETED for request");
        } else {
            logger.info("ImportJob already marked as FAILED; leaving state intact");
        }

        // No external entity updates here — notification will be handled by downstream processors
        return entity;
    }
}