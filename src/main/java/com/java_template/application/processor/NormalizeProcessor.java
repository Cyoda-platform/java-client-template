package com.java_template.application.processor;
import com.java_template.application.entity.hnitem.version_1.HNItem;
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

import java.util.ArrayList;
import java.util.List;

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

        // Trim textual fields
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
        if (entity.getUrl() != null) {
            entity.setUrl(entity.getUrl().trim());
        }

        // Normalize URL: ensure scheme present (default to https:// if missing)
        String url = entity.getUrl();
        if (url != null && !url.isBlank()) {
            String lower = url.toLowerCase();
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                entity.setUrl("https://" + url);
            }
        }

        // Ensure kids list is non-null
        if (entity.getKids() == null) {
            entity.setKids(new ArrayList<>());
        }

        // Ensure rawJson exists; if not, build a minimal raw JSON representation from available fields
        if (entity.getRawJson() == null || entity.getRawJson().isBlank()) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");

            // helper to append comma if needed
            boolean first = true;
            if (entity.getId() != null) {
                sb.append("\"id\":").append(entity.getId());
                first = false;
            }
            if (entity.getBy() != null) {
                if (!first) sb.append(",");
                sb.append("\"by\":\"").append(escapeJson(entity.getBy())).append("\"");
                first = false;
            }
            if (entity.getTitle() != null) {
                if (!first) sb.append(",");
                sb.append("\"title\":\"").append(escapeJson(entity.getTitle())).append("\"");
                first = false;
            }
            if (entity.getTime() != null) {
                if (!first) sb.append(",");
                sb.append("\"time\":").append(entity.getTime());
                first = false;
            }
            if (entity.getType() != null) {
                if (!first) sb.append(",");
                sb.append("\"type\":\"").append(escapeJson(entity.getType())).append("\"");
                first = false;
            }
            if (entity.getUrl() != null) {
                if (!first) sb.append(",");
                sb.append("\"url\":\"").append(escapeJson(entity.getUrl())).append("\"");
                first = false;
            }
            if (entity.getText() != null) {
                if (!first) sb.append(",");
                sb.append("\"text\":\"").append(escapeJson(entity.getText())).append("\"");
                first = false;
            }
            if (entity.getScore() != null) {
                if (!first) sb.append(",");
                sb.append("\"score\":").append(entity.getScore());
                first = false;
            }
            if (entity.getDescendants() != null) {
                if (!first) sb.append(",");
                sb.append("\"descendants\":").append(entity.getDescendants());
                first = false;
            }
            // kids array
            List<Long> kids = entity.getKids();
            if (kids != null) {
                if (!first) sb.append(",");
                sb.append("\"kids\":[");
                for (int i = 0; i < kids.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(kids.get(i));
                }
                sb.append("]");
            }

            sb.append("}");
            entity.setRawJson(sb.toString());
        }

        // Additional small normalizations/enrichments (kept minimal and safe)
        // Ensure type is lowercase for consistency
        if (entity.getType() != null) {
            entity.setType(entity.getType().toLowerCase());
        }

        return entity;
    }

    private String escapeJson(String s) {
        if (s == null) return null;
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}