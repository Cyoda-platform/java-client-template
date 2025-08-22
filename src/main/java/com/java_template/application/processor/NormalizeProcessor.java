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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class NormalizeProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NormalizeProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NormalizeProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HNItem for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(HNItem.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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

        // Trim basic string fields to normalize whitespace
        if (entity.getBy() != null) {
            entity.setBy(entity.getBy().trim());
        }
        if (entity.getTitle() != null) {
            entity.setTitle(entity.getTitle().trim());
        }
        if (entity.getText() != null) {
            entity.setText(entity.getText().trim());
        }
        if (entity.getType() != null) {
            entity.setType(entity.getType().trim());
        }

        // Normalize URL: ensure scheme present if user provided a URL without scheme
        String url = entity.getUrl();
        if (url != null) {
            url = url.trim();
            if (!url.isBlank() && !url.startsWith("http://") && !url.startsWith("https://")) {
                // Prefer https by default
                url = "https://" + url;
            }
            entity.setUrl(url.isBlank() ? null : url);
        }

        // Ensure kids is an empty list instead of null for downstream processing
        if (entity.getKids() == null) {
            entity.setKids(new ArrayList<>());
        } else {
            // remove any nulls from kids list
            List<Long> cleaned = new ArrayList<>();
            for (Long k : entity.getKids()) {
                if (k != null) cleaned.add(k);
            }
            entity.setKids(cleaned);
        }

        // If rawJson is missing, synthesize a minimal raw JSON representation for fidelity
        if (entity.getRawJson() == null || entity.getRawJson().isBlank()) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            if (entity.getId() != null) {
                sb.append("\"id\":").append(entity.getId());
                first = false;
            }
            if (entity.getBy() != null && !entity.getBy().isBlank()) {
                if (!first) sb.append(",");
                sb.append("\"by\":\"").append(escapeJson(entity.getBy())).append("\"");
                first = false;
            }
            if (entity.getTitle() != null && !entity.getTitle().isBlank()) {
                if (!first) sb.append(",");
                sb.append("\"title\":\"").append(escapeJson(entity.getTitle())).append("\"");
                first = false;
            }
            if (entity.getTime() != null) {
                if (!first) sb.append(",");
                sb.append("\"time\":").append(entity.getTime());
                first = false;
            }
            if (entity.getType() != null && !entity.getType().isBlank()) {
                if (!first) sb.append(",");
                sb.append("\"type\":\"").append(escapeJson(entity.getType())).append("\"");
                first = false;
            }
            if (entity.getUrl() != null && !entity.getUrl().isBlank()) {
                if (!first) sb.append(",");
                sb.append("\"url\":\"").append(escapeJson(entity.getUrl())).append("\"");
            }
            sb.append("}");
            entity.setRawJson(sb.toString());
        } else {
            // Trim stored rawJson
            entity.setRawJson(entity.getRawJson().trim());
        }

        logger.debug("Normalized HNItem id={} by={} title={} url={}", entity.getId(), entity.getBy(), entity.getTitle(), entity.getUrl());
        return entity;
    }

    // Basic JSON string escaper for synthesized rawJson (handles quotes and backslashes)
    private String escapeJson(String input) {
        if (input == null) return null;
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\"':
                    sb.append("\\\"");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}