package com.java_template.application.processor;

import com.java_template.application.entity.weeklysend.version_1.WeeklySend;
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

@Component
public class ScheduleSendProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleSendProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ScheduleSendProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ScheduleSend for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(WeeklySend.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(WeeklySend entity) {
        return entity != null && entity.isValid();
    }

    private WeeklySend processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklySend> context) {
        WeeklySend send = context.entity();
        try {
            // If scheduled_date is not provided, schedule immediately
            if (send.getScheduled_date() == null || send.getScheduled_date().isBlank()) {
                send.setScheduled_date(OffsetDateTime.now().toString());
            }
            send.setStatus("scheduled");
            logger.info("WeeklySend {} scheduled for {}", send.getId(), send.getScheduled_date());
        } catch (Exception ex) {
            logger.error("Error scheduling WeeklySend {}: {}", send == null ? "<null>" : send.getId(), ex.getMessage(), ex);
            if (send != null) {
                send.setStatus("failed");
                send.setError_details(ex.getMessage());
            }
        }
        return send;
    }
}
