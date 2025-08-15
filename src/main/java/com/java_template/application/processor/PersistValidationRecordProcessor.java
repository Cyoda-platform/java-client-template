package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.validationrecord.version_1.ValidationRecord;
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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class PersistValidationRecordProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistValidationRecordProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper mapper = new ObjectMapper();

    public PersistValidationRecordProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PersistValidationRecord for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ValidationRecord.class)
            .validate(this::isValidEntity, "Invalid validation record")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ValidationRecord entity) {
        return entity != null;
    }

    private ValidationRecord processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ValidationRecord> context) {
        ValidationRecord record = context.entity();
        try {
            String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            if (record.getTechnicalId() == null) {
                record.setTechnicalId(UUID.randomUUID().toString());
            }
            record.setCheckedAt(now);
            record.setCreatedAt(now);
            // persist to an in-memory repository for demo purposes
            ValidationRecordRepository.getInstance().save(record);
            logger.info("Persisted ValidationRecord {} for hnItem {}", record.getTechnicalId(), record.getHnItemTechnicalId());
            return record;
        } catch (Exception e) {
            logger.error("Error persisting ValidationRecord {}: {}", record == null ? "<null>" : record.getTechnicalId(), e.getMessage(), e);
            return record;
        }
    }
}
