package com.java_template.application.processor;

import com.java_template.application.entity.weeklyjob.version_1.WeeklyJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

@Component
public class StartValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StartValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing WeeklyJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(WeeklyJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(WeeklyJob entity) {
        return entity != null && entity.isValid();
    }

    private WeeklyJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklyJob> context) {
        WeeklyJob entity = context.entity();

        // Business validations beyond isValid()

        // 1) recipients must be present and non-blank (entity.isValid() already checks this,
        //    but re-verify to provide explicit error logging)
        if (entity.getRecipients() == null || entity.getRecipients().isEmpty()) {
            logger.error("WeeklyJob validation failed: recipients empty");
            throw new IllegalArgumentException("WeeklyJob recipients must be provided");
        }
        for (String r : entity.getRecipients()) {
            if (r == null || r.isBlank()) {
                logger.error("WeeklyJob validation failed: recipient blank");
                throw new IllegalArgumentException("WeeklyJob contains blank recipient");
            }
        }

        // 2) runTime must be in HH:mm (or H:mm) format
        String runTime = entity.getRunTime();
        LocalTime localTime;
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("H:mm", Locale.ENGLISH);
            localTime = LocalTime.parse(runTime, fmt);
        } catch (DateTimeParseException | NullPointerException ex) {
            logger.error("WeeklyJob validation failed: invalid runTime='{}'", runTime, ex);
            throw new IllegalArgumentException("Invalid runTime format. Expected HH:mm or H:mm.");
        }

        // 3) timezone must be a valid ZoneId
        String timezone = entity.getTimezone();
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezone);
        } catch (DateTimeException | NullPointerException ex) {
            logger.error("WeeklyJob validation failed: invalid timezone='{}'", timezone, ex);
            throw new IllegalArgumentException("Invalid timezone: " + timezone);
        }

        // 4) recurrenceDay must map to a DayOfWeek
        String recurrenceDay = entity.getRecurrenceDay();
        DayOfWeek dayOfWeek;
        try {
            dayOfWeek = DayOfWeek.valueOf(recurrenceDay.trim().toUpperCase(Locale.ENGLISH));
        } catch (Exception ex) {
            logger.error("WeeklyJob validation failed: invalid recurrenceDay='{}'", recurrenceDay, ex);
            throw new IllegalArgumentException("Invalid recurrenceDay: " + recurrenceDay);
        }

        // If all validations pass, compute nextRunAt and set status to SCHEDULED
        String nextRunAtIso = computeNextRunIso(localTime, dayOfWeek, zoneId);
        entity.setNextRunAt(nextRunAtIso);
        entity.setStatus("SCHEDULED");

        logger.info("WeeklyJob validated and scheduled. nextRunAt={}", nextRunAtIso);

        return entity;
    }

    private String computeNextRunIso(LocalTime runTime, DayOfWeek recurrenceDay, ZoneId zoneId) {
        ZonedDateTime now = ZonedDateTime.now(zoneId);

        // Start from today or next occurrence of the requested day
        ZonedDateTime candidate = now.with(TemporalAdjusters.nextOrSame(recurrenceDay))
                .withHour(runTime.getHour())
                .withMinute(runTime.getMinute())
                .withSecond(0)
                .withNano(0);

        // If candidate is not strictly in the future (i.e., equal or before now), advance by one week
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusWeeks(1);
        }

        // Convert to UTC ISO instant string (Z)
        Instant instant = candidate.toInstant();
        return instant.toString();
    }
}