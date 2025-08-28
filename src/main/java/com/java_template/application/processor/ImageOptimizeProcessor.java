package com.java_template.application.processor;

import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.workflow.CyodaEntity;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Component
public class ImageOptimizeProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ImageOptimizeProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ImageOptimizeProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(MapEntity.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(MapEntity entity) {
        return entity != null;
    }

    @SuppressWarnings("unchecked")
    private MapEntity processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<MapEntity> context) {
        MapEntity entity = context.entity();

        try {
            // Optimize photos: replace each photo URL with an "optimized" variant.
            Object photosObj = entity.get("photos");
            if (photosObj instanceof List) {
                List<?> photosList = (List<?>) photosObj;
                if (!photosList.isEmpty()) {
                    List<String> optimized = new ArrayList<>(photosList.size());
                    for (Object o : photosList) {
                        if (!(o instanceof String)) continue;
                        String url = (String) o;
                        if (url == null || url.isBlank()) {
                            // skip invalid entries
                            continue;
                        }
                        String opt = optimizeImageUrl(url);
                        optimized.add(opt);
                        logger.debug("Optimized image url from [{}] to [{}] for pet id {}", url, opt, entity.get("id"));
                    }
                    // Replace photos list with optimized urls
                    entity.put("photos", optimized);
                }
            }

            // Mark pet as images-ready
            entity.put("status", "IMAGES_READY");

            // Ensure tag indicating optimization is present
            Object tagsObj = entity.get("tags");
            List<String> tags;
            if (tagsObj instanceof List) {
                tags = new ArrayList<>();
                for (Object t : (List<?>) tagsObj) {
                    if (t instanceof String) tags.add((String) t);
                }
            } else {
                tags = new ArrayList<>();
            }

            boolean hasTag = false;
            for (String t : tags) {
                if ("images_optimized".equalsIgnoreCase(t)) {
                    hasTag = true;
                    break;
                }
            }
            if (!hasTag) {
                tags.add("images_optimized");
                entity.put("tags", tags);
            }

            logger.info("Image optimization completed for pet id {}", entity.get("id"));
        } catch (Exception ex) {
            logger.error("Error during image optimization for pet id {}: {}", entity != null ? entity.get("id") : "unknown", ex.getMessage(), ex);
            // If error occurs, add a tag to indicate optimization failed (do not throw)
            if (entity != null) {
                Object tagsObj = entity.get("tags");
                List<String> tags;
                if (tagsObj instanceof List) {
                    tags = new ArrayList<>();
                    for (Object t : (List<?>) tagsObj) {
                        if (t instanceof String) tags.add((String) t);
                    }
                } else {
                    tags = new ArrayList<>();
                }
                tags.add("images_optimization_failed");
                entity.put("tags", tags);
            }
        }

        return entity;
    }

    private String optimizeImageUrl(String url) {
        // Simple deterministic optimization placeholder:
        // Append query parameter indicating optimization if not present.
        if (url.contains("optimized=true")) {
            return url;
        }
        if (url.contains("?")) {
            return url + "&optimized=true&thumbnail=true";
        } else {
            return url + "?optimized=true&thumbnail=true";
        }
    }

    private static class MapEntity extends HashMap<String, Object> implements CyodaEntity {
        private static final long serialVersionUID = 1L;

        @Override
        public String getModelKey() {
            Object v = this.get("modelKey");
            return v != null ? v.toString() : null;
        }
    }
}