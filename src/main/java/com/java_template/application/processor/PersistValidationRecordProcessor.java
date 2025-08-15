package com.java_template.application.processor;

import com.java_template.application.entity.validationrecord.version_1.ValidationRecord;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
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
import java.util.concurrent.CompletableFuture;

@Component
public class PersistValidationRecordProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistValidationRecordProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PersistValidationRecordProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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

            // persist via entityService
            try {
                CompletableFuture<java.util.UUID> fut = entityService.addItem(
                    ValidationRecord.ENTITY_NAME,
                    String.valueOf(ValidationRecord.ENTITY_VERSION),
                    record
                );
                java.util.UUID id = fut.join();
                if (id != null) record.setTechnicalId(id.toString());
            } catch (Exception e) {
                logger.warn("Failed to persist ValidationRecord via EntityService: {}", e.getMessage());
            }

            // persist to in-memory repo for demo purposes
            ValidationRecordRepository.getInstance().save(record);
            logger.info("Persisted ValidationRecord {} for hnItemId {}", record.getTechnicalId(), record.getHnItemId());
            return record;
        } catch (Exception e) {
            logger.error("Error persisting ValidationRecord {}: {}", record == null ? "<null>" : record.getTechnicalId(), e.getMessage(), e);
            return record;
        }
    }
}
