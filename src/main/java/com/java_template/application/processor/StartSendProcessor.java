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
public class StartSendProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartSendProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StartSendProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing StartSend for request: {}", request.getId());

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
            send.setStatus("sending");
            send.setActual_send_date(OffsetDateTime.now().toString());
            // In V1 we simulate dispatch by setting status; actual per-recipient sends are out of scope
            logger.info("WeeklySend {} marked as sending", send.getId());
        } catch (Exception ex) {
            logger.error("Error starting send {}: {}", send.getId(), ex.getMessage(), ex);
            send.setStatus("failed");
            send.setError_details(ex.getMessage());
        }
        return send;
    }
}
