package com.java_template.application.processor;

import com.java_template.application.entity.hnitem.version_1.HNItem;
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
public class EnrichProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HNItem for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(HNItem.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HNItem entity) {
        return entity != null && entity.isValid();
    }

    private HNItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HNItem> context) {
        HNItem entity = context.entity();

        if (entity == null) return null;

        // Trim textual fields if present
        if (entity.getTitle() != null) {
            entity.setTitle(entity.getTitle().trim());
        }
        if (entity.getBy() != null) {
            entity.setBy(entity.getBy().trim());
        }
        if (entity.getText() != null) {
            entity.setText(entity.getText().trim());
        }

        // Normalize type to lowercase
        if (entity.getType() != null) {
            entity.setType(entity.getType().trim().toLowerCase());
        }

        // Normalize URL: ensure scheme is present (default https:// if missing)
        if (entity.getUrl() != null) {
            String url = entity.getUrl().trim();
            if (!url.isEmpty() && !(url.startsWith("http://") || url.startsWith("https://"))) {
                url = "https://" + url;
            }
            entity.setUrl(url);
        }

        // Ensure rawJson field contains a serialized representation for fidelity if absent
        if (entity.getRawJson() == null || entity.getRawJson().isBlank()) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            // id
            sb.append("\"id\":").append(entity.getId() == null ? "null" : entity.getId());
            // by
            sb.append(",\"by\":").append(stringOrNull(entity.getBy()));
            // title
            sb.append(",\"title\":").append(stringOrNull(entity.getTitle()));
            // time
            sb.append(",\"time\":").append(entity.getTime() == null ? "null" : entity.getTime());
            // type
            sb.append(",\"type\":").append(stringOrNull(entity.getType()));
            // url
            sb.append(",\"url\":").append(stringOrNull(entity.getUrl()));
            // text
            sb.append(",\"text\":").append(stringOrNull(entity.getText()));
            // kids
            sb.append(",\"kids\":");
            if (entity.getKids() == null) {
                sb.append("null");
            } else {
                sb.append("[");
                for (int i = 0; i < entity.getKids().size(); i++) {
                    Long kid = entity.getKids().get(i);
                    sb.append(kid == null ? "null" : kid);
                    if (i < entity.getKids().size() - 1) sb.append(",");
                }
                sb.append("]");
            }
            // score
            sb.append(",\"score\":").append(entity.getScore() == null ? "null" : entity.getScore());
            // descendants
            sb.append(",\"descendants\":").append(entity.getDescendants() == null ? "null" : entity.getDescendants());
            sb.append("}");
            entity.setRawJson(sb.toString());
        }

        // Additional light-weight enrichment: if score is null set to 0
        if (entity.getScore() == null) {
            entity.setScore(0);
        }

        // No external entity modifications (persist) are performed here; Cyoda runtime will persist changes to this entity automatically.

        logger.debug("Enriched HNItem id={} by={} title='{}'", entity.getId(), entity.getBy(), entity.getTitle());
        return entity;
    }

    private String stringOrNull(String value) {
        if (value == null) return "null";
        // simple escape of backslashes and quotes to keep produced JSON stable for storage
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }
}