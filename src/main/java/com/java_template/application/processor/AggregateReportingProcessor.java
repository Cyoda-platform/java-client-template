package com.java_template.application.processor;

import com.java_template.application.entity.weeklysend.version_1.WeeklySend;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;

@Component
public class AggregateReportingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AggregateReportingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AggregateReportingProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AggregateReporting for request: {}", request.getId());

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
            // In V1 there are no delivery records; we assume external system populated counts.
            // If counts are missing, leave them as-is or set to zero.
            if (send.getOpens_count() == null) send.setOpens_count(0);
            if (send.getClicks_count() == null) send.setClicks_count(0);
            if (send.getUnsubscribes_count() == null) send.setUnsubscribes_count(0);
            if (send.getBounces_count() == null) send.setBounces_count(0);

            // finalize status
            send.setStatus("sent");
            if (send.getActual_send_date() == null || send.getActual_send_date().isBlank()) {
                send.setActual_send_date(OffsetDateTime.now().toString());
            }
            logger.info("WeeklySend {} aggregated reporting and finalized", send.getId());
        } catch (Exception ex) {
            logger.error("Error aggregating report for {}: {}", send.getId(), ex.getMessage(), ex);
            send.setStatus("failed");
            send.setError_details(ex.getMessage());
        }
        return send;
    }
}
