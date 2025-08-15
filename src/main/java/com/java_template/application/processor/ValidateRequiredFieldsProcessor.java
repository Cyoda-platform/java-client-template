package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.application.entity.validationrecord.version_1.ValidationRecord;
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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ValidateRequiredFieldsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateRequiredFieldsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper mapper = new ObjectMapper();

    public ValidateRequiredFieldsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HNItem validation for request: {}", request.getId());

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
        return entity != null && entity.getRawJson() != null && !entity.getRawJson().isBlank();
    }

    private HNItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HNItem> context) {
        HNItem entity = context.entity();
        try {
            // mark as validating
            entity.setStatus("VALIDATING");
            entity.setErrorMessage(null);
            entity.setImportTimestamp(entity.getImportTimestamp()); // noop to avoid unused warning
            entity.setErrorMessage(null);
            String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            String raw = entity.getRawJson();
            JsonNode parsed = mapper.readTree(raw);
            List<String> missing = new ArrayList<>();

            // check id (Hacker News id)
            if (!parsed.hasNonNull("id")) {
                missing.add("id");
            } else {
                try {
                    long hnId = parsed.get("id").asLong();
                    // entity has field 'id' representing hnId
                    entity.setId(hnId);
                } catch (Exception ex) {
                    missing.add("id");
                }
            }

            // check type
            if (!parsed.hasNonNull("type") || parsed.get("type").asText().isBlank()) {
                missing.add("type");
            } else {
                entity.setType(parsed.get("type").asText());
            }

            // Create a validation record summarizing the result
            ValidationRecord record = new ValidationRecord();
            record.setTechnicalId(UUID.randomUUID().toString());
            record.setHnItemId(entity.getId());
            record.setCheckedAt(now);
            if (!missing.isEmpty()) {
                String message = "Missing required fields: " + String.join(", ", missing);
                record.setIsValid(Boolean.FALSE);
                record.setMissingFields(missing);
                record.setMessage(message);

                // Persist validation record to in-memory repo for demo/testing
                ValidationRecordRepository.getInstance().save(record);

                entity.setStatus("INVALID");
                entity.setErrorMessage(message);
                logger.info("Validation failed for HNItem {}: {}", entity.getId(), message);
            } else {
                record.setIsValid(Boolean.TRUE);
                record.setMissingFields(new ArrayList<>());
                record.setMessage("Validation passed");

                ValidationRecordRepository.getInstance().save(record);

                entity.setStatus("VALIDATED");
                entity.setErrorMessage(null);
                logger.info("Validation passed for HNItem {}", entity.getId());
            }

            return entity;
        } catch (Exception e) {
            logger.error("Error validating HNItem {}: {}", entity == null ? "<null>" : String.valueOf(entity.getId()), e.getMessage(), e);
            if (entity != null) {
                entity.setStatus("INVALID");
                entity.setErrorMessage("Validation processing error: " + e.getMessage());
            }
            return entity;
        }
    }
}
