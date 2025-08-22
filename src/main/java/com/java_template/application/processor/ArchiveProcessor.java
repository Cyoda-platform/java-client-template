package com.java_template.application.processor;

import com.java_template.application.entity.hn_item.version_1.HN_Item;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class ArchiveProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper mapper = new ObjectMapper();

    public ArchiveProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HN_Item for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(HN_Item.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HN_Item entity) {
        return entity != null && entity.isValid();
    }

    private HN_Item processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HN_Item> context) {
        HN_Item entity = context.entity();

        try {
            String raw = entity.getRawJson();
            if (raw == null || raw.isBlank()) {
                // nothing to enrich; return entity unchanged
                logger.warn("HN_Item rawJson is empty for id={}", entity.getId());
                return entity;
            }

            // Attempt to parse the raw JSON and add an "archived": true flag.
            // This preserves the original payload structure while marking it archived.
            ObjectNode node;
            try {
                node = (ObjectNode) mapper.readTree(raw);
            } catch (Exception ex) {
                // If rawJson is not a JSON object, wrap it into an object containing original payload and archived flag.
                node = mapper.createObjectNode();
                node.put("rawPayload", raw);
            }

            node.put("archived", true);
            String updated = mapper.writeValueAsString(node);
            entity.setRawJson(updated);

            logger.info("Archived HN_Item id={} by adding archived flag to rawJson", entity.getId());
        } catch (Exception e) {
            // Log and return entity unchanged; do not fail the processor here.
            logger.error("Failed to mark HN_Item id={} as archived: {}", entity.getId(), e.getMessage(), e);
        }

        return entity;
    }
}