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
import java.time.Duration;
import java.util.Locale;

@Component
public class ValidateDurationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateDurationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateDurationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EggTimer for request: {}", request.getId());

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
            // Compute duration if not provided or invalid
            Integer duration = timer.getDurationSeconds();
            int eggs = timer.getEggsCount() != null ? timer.getEggsCount() : 1;
            if (duration == null || duration <= 0) {
                int base = lookupBaseDuration(timer.getBoilType(), timer.getEggSize());
                int adjustment = Math.max(0, eggs - 1) * 15; // per egg adjustment
                timer.setDurationSeconds(base + adjustment);
                logger.info("Computed durationSeconds={} for timerId={}", timer.getDurationSeconds(), timer.getId());
            }

            // Normalize startAt and determine scheduling
            Instant now = Instant.now();
            String startAtStr = timer.getStartAt();
            Instant startAt = null;
            if (startAtStr != null) {
                try {
                    startAt = Instant.parse(startAtStr);
                } catch (Exception ex) {
                    // invalid format - treat as immediate start
                    logger.warn("Invalid startAt format for timer {}: {}. Will start immediately.", timer.getId(), startAtStr);
                    startAt = null;
                }
            }

            if (startAt == null || !startAt.isAfter(now)) {
                // start immediately
                timer.setScheduledStartAt(now.toString());
                timer.setState("RUNNING");
                Instant expected = now.plusSeconds(timer.getDurationSeconds());
                timer.setExpectedEndAt(expected.toString());
            } else {
                // scheduled for future
                timer.setScheduledStartAt(startAt.toString());
                timer.setState("SCHEDULED");
                Instant expected = startAt.plusSeconds(timer.getDurationSeconds());
                timer.setExpectedEndAt(expected.toString());
            }

            return timer;
        } catch (Exception ex) {
            logger.error("Error processing ValidateDurationProcessor for timer {}: {}", timer != null ? timer.getId() : null, ex.getMessage(), ex);
            return timer;
        }
    }

    private int lookupBaseDuration(String boilType, String eggSize) {
        String bt = (boilType != null) ? boilType.toLowerCase(Locale.ROOT) : "medium";
        String es = (eggSize != null) ? eggSize.toLowerCase(Locale.ROOT) : "medium";

        // Defaults based on functional requirements
        switch (bt) {
            case "soft":
                switch (es) {
                    case "small": return 180;
                    case "large": return 240;
                    default: return 210; // medium
                }
            case "hard":
                switch (es) {
                    case "small": return 300;
                    case "large": return 420;
                    default: return 360; // medium
                }
            case "medium":
            default:
                switch (es) {
                    case "small": return 240;
                    case "large": return 360;
                    default: return 300; // medium
                }
        }
    }
}
