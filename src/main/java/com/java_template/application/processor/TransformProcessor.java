package com.java_template.application.processor;
import com.java_template.application.entity.ingestjob.version_1.IngestJob;
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

import java.util.*;

@Component
public class TransformProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TransformProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public TransformProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing IngestJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(IngestJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestJob entity) {
        return entity != null && entity.isValid();
    }

    private IngestJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestJob> context) {
        IngestJob entity = context.entity();

        try {
            Map<String, Object> original = entity.getHnPayload();
            if (original == null) {
                logger.warn("IngestJob {} has null hnPayload, nothing to transform", entity.getTechnicalId());
                return entity;
            }

            // Build normalized HNItem from the incoming payload (best-effort conversion)
            HNItem hnItem = new HNItem();

            // id
            Object idObj = original.get("id");
            if (idObj instanceof Number) {
                hnItem.setId(((Number) idObj).longValue());
            } else if (idObj instanceof String) {
                try {
                    hnItem.setId(Long.parseLong((String) idObj));
                } catch (NumberFormatException ignored) { /* leave null */ }
            }

            // by
            Object byObj = original.get("by");
            if (byObj != null) hnItem.setBy(String.valueOf(byObj));

            // title
            Object titleObj = original.get("title");
            if (titleObj != null) hnItem.setTitle(String.valueOf(titleObj));

            // time
            Object timeObj = original.get("time");
            if (timeObj instanceof Number) {
                hnItem.setTime(((Number) timeObj).longValue());
            } else if (timeObj instanceof String) {
                try {
                    hnItem.setTime(Long.parseLong((String) timeObj));
                } catch (NumberFormatException ignored) { /* leave null */ }
            }

            // type
            Object typeObj = original.get("type");
            if (typeObj != null) hnItem.setType(String.valueOf(typeObj));

            // url
            Object urlObj = original.get("url");
            if (urlObj != null) hnItem.setUrl(String.valueOf(urlObj));

            // text
            Object textObj = original.get("text");
            if (textObj != null) hnItem.setText(String.valueOf(textObj));

            // score
            Object scoreObj = original.get("score");
            if (scoreObj instanceof Number) {
                hnItem.setScore(((Number) scoreObj).intValue());
            } else if (scoreObj instanceof String) {
                try {
                    hnItem.setScore(Integer.parseInt((String) scoreObj));
                } catch (NumberFormatException ignored) { /* leave null */ }
            }

            // descendants
            Object descObj = original.get("descendants");
            if (descObj instanceof Number) {
                hnItem.setDescendants(((Number) descObj).intValue());
            } else if (descObj instanceof String) {
                try {
                    hnItem.setDescendants(Integer.parseInt((String) descObj));
                } catch (NumberFormatException ignored) { /* leave null */ }
            }

            // kids - normalize to List<Long>
            Object kidsObj = original.get("kids");
            if (kidsObj instanceof List<?>) {
                List<?> rawKids = (List<?>) kidsObj;
                List<Long> kids = new ArrayList<>();
                for (Object k : rawKids) {
                    if (k instanceof Number) {
                        kids.add(((Number) k).longValue());
                    } else if (k instanceof String) {
                        try {
                            kids.add(Long.parseLong((String) k));
                        } catch (NumberFormatException ignored) { /* skip */ }
                    }
                }
                if (!kids.isEmpty()) hnItem.setKids(kids);
            }

            // raw_json - keep a string representation of the original payload for fidelity
            // Use Map.toString() as a fallback textual serialization
            hnItem.setRawJson(String.valueOf(original));

            // Convert HNItem back into a normalized Map<String,Object> to store in the IngestJob.hnPayload
            Map<String, Object> normalized = new HashMap<>();
            if (hnItem.getId() != null) normalized.put("id", hnItem.getId());
            if (hnItem.getBy() != null) normalized.put("by", hnItem.getBy());
            if (hnItem.getTitle() != null) normalized.put("title", hnItem.getTitle());
            if (hnItem.getTime() != null) normalized.put("time", hnItem.getTime());
            if (hnItem.getType() != null) normalized.put("type", hnItem.getType());
            if (hnItem.getUrl() != null) normalized.put("url", hnItem.getUrl());
            if (hnItem.getText() != null) normalized.put("text", hnItem.getText());
            if (hnItem.getScore() != null) normalized.put("score", hnItem.getScore());
            if (hnItem.getDescendants() != null) normalized.put("descendants", hnItem.getDescendants());
            if (hnItem.getKids() != null) normalized.put("kids", hnItem.getKids());
            normalized.put("raw_json", hnItem.getRawJson());

            // Replace the job payload with the normalized representation
            entity.setHnPayload(normalized);

            // If the job was in VALIDATING state, progress it to PROCESSING; otherwise keep existing status
            String currentStatus = entity.getStatus();
            if (currentStatus != null && currentStatus.equalsIgnoreCase("VALIDATING")) {
                entity.setStatus("PROCESSING");
            }

            logger.info("Transformed IngestJob {} payload into normalized HNItem shape", entity.getTechnicalId());
        } catch (Exception e) {
            logger.error("Error while transforming IngestJob {}: {}", entity.getTechnicalId(), e.getMessage(), e);
            entity.setStatus("FAILED");
            entity.setErrorMessage("Transform error: " + e.getMessage());
        }

        return entity;
    }
}