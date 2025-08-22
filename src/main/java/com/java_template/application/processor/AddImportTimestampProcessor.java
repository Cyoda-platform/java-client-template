package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.time.Instant;

@Component
public class AddImportTimestampProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AddImportTimestampProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AddImportTimestampProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
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

        try {
            String payload = entity.getPayload();
            if (payload == null || payload.isBlank()) {
                String msg = "Payload is missing or empty";
                logger.error(msg + " for ImportJob");
                entity.setStatus("FAILED");
                entity.setErrorMessage(msg);
                return entity;
            }

            JsonNode root = objectMapper.readTree(payload);
            if (root == null || !root.isObject()) {
                // If payload is not an object, wrap it into an object with rawJson field
                ObjectNode wrapper = objectMapper.createObjectNode();
                wrapper.set("raw", root);
                wrapper.put("importTimestamp", Instant.now().toString());
                entity.setPayload(objectMapper.writeValueAsString(wrapper));
                return entity;
            }

            ObjectNode objectNode = (ObjectNode) root;
            // Add or overwrite importTimestamp with current ISO-8601 timestamp
            objectNode.put("importTimestamp", Instant.now().toString());

            // persist the updated payload back into the ImportJob entity
            entity.setPayload(objectMapper.writeValueAsString(objectNode));

            logger.info("Added importTimestamp to ImportJob payload for request entity.");

        } catch (Exception ex) {
            logger.error("Failed to add importTimestamp to payload", ex);
            entity.setStatus("FAILED");
            String err = ex.getMessage() != null ? ex.getMessage() : "Unknown error while enriching payload";
            entity.setErrorMessage(err);
        }

        return entity;
    }
}