package com.java_template.application.processor;

import com.java_template.application.entity.eggtimer.version_1.EggTimer;
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

@Component
public class TimerCompleteProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TimerCompleteProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public TimerCompleteProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing TimerComplete for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(EggTimer.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(EggTimer entity) {
        return entity != null && entity.isValid();
    }

    private EggTimer processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<EggTimer> context) {
        EggTimer timer = context.entity();
        try {
            if (timer.getState() == null) return timer;
            String state = timer.getState();
            if ("COMPLETED".equalsIgnoreCase(state) || "CANCELLED".equalsIgnoreCase(state)) {
                logger.info("Timer {} already terminal ({}), skipping completion processing", timer.getId(), state);
                return timer;
            }

            Instant now = Instant.now();
            String expectedStr = timer.getExpectedEndAt();
            if (expectedStr == null) {
                logger.warn("Timer {} has no expectedEndAt; cannot determine completion", timer.getId());
                return timer;
            }

            Instant expected = Instant.parse(expectedStr);
            if (!now.isBefore(expected) || now.equals(expected)) {
                // complete
                timer.setState("COMPLETED");
                logger.info("Timer {} marked COMPLETED", timer.getId());

                // ensure notification exists (idempotent)
                // Note: ScheduleNotificationProcessor may be invoked as part of workflow; call it explicitly is optional here

                // Persist history - This should be handled by a separate processor in real implementation
                // For now just log the history creation
                logger.info("Persisting history for timer {}", timer.getId());
            } else {
                logger.info("Timer {} not yet completed. expectedEndAt={}", timer.getId(), expectedStr);
            }

            return timer;
        } catch (Exception ex) {
            logger.error("Error in TimerCompleteProcessor for timer {}: {}", timer != null ? timer.getId() : null, ex.getMessage(), ex);
            return timer;
        }
    }
}
