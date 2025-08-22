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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

        // Ensure basic normalization/enrichment of HNItem fields without persisting other entities.
        if (entity == null) return null;

        // Normalize type to lowercase for consistency
        if (entity.getType() != null) {
            String type = entity.getType().trim();
            if (!type.isEmpty()) {
                entity.setType(type.toLowerCase());
            } else {
                entity.setType(null);
            }
        }

        // Trim textual fields
        if (entity.getTitle() != null) {
            String t = entity.getTitle().trim();
            entity.setTitle(t.isEmpty() ? null : t);
        }
        if (entity.getText() != null) {
            String tx = entity.getText().trim();
            entity.setText(tx.isEmpty() ? null : tx);
        }
        if (entity.getBy() != null) {
            String by = entity.getBy().trim();
            entity.setBy(by.isEmpty() ? null : by);
        }

        // Normalize URL: ensure scheme present (default to https if missing)
        if (entity.getUrl() != null) {
            String url = entity.getUrl().trim();
            if (!url.isEmpty()) {
                String lower = url.toLowerCase();
                if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
                    url = "https://" + url;
                }
                entity.setUrl(url);
            } else {
                entity.setUrl(null);
            }
        }

        // Ensure rawJson exists. If missing, synthesize a minimal JSON representation from available fields.
        if (entity.getRawJson() == null || entity.getRawJson().isBlank()) {
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            boolean first = true;

            if (entity.getId() != null) {
                sb.append("\"id\":").append(entity.getId());
                first = false;
            }
            if (entity.getBy() != null) {
                if (!first) sb.append(',');
                sb.append("\"by\":").append(quote(entity.getBy()));
                first = false;
            }
            if (entity.getTitle() != null) {
                if (!first) sb.append(',');
                sb.append("\"title\":").append(quote(entity.getTitle()));
                first = false;
            }
            if (entity.getTime() != null) {
                if (!first) sb.append(',');
                sb.append("\"time\":").append(entity.getTime());
                first = false;
            }
            if (entity.getType() != null) {
                if (!first) sb.append(',');
                sb.append("\"type\":").append(quote(entity.getType()));
                first = false;
            }
            if (entity.getUrl() != null) {
                if (!first) sb.append(',');
                sb.append("\"url\":").append(quote(entity.getUrl()));
                first = false;
            }
            if (entity.getText() != null) {
                if (!first) sb.append(',');
                sb.append("\"text\":").append(quote(entity.getText()));
                first = false;
            }
            if (entity.getScore() != null) {
                if (!first) sb.append(',');
                sb.append("\"score\":").append(entity.getScore());
                first = false;
            }
            if (entity.getDescendants() != null) {
                if (!first) sb.append(',');
                sb.append("\"descendants\":").append(entity.getDescendants());
                first = false;
            }
            List<Long> kids = entity.getKids();
            if (kids != null && !kids.isEmpty()) {
                if (!first) sb.append(',');
                sb.append("\"kids\":[");
                sb.append(kids.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(",")));
                sb.append(']');
            }
            sb.append('}');
            entity.setRawJson(sb.toString());
        }

        return entity;
    }

    private String quote(String s) {
        if (s == null) return "null";
        String escaped = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        return "\"" + escaped + "\"";
    }
}