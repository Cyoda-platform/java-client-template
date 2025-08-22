package com.java_template.application.processor;

import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AddImportTimestampProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AddImportTimestampProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    public AddImportTimestampProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
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

        // Enrichment: add importTimestamp to the payload JSON.
        // The payload field on ImportJob is a serialized JSON string.
        // Use ObjectMapper to parse and update the JSON, then write it back.
        try {
            String payload = entity.getPayload();
            if (payload == null || payload.isBlank()) {
                // Should not happen because isValid() checked payload, but guard defensively
                String msg = "Payload is empty, cannot add importTimestamp";
                logger.error(msg);
                entity.setStatus("FAILED");
                entity.setErrorMessage(msg);
                return entity;
            }

            ObjectNode payloadNode;
            try {
                payloadNode = (ObjectNode) objectMapper.readTree(payload);
            } catch (ClassCastException cce) {
                // If parsed JSON is not an object, wrap it into an object under a "data" field
                ObjectNode wrapper = objectMapper.createObjectNode();
                wrapper.set("data", objectMapper.readTree(payload));
                payloadNode = wrapper;
            }

            // Add or overwrite importTimestamp
            payloadNode.put("importTimestamp", Instant.now().toString());

            // Write back to the ImportJob payload
            String updatedPayload = objectMapper.writeValueAsString(payloadNode);
            entity.setPayload(updatedPayload);

            // Keep existing status unchanged here; subsequent processors will persist HN_Item and update job status.
            logger.info("Added importTimestamp to ImportJob payload for request entity.");

        } catch (Exception ex) {
            logger.error("Failed to add importTimestamp to payload: {}", ex.getMessage(), ex);
            entity.setStatus("FAILED");
            entity.setErrorMessage("Failed to enrich payload: " + ex.getMessage());
        }

        return entity;
    }
}