package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.application.entity.changeevent.version_1.ChangeEvent;
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

import java.time.Instant;
import java.util.UUID;

@Component
public class EmitChangeEventProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmitChangeEventProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EmitChangeEventProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmitChangeEvent for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid laureate for change event")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate laureate) {
        return laureate != null && laureate.getBusinessId() != null && !laureate.getBusinessId().isBlank();
    }

    private ChangeEvent processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate laureate = context.entity();
        ChangeEvent evt = new ChangeEvent();
        try {
            evt.setEventId("evt-" + UUID.randomUUID().toString());
            evt.setLaureateTechnicalId(laureate.getTechnicalId());
            evt.setLaureateBusinessId(laureate.getBusinessId());
            evt.setChangeType(laureate.getChangeType());
            evt.setPayload(java.util.Map.of("laureate", laureate));
            evt.setCreatedAt(Instant.now().toString());
            evt.setStatus(ChangeEvent.Status.CREATED);
            logger.info("Emitted ChangeEvent {} for laureate {}", evt.getEventId(), laureate.getBusinessId());
            // In real implementation persist event and enqueue for delivery
        } catch (Exception e) {
            logger.error("Error emitting change event for laureate {}: {}", laureate.getBusinessId(), e.getMessage());
        }
        return evt;
    }
}
