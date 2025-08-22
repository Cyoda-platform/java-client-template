package com.java_template.application.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.ingestjob.version_1.IngestJob;
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
import java.util.Map;

@Component
public class ValidateJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    public ValidateJobProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
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
            // Basic presence checks already covered by isValidEntity.
            // Validate hn_payload structure and required HN fields by mapping to HNItem.
            Map<String, Object> payload = entity.getHnPayload();

            if (payload == null) {
                entity.setStatus("FAILED");
                entity.setErrorMessage("invalid json");
                logger.warn("IngestJob {} failed validation: hn_payload is null", entity.getTechnicalId());
                return entity;
            }

            HNItem hnItem = new HNItem();

            // id
            Object idObj = payload.get("id");
            Long idVal = null;
            if (idObj instanceof Number) {
                idVal = ((Number) idObj).longValue();
            } else if (idObj instanceof String) {
                try {
                    idVal = Long.parseLong((String) idObj);
                } catch (NumberFormatException ignored) { }
            }
            hnItem.setId(idVal);

            // by
            Object byObj = payload.get("by");
            if (byObj != null) {
                hnItem.setBy(String.valueOf(byObj));
            }

            // time
            Object timeObj = payload.get("time");
            Long timeVal = null;
            if (timeObj instanceof Number) {
                timeVal = ((Number) timeObj).longValue();
            } else if (timeObj instanceof String) {
                try {
                    timeVal = Long.parseLong((String) timeObj);
                } catch (NumberFormatException ignored) { }
            }
            hnItem.setTime(timeVal);

            // title
            Object titleObj = payload.get("title");
            if (titleObj != null) {
                hnItem.setTitle(String.valueOf(titleObj));
            }

            // optional fields
            Object typeObj = payload.get("type");
            if (typeObj != null) hnItem.setType(String.valueOf(typeObj));

            Object urlObj = payload.get("url");
            if (urlObj != null) hnItem.setUrl(String.valueOf(urlObj));

            Object textObj = payload.get("text");
            if (textObj != null) hnItem.setText(String.valueOf(textObj));

            Object scoreObj = payload.get("score");
            if (scoreObj instanceof Number) {
                hnItem.setScore(((Number) scoreObj).intValue());
            } else if (scoreObj instanceof String) {
                try {
                    hnItem.setScore(Integer.parseInt((String) scoreObj));
                } catch (NumberFormatException ignored) { }
            }

            Object descendantsObj = payload.get("descendants");
            if (descendantsObj instanceof Number) {
                hnItem.setDescendants(((Number) descendantsObj).intValue());
            } else if (descendantsObj instanceof String) {
                try {
                    hnItem.setDescendants(Integer.parseInt((String) descendantsObj));
                } catch (NumberFormatException ignored) { }
            }

            // kids - attempt to convert to List<Long>
            Object kidsObj = payload.get("kids");
            if (kidsObj instanceof List) {
                List<?> rawKids = (List<?>) kidsObj;
                List<Long> kids = new ArrayList<>();
                for (Object o : rawKids) {
                    if (o instanceof Number) {
                        kids.add(((Number) o).longValue());
                    } else if (o instanceof String) {
                        try {
                            kids.add(Long.parseLong((String) o));
                        } catch (NumberFormatException ignored) { }
                    }
                }
                if (!kids.isEmpty()) {
                    hnItem.setKids(kids);
                }
            }

            // rawJson - serialize payload for fidelity
            try {
                String raw = objectMapper.writeValueAsString(payload);
                hnItem.setRawJson(raw);
            } catch (JsonProcessingException e) {
                hnItem.setRawJson(null);
            }

            // Use HNItem.isValid() to perform canonical validation of required HN fields.
            if (!hnItem.isValid()) {
                entity.setStatus("FAILED");
                entity.setErrorMessage("missing required fields");
                logger.warn("IngestJob {} failed HNItem validation: {}", entity.getTechnicalId(), entity.getErrorMessage());
                return entity;
            }

            // Validation passed -> move job to PROCESSING
            entity.setStatus("PROCESSING");
            entity.setErrorMessage(null);
            logger.info("IngestJob {} validation passed, moving to PROCESSING", entity.getTechnicalId());
            return entity;

        } catch (Exception ex) {
            logger.error("Unexpected error while validating IngestJob {}: {}", entity.getTechnicalId(), ex.getMessage(), ex);
            entity.setStatus("FAILED");
            entity.setErrorMessage("validation error: " + ex.getMessage());
            return entity;
        }
    }
}