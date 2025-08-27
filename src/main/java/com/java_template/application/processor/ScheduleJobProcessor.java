package com.java_template.application.processor;

import com.java_template.application.entity.weeklyjob.version_1.WeeklyJob;
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

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

@Component
public class ScheduleJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ScheduleJobProcessor(SerializerFactory serializerFactory) {
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

        // Compute the nextRunAt field based on recurrenceDay, runTime and timezone.
        // Store nextRunAt as an ISO instant string (UTC, e.g., 2025-08-27T09:00:00Z)
        try {
            String recurrenceDay = entity.getRecurrenceDay();
            String runTime = entity.getRunTime();
            String timezone = entity.getTimezone();

            if (recurrenceDay == null || runTime == null || timezone == null) {
                logger.warn("Cannot compute nextRunAt: recurrenceDay/runTime/timezone missing for job {}", entity.getId());
                return entity;
            }

            DayOfWeek targetDay = parseDayOfWeek(recurrenceDay);
            if (targetDay == null) {
                logger.warn("Unrecognized recurrenceDay '{}' for job {}", recurrenceDay, entity.getId());
                return entity;
            }

            ZoneId zone;
            try {
                zone = ZoneId.of(timezone);
            } catch (DateTimeException ex) {
                logger.warn("Invalid timezone '{}' for job {}. Falling back to UTC.", timezone, entity.getId());
                zone = ZoneId.of("UTC");
            }

            // Determine the reference moment to compute next occurrence from.
            ZonedDateTime reference;
            String lastRunAt = entity.getLastRunAt();
            if (lastRunAt != null && !lastRunAt.isBlank()) {
                try {
                    Instant lastInstant = Instant.parse(lastRunAt);
                    reference = ZonedDateTime.ofInstant(lastInstant, zone);
                } catch (DateTimeParseException ex) {
                    // fallback to now in zone
                    reference = ZonedDateTime.now(zone);
                }
            } else {
                reference = ZonedDateTime.now(zone);
            }

            // Parse runTime (accepts "H:mm" or "HH:mm")
            LocalTime timeOfDay;
            try {
                DateTimeFormatter tf = DateTimeFormatter.ofPattern("H:mm", Locale.ROOT);
                timeOfDay = LocalTime.parse(runTime, tf);
            } catch (DateTimeParseException ex) {
                logger.warn("Invalid runTime '{}' for job {}. Cannot compute nextRunAt.", runTime, entity.getId());
                return entity;
            }

            // Compute candidate date/time
            ZonedDateTime candidate = reference.with(TemporalAdjusters.nextOrSame(targetDay))
                                               .withHour(timeOfDay.getHour())
                                               .withMinute(timeOfDay.getMinute())
                                               .withSecond(0)
                                               .withNano(0);

            // If candidate is not strictly after reference, advance by one week to get the next occurrence
            if (!candidate.isAfter(reference)) {
                candidate = candidate.with(TemporalAdjusters.next(targetDay))
                                     .withHour(timeOfDay.getHour())
                                     .withMinute(timeOfDay.getMinute())
                                     .withSecond(0)
                                     .withNano(0);
            }

            // Convert to UTC instant string
            String nextRunAtIso = candidate.toInstant().toString();
            entity.setNextRunAt(nextRunAtIso);

            // Set status to SCHEDULED if not already
            if (entity.getStatus() == null || !entity.getStatus().equalsIgnoreCase("SCHEDULED")) {
                entity.setStatus("SCHEDULED");
            }

            logger.info("Computed nextRunAt={} (zone {}) for job {}", nextRunAtIso, zone, entity.getId());

        } catch (Exception ex) {
            logger.error("Unexpected error while scheduling WeeklyJob {}: {}", entity != null ? entity.getId() : "unknown", ex.getMessage(), ex);
            // Do not throw; leave entity unchanged so that workflow can handle retries/alerts
        }

        return entity;
    }

    private DayOfWeek parseDayOfWeek(String input) {
        if (input == null) return null;
        String trimmed = input.trim().toUpperCase(Locale.ROOT);
        // Try full name (MONDAY, TUESDAY, etc.)
        try {
            return DayOfWeek.valueOf(trimmed);
        } catch (IllegalArgumentException ignored) {}

        // Try first three letters (MON, TUE, ...)
        if (trimmed.length() >= 3) {
            String shortName = trimmed.substring(0, 3);
            switch (shortName) {
                case "MON": return DayOfWeek.MONDAY;
                case "TUE": return DayOfWeek.TUESDAY;
                case "WED": return DayOfWeek.WEDNESDAY;
                case "THU": return DayOfWeek.THURSDAY;
                case "FRI": return DayOfWeek.FRIDAY;
                case "SAT": return DayOfWeek.SATURDAY;
                case "SUN": return DayOfWeek.SUNDAY;
                default: return null;
            }
        }
        return null;
    }
}