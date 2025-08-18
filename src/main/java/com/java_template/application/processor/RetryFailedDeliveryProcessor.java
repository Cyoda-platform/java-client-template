package com.java_template.application.processor;

import com.java_template.application.entity.deliveryRecord.version_1.DeliveryRecord;
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
import java.time.ZoneOffset;

@Component
public class RetryFailedDeliveryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RetryFailedDeliveryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final int maxAttempts = 3;

    public RetryFailedDeliveryProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DeliveryRecord for retry request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DeliveryRecord.class)
            .validate(this::isValidEntity, "Invalid DeliveryRecord entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(DeliveryRecord entity) {
        return entity != null;
    }

    private DeliveryRecord processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DeliveryRecord> context) {
        DeliveryRecord record = context.entity();
        try {
            if (record.getStatus() == null) return record;
            String status = record.getStatus();
            if ("FAILED".equalsIgnoreCase(status) || "RETRY_SCHEDULED".equalsIgnoreCase(status)) {
                int attempts = record.getAttempts() == null ? 0 : record.getAttempts();
                if (attempts < maxAttempts) {
                    // schedule next retry with exponential backoff
                    long backoffSeconds = (long) Math.pow(2, attempts) * 60; // base 1 minute
                    OffsetDateTime nextAttempt = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(backoffSeconds);
                    record.setStatus("QUEUED");
                    record.setLastAttemptAt(nextAttempt.toString());
                    logger.info("Scheduling retry for record {} nextAttempt={} attempts={}", record.getId(), nextAttempt, attempts);
                } else {
                    record.setStatus("FAILED");
                    logger.info("Max attempts reached for record {} attempts={}", record.getId(), attempts);
                }
            }
        } catch (Exception ex) {
            logger.error("Error in retry processor", ex);
        }
        return record;
    }
}
