package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class ScheduleDeliveryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleDeliveryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    // Platform default: send day is Monday; for simplicity compute next week's Monday
    private static final int PLATFORM_DEFAULT_SEND_DAY = 1; // Monday (ISO)

    public ScheduleDeliveryProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ScheduleDelivery for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber subscriber = context.entity();
        try {
            String tz = subscriber.getTimezone() != null ? subscriber.getTimezone() : "UTC";
            ZoneId zone = ZoneId.of(tz);
            ZonedDateTime now = ZonedDateTime.now(zone);

            // compute next send date as next occurrence of platform default send day
            ZonedDateTime nextSend = now.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.of(PLATFORM_DEFAULT_SEND_DAY)));

            subscriber.setLastDeliveryId(null); // no delivery yet
            logger.info("Scheduled delivery for subscriber {} at {}", subscriber.getId(), nextSend.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            // In real implementation we'd create a Delivery record via EntityService; omitted because processor should only modify current entity state.
        } catch (Exception ex) {
            logger.error("Error scheduling delivery for subscriber {}: {}", subscriber.getId(), ex.getMessage(), ex);
        }
        return subscriber;
    }
}
