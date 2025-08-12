package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
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
public class ProcessHackerNewsItem implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessHackerNewsItem.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    public ProcessHackerNewsItem(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HackerNewsItem for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(HackerNewsItem.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HackerNewsItem entity) {
        return entity != null;
    }

    private HackerNewsItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HackerNewsItem> context) {
        HackerNewsItem entity = context.entity();

        // Business logic implementation based on functional requirements
        // 1. Initial state is START (already set externally before event)
        // 2. Validate mandatory fields done via criteria in workflow
        // 3. Apply filtering based on criteria outcome: if missing mandatory fields, set state INVALID and record reason
        //    else set state VALID

        // Check for mandatory fields: id and type
        boolean idMissing = entity.getId() == null;
        boolean typeMissing = entity.getType() == null || entity.getType().isEmpty();

        if (idMissing && typeMissing) {
            entity.setState("INVALID");
            entity.setInvalidReason("Missing mandatory fields: id, type");
            logger.warn("Entity marked INVALID due to missing fields: id and type");
        } else if (idMissing) {
            entity.setState("INVALID");
            entity.setInvalidReason("Missing mandatory field: id");
            logger.warn("Entity marked INVALID due to missing field: id");
        } else if (typeMissing) {
            entity.setState("INVALID");
            entity.setInvalidReason("Missing mandatory field: type");
            logger.warn("Entity marked INVALID due to missing field: type");
        } else {
            entity.setState("VALID");
            entity.setInvalidReason(null);
            logger.info("Entity marked VALID as all mandatory fields present");
        }

        // Set creation timestamp if not already set
        if (entity.getCreationTimestamp() == null) {
            entity.setCreationTimestamp(Instant.now());
            logger.info("Set creationTimestamp to {}", entity.getCreationTimestamp());
        }

        // The entity state and invalidReason will be persisted automatically by the platform
        return entity;
    }
}
